package org.academy.internal.common.world.damagesource;

import net.minecraft.advancements.triggers.CriteriaTriggers;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.neoforged.neoforge.common.damagesource.DamageContainer;
import org.academy.AcademyCraft;
import org.academy.api.common.ability.Skill;
import org.academy.api.common.damage.SkillDamageSource;
import org.academy.internal.common.ability.darkmatter.DarkmatterTargeting;
import org.academy.internal.common.attribute.PlayerAttributeRuntime;
import org.academy.mixin.common.LivingEntityDamageInvoker;
import org.jspecify.annotations.Nullable;

/**
 * Central entry point for AcademyCraft category damage.
 */
public final class SkillDamageUtil {
    private static final float EPSILON = 0.0001f;

    private SkillDamageUtil() {
    }

    public static boolean apply(ServerPlayer attacker, LivingEntity target, Skill skill,
                                ResourceKey<DamageType> type, float amount) {
        if (attacker == null || target == null || skill == null || type == null) return false;
        if (!(amount > 0.0f) || !Float.isFinite(amount) || !target.isAlive()) return false;
        if (target == attacker || DamageTypes.isImmunePlayer(target instanceof Player p ? p : null)) {
            return false;
        }
        if (PvpSetting.shouldPrevent(attacker, target)) return false;
        if (!(target.level() instanceof ServerLevel level)) return false;

        var source = SkillDamageSource.of(attacker, skill, type);
        if (DamageTypes.usesVerifiedTrueHealth(type)) {
            return applyVerifiedTrueHealth(target, source, amount);
        }
        if (DamageTypes.usesDirectActuallyHurt(type)) {
            return applyDirectWithFallback(level, attacker, target, skill, source, amount);
        }
        return target.hurtServer(level, source, amount);
    }

    public static boolean applyDirect(ServerLevel level, LivingEntity target,
                                      SkillDamageSource source, float amount) {
        return applyDirect(level, target, source, amount, true);
    }

    public static boolean applyDirectFromHurtServer(
            ServerLevel level,
            LivingEntity target,
            SkillDamageSource source,
            float amount
    ) {
        return applyDirect(level, target, source, amount, false);
    }

    private static boolean applyDirect(
            ServerLevel level,
            LivingEntity target,
            SkillDamageSource source,
            float amount,
            boolean notifyCustomHurt
    ) {
        if (DarkmatterTargeting.isNetworkMember(target)
                && DarkmatterTargeting.isDarkmatterDamage(source)) return false;
        if (!(source.getEntity() instanceof ServerPlayer attacker)) return false;
        if (PvpSetting.shouldPrevent(attacker, target)) return false;
        if (DamageTypes.isImmunePlayer(target instanceof Player p ? p : null)) {
            return false;
        }
        if (DamageTypes.usesVerifiedTrueHealth(source)) {
            var handler = new CTAEntityActuallyHurt(target);
            return notifyCustomHurt
                    ? handler.actuallyHurt(source, amount, true)
                    : handler.actuallyHurtFromHurtServer(source, amount, true);
        }
        return applyDirectWithFallback(level, attacker, target, source.getSkill(), source, amount);
    }

    /**
     * Applies CTA/VEC through the verified authoritative-health route. Callers should use this
     * instead of invoking {@code hurtServer} when the original amount must not be clipped by a
     * custom entity override. The override still receives a non-mutating compatibility callback.
     */
    public static boolean applyVerifiedTrueHealth(
            LivingEntity target,
            DamageSource source,
            float amount
    ) {
        if (target == null || source == null
                || !(amount > 0.0f) || !Float.isFinite(amount)
                || !target.isAlive()) return false;
        if (!DamageTypes.usesVerifiedTrueHealth(source)) return false;
        if (!(target.level() instanceof ServerLevel)) return false;
        if (source.getEntity() == target || source.getDirectEntity() == target) return false;
        var attacker = PvpSetting.resolveAttacker(source);
        if (attacker != null && PvpSetting.shouldPrevent(attacker, target)) return false;
        if (DamageTypes.isImmunePlayer(target instanceof Player player ? player : null)) return false;
        return new CTAEntityActuallyHurt(target).actuallyHurt(source, amount, true);
    }

    private static boolean applyDirectWithFallback(ServerLevel level, ServerPlayer attacker,
                                                   LivingEntity target, Skill skill,
                                                   DamageSource source, float amount) {
        var beforeHealth = target.getHealth();
        var beforeAbsorption = target.getAbsorptionAmount();
        var invoker = (LivingEntityDamageInvoker) target;
        var containers = invoker.academy$getDamageContainers();
        var container = new DamageContainer(source, amount);
        containers.push(container);

        var completed = false;
        try {
            invoker.academy$actuallyHurt(level, source, amount);
            completed = true;
        } catch (Throwable error) {
            AcademyCraft.getLogger().warn(
                    "Direct actuallyHurt failed for {}; falling back when no damage was committed",
                    target.getStringUUID(),
                    error
            );
            PlayerAttributeRuntime.clearDamageContext();
        } finally {
            if (!containers.isEmpty() && containers.peek() == container) containers.pop();
            else containers.remove(container);
        }

        var healthDamage = Math.max(0.0f, beforeHealth - target.getHealth());
        var absorptionDamage = Math.max(0.0f, beforeAbsorption - target.getAbsorptionAmount());
        var committed = healthDamage > EPSILON || absorptionDamage > EPSILON;
        if (!completed && !committed) {
            return target.hurtServer(level, attacker.damageSources().playerAttack(attacker), amount);
        }
        if (!committed) {
            return target.hurtServer(level, attacker.damageSources().playerAttack(attacker), amount);
        }

        completeDirectDamage(
                level, target, source, amount, healthDamage + absorptionDamage,
                attacker, skill, target.isDeadOrDying(), true
        );
        DamageCompletionDeclaration.publish(
                target,
                source,
                container.getOriginalDamage(),
                container.getInflictedDamage(),
                container.getNewDamage()
        );
        return true;
    }

    /**
     * Completes the outer half of vanilla's damage flow after a verified direct health mutation.
     * The health mutation itself may bypass {@code hurtServer}, but kill attribution, criteria,
     * death events, loot, experience, and the normal death-removal animation still use vanilla.
     */
    static void completeDirectDamage(
            ServerLevel level,
            LivingEntity target,
            DamageSource source,
            float originalAmount,
            float inflictedAmount,
            @Nullable ServerPlayer attacker,
            @Nullable Skill skill,
            boolean confirmedLethal,
            boolean checkDeathProtection
    ) {
        var invoker = (LivingEntityDamageInvoker) target;
        if (attacker != null) {
            target.setLastHurtByPlayer(attacker, 100);
            target.setLastHurtByMob(attacker);
        }
        invoker.academy$setLastHurt(originalAmount);
        invoker.academy$setLastDamageSource(source);
        invoker.academy$setLastDamageStamp(level.getGameTime());
        level.broadcastDamageEvent(target, source);
        if (attacker != null && skill != null) {
            PvpSetting.recordSkillDamage(attacker, target, inflictedAmount);
            skill.onHurt(attacker, target, inflictedAmount);
        }

        if (target instanceof ServerPlayer hurtPlayer) {
            CriteriaTriggers.ENTITY_HURT_PLAYER.trigger(
                    hurtPlayer, source, originalAmount, inflictedAmount, false
            );
        }
        if (attacker != null) {
            CriteriaTriggers.PLAYER_HURT_ENTITY.trigger(
                    attacker, target, source, originalAmount, inflictedAmount, false
            );
        }

        for (var effect : target.getActiveEffects()) {
            try {
                effect.onMobHurt(level, target, source, inflictedAmount);
            } catch (Throwable error) {
                AcademyCraft.getLogger().warn(
                        "A direct-damage effect callback failed for {}",
                        target.getStringUUID(),
                        error
                );
            }
        }

        var completion = new DamageContainer(source, originalAmount);
        completion.setNewDamage(inflictedAmount);
        completion.captureInflictedDamage();
        completion.setNewDamage(inflictedAmount);
        completion.setPostAttackInvulnerabilityTicks(0);
        completion.setShouldCauseSideEffects(false);
        try {
            target.onDamageTaken(completion);
        } catch (Throwable error) {
            AcademyCraft.getLogger().warn(
                    "A direct-damage completion callback failed for {}",
                    target.getStringUUID(),
                    error
            );
        }

        if (!confirmedLethal) {
            invoker.academy$playHurtSound(source);
            invoker.academy$playSecondaryHurtSound(source);
            return;
        }

        var protectedFromDeath = checkDeathProtection
                && invoker.academy$checkTotemDeathProtection(source);
        if (protectedFromDeath) return;

        var deathSound = invoker.academy$getDeathSound();
        if (deathSound != null) {
            target.playSound(deathSound, invoker.academy$getSoundVolume(), target.getVoicePitch());
        }
        invoker.academy$playSecondaryHurtSound(source);
        TrueDamageCompatibility.completeLethalDamage(level, target, source);
    }
}
