package org.academy.internal.common.world.damagesource;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.gameevent.GameEvent;
import org.academy.api.common.damage.DamageComposition;
import org.academy.api.common.damage.SkillDamageSource;
import org.academy.api.server.ability.SkillTuning;
import org.academy.internal.common.ability.level0.skills.OutputControl;
import org.academy.internal.common.attribute.PlayerAttributeRuntime;
import org.academy.internal.common.entitycontrol.EntityControlApi;
import org.academy.internal.common.entitycontrol.TrueHealthOffsetRuntime;

/**
 * Applies an exact subtraction to the resolved combat-health pool after normal damage hooks run.
 */
public final class CTAEntityActuallyHurt {
    private static final float EPSILON = 0.05f;
    private final LivingEntity entity;

    public CTAEntityActuallyHurt(LivingEntity entity) {
        this.entity = entity;
    }

    public static float readTrueHealth(LivingEntity entity) {
        return TrueHealthOffsetRuntime.effectiveHealth(entity);
    }

    public static void writeTrueHealth(LivingEntity entity, float value) {
        PlayerAttributeRuntime.runWithoutResistance(() -> EntityControlApi.forceSetTrueHealth(entity, value));
    }

    public static void simulateMarkedDeath(LivingEntity entity, ServerPlayer killer, DamageSource source) {
        if (entity == null || killer == null || source == null) return;
        var health = Math.max(1.0f, readTrueHealth(entity));
        new CTAEntityActuallyHurt(entity).actuallyHurt(source, health + 1.0f, true);
    }

    private static ServerPlayer resolveOwnerPlayer(DamageSource source) {
        return PvpSetting.resolveAttacker(source);
    }

    public boolean actuallyHurt(DamageSource source, float amount, boolean special) {
        return actuallyHurt(source, amount, special, true);
    }

    boolean actuallyHurtFromHurtServer(DamageSource source, float amount, boolean special) {
        return actuallyHurt(source, amount, special, false);
    }

    private boolean actuallyHurt(
            DamageSource source,
            float amount,
            boolean special,
            boolean notifyCustomHurt
    ) {
        if (entity == null || source == null || !(entity.level() instanceof ServerLevel level)) return false;
        if (!Float.isFinite(amount) || amount <= 0.0f || entity.isDeadOrDying()) return false;
        if (entity instanceof Player player && DamageTypes.isImmunePlayer(player)) return false;
        if (shouldPreventFriendlyFire(source)) return false;

        var reducedAmount = entity instanceof Player player
                ? PlayerAttributeRuntime.reduceDamage(player, amount, 0.08)
                : amount;
        var adjustedAmount = OutputControl.adjustDamage(source, reducedAmount);
        if (adjustedAmount > reducedAmount) {
            var percentage = Math.min(amount,
                    DamageComposition.maximumHealthPart(entity, source));
            adjustedAmount = reducedAmount + (adjustedAmount - reducedAmount) * (1.0f - percentage / amount);
        }
        adjustedAmount = org.academy.internal.common.ability.AbilityCategories.ELECTROMASTER.get()
                .outgoingDamage(source, adjustedAmount);
        adjustedAmount = SkillTuning.scaleSkillDamage(
                source, adjustedAmount, DamageComposition.maximumHealthPart(entity, source));
        if (!(adjustedAmount > 0.0f) || !Float.isFinite(adjustedAmount)) return false;
        if (notifyCustomHurt) {
            TrueDamageCompatibility.notifyCustomHurt(entity, level, source, adjustedAmount);
        }
        var finalAmount = adjustedAmount;
        var applied = new boolean[1];
        OutputControl.runWithoutDamageScaling(
                () -> PlayerAttributeRuntime.runWithoutResistance(
                        () -> applied[0] = apply(level, source, finalAmount)
                )
        );
        return applied[0];
    }

    private boolean apply(ServerLevel level, DamageSource source, float amount) {
        var before = readTrueHealth(entity);
        if (!Float.isFinite(before) || before <= 0.0f) return false;
        var expected = Math.max(0.0f, before - amount);
        if (!TrueHealthOffsetRuntime.permits(entity, expected)) return false;
        var wrote = EntityControlApi.forceSetTrueHealth(entity, expected);
        var projected = expected < before && TrueHealthOffsetRuntime.install(entity, expected);
        var observed = readTrueHealth(entity);
        if ((!wrote && !projected) || !Float.isFinite(observed) || Math.abs(observed - expected) > EPSILON) {
            return false;
        }

        // Unsupported custom pools retain their legacy best-effort cap; never stack it with projection.
        if (!TrueHealthOffsetRuntime.supports(entity) && expected > 0)
            EntityControlApi.capTrueHealthTemporarily(entity, expected, 2L);
        var inflicted = Math.max(0.0f, before - observed);
        var declarationDamage = DamageCompletionDeclaration.resolveHealthDamageForDeclaration(
                before, expected, observed, amount
        );
        if (!(inflicted > 0.0f)) {
            DamageCompletionDeclaration.publish(entity, source, amount, declarationDamage);
            return false;
        }

        entity.getCombatTracker().recordDamage(source, inflicted);
        entity.setInvulnerableTime(0);
        entity.hurtDuration = 10;
        entity.hurtTime = entity.hurtDuration;
        entity.syncVelocity = true;
        entity.walkAnimation.setSpeed(1.5f);
        entity.gameEvent(GameEvent.ENTITY_DAMAGE);
        if (entity instanceof Player player) {
            player.getFoodData().addExhaustion(source.getFoodExhaustion());
        }
        var attacker = resolveOwnerPlayer(source);
        var skill = source instanceof SkillDamageSource skillSource ? skillSource.getSkill() : null;
        SkillDamageUtil.completeDirectDamage(
                level, entity, source, amount, inflicted, attacker, skill,
                expected == 0.0f, true
        );
        DamageCompletionDeclaration.publish(entity, source, amount, declarationDamage);
        return true;
    }

    private boolean shouldPreventFriendlyFire(DamageSource source) {
        var owner = resolveOwnerPlayer(source);
        return owner != null && CtaFriendlyFireWhitelist.shouldProtect(owner, entity);
    }
}
