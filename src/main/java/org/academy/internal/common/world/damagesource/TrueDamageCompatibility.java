package org.academy.internal.common.world.damagesource;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.level.gameevent.GameEvent;
import net.neoforged.neoforge.common.CommonHooks;
import org.academy.AcademyCraft;
import org.academy.internal.common.entitycontrol.EntityControlApi;
import org.academy.mixin.common.LivingEntityDamageInvoker;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;

/**
 * Compatibility bridge for verified true damage.
 *
 * <p>CTA and VEC keep their full authoritative-health subtraction, but custom entity overrides
 * still receive a non-mutating notification pass. Lethal damage first offers the override its
 * normal death callback, then enters the vanilla death implementation directly if the override
 * short-circuits it. This avoids javaagents and raw memory access.</p>
 */
public final class TrueDamageCompatibility {
    private static final ThreadLocal<HurtProbe> HURT_PROBE = new ThreadLocal<>();
    private static final ThreadLocal<DeathAttempt> DEATH_ATTEMPT = new ThreadLocal<>();
    private static final MethodHandle VANILLA_DIE = resolveVanillaDie();
    private static final ClassValue<Boolean> CUSTOM_HURT_SERVER = new ClassValue<>() {
        @Override
        protected Boolean computeValue(Class<?> type) {
            try {
                return type.getMethod(
                        "hurtServer",
                        ServerLevel.class,
                        DamageSource.class,
                        float.class
                ).getDeclaringClass() != LivingEntity.class;
            } catch (Throwable ignored) {
                return false;
            }
        }
    };

    private TrueDamageCompatibility() {
    }

    /**
     * Runs a custom hurt override without allowing its ordinary health path to replace or cap the
     * authoritative CTA/VEC subtraction that follows.
     */
    public static void notifyCustomHurt(
            LivingEntity target,
            ServerLevel level,
            DamageSource source,
            float amount
    ) {
        if (target == null || level == null || source == null
                || !(amount > 0.0f) || !Float.isFinite(amount)
                || HURT_PROBE.get() != null
                || !CUSTOM_HURT_SERVER.get(target.getClass())) {
            return;
        }

        var healthBefore = EntityControlApi.getAuthoritativeHealth(target);
        var absorptionBefore = target.getAbsorptionAmount();
        var probe = new HurtProbe(target, source);
        HURT_PROBE.set(probe);
        try {
            target.hurtServer(level, source, amount);
        } catch (Throwable error) {
            AcademyCraft.getLogger().warn(
                    "Custom true-damage hurt notification failed for {}",
                    target.getStringUUID(),
                    error
            );
        } finally {
            HURT_PROBE.remove();
            if (Float.isFinite(healthBefore)
                    && Math.abs(EntityControlApi.getAuthoritativeHealth(target) - healthBefore) > 0.0001f) {
                EntityControlApi.forceSetTrueHealth(target, healthBefore);
            }
            if (Float.isFinite(absorptionBefore)
                    && Math.abs(target.getAbsorptionAmount() - absorptionBefore) > 0.0001f) {
                target.setAbsorptionAmount(absorptionBefore);
            }
        }
    }

    /**
     * Called at the head of LivingEntity.hurtServer. Returning true suppresses only the base
     * health mutation reached from a custom notification pass; the custom override itself has
     * already run.
     */
    public static boolean isHurtProbe(LivingEntity target, DamageSource source) {
        var probe = HURT_PROBE.get();
        return probe != null && probe.target == target && probe.source == source;
    }

    /**
     * Called by the LivingEntity.die mixin so the dispatcher can distinguish a custom override
     * that deliberately stopped before vanilla death from a normal cancellable death event.
     */
    public static void onVanillaDieEntered(LivingEntity target, DamageSource source) {
        var attempt = DEATH_ATTEMPT.get();
        if (attempt != null && attempt.target == target && attempt.source == source) {
            attempt.vanillaEntered = true;
        }
    }

    /**
     * Completes lethal true damage without allowing a custom die override to restore the health
     * that CTA/VEC already depleted. A normal death-event cancellation remains authoritative.
     */
    public static boolean completeLethalDamage(
            ServerLevel level,
            LivingEntity target,
            DamageSource source
    ) {
        if (level == null || target == null || source == null) return false;
        if (isDeathCommitted(target)) {
            enforceDepletedHealth(target);
            return true;
        }

        var previous = DEATH_ATTEMPT.get();
        var attempt = new DeathAttempt(target, source);
        DEATH_ATTEMPT.set(attempt);
        try {
            try {
                target.die(source);
            } catch (Throwable error) {
                AcademyCraft.getLogger().warn(
                        "Custom true-damage death callback failed for {}",
                        target.getStringUUID(),
                        error
                );
            }
            if (isDeathCommitted(target)) {
                enforceDepletedHealth(target);
                return true;
            }

            // If LivingEntity.die was entered, a normal death hook canceled the sequence. Respect
            // that cancellation instead of posting the event a second time.
            if (!needsVanillaDeathFallback(isDeathCommitted(target), attempt.vanillaEntered)) {
                restoreCanceledDeathHealth(target);
                return false;
            }

            if (invokeVanillaDie(target, source) && isDeathCommitted(target)) {
                enforceDepletedHealth(target);
                return true;
            }

            if (!attempt.vanillaEntered && forceVanillaDeath(level, target, source)) {
                enforceDepletedHealth(target);
                return true;
            }

            restoreCanceledDeathHealth(target);
            return false;
        } finally {
            if (previous == null) DEATH_ATTEMPT.remove();
            else DEATH_ATTEMPT.set(previous);
        }
    }

    static boolean needsVanillaDeathFallback(boolean deathCommitted, boolean vanillaEntered) {
        return !deathCommitted && !vanillaEntered;
    }

    private static boolean invokeVanillaDie(LivingEntity target, DamageSource source) {
        if (VANILLA_DIE == null) return false;
        try {
            VANILLA_DIE.invoke(target, source);
            return true;
        } catch (Throwable error) {
            AcademyCraft.getLogger().warn(
                    "Direct LivingEntity death dispatch failed for {}",
                    target.getStringUUID(),
                    error
            );
            return false;
        }
    }

    private static boolean forceVanillaDeath(
            ServerLevel level,
            LivingEntity target,
            DamageSource source
    ) {
        if (CommonHooks.onLivingDeath(target, source)) return false;
        try {
            var credit = target.getKillCredit();
            if (credit != null) credit.awardKillScore(target, source);
            if (target.isSleeping()) target.stopSleeping();
            target.stopUsingItem();

            var invoker = (LivingEntityDamageInvoker) target;
            invoker.academy$handleKillingBlow();
            target.getCombatTracker().recheckStatus();

            var killer = source.getEntity();
            if (killer == null || !killer.killedEntity(level, target, source)) {
                target.gameEvent(GameEvent.ENTITY_DIE);
                invoker.academy$dropAllDeathLoot(level, source);
            }
            level.broadcastEntityEvent(target, (byte) 3);
            target.setPose(Pose.DYING);
            return isDeathCommitted(target);
        } catch (Throwable error) {
            AcademyCraft.getLogger().warn(
                    "Fallback true-damage death completion failed for {}",
                    target.getStringUUID(),
                    error
            );
            return false;
        }
    }

    private static boolean isDeathCommitted(LivingEntity target) {
        return target.isRemoved() || ((LivingEntityDamageInvoker) target).academy$isDead();
    }

    private static void enforceDepletedHealth(LivingEntity target) {
        EntityControlApi.forceSetTrueHealth(target, 0.0f);
        target.deathTime = 0;
    }

    private static void restoreCanceledDeathHealth(LivingEntity target) {
        if (EntityControlApi.getAuthoritativeHealth(target) <= 0.0f) {
            EntityControlApi.forceSetTrueHealth(target, 1.0f);
        }
    }

    private static MethodHandle resolveVanillaDie() {
        try {
            var lookup = MethodHandles.privateLookupIn(LivingEntity.class, MethodHandles.lookup());
            return lookup.findSpecial(
                    LivingEntity.class,
                    "die",
                    MethodType.methodType(void.class, DamageSource.class),
                    LivingEntity.class
            );
        } catch (Throwable error) {
            AcademyCraft.getLogger().warn(
                    "Direct LivingEntity death dispatch is unavailable; using the accessor fallback",
                    error
            );
            return null;
        }
    }

    private record HurtProbe(LivingEntity target, DamageSource source) {
    }

    private static final class DeathAttempt {
        private final LivingEntity target;
        private final DamageSource source;
        private boolean vanillaEntered;

        private DeathAttempt(LivingEntity target, DamageSource source) {
            this.target = target;
            this.source = source;
        }
    }
}
