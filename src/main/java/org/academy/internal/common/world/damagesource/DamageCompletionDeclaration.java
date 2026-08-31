package org.academy.internal.common.world.damagesource;

import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.neoforged.neoforge.common.CommonHooks;
import net.neoforged.neoforge.common.damagesource.DamageContainer;
import org.academy.AcademyCraft;

/**
 * Publishes the immutable completion signal for damage resolved outside vanilla's damage pipeline.
 * The declaration is deliberately output-only: it does not post mutable pre-damage events and its
 * result is never fed back into the already completed health mutation.
 */
public final class DamageCompletionDeclaration {
    private DamageCompletionDeclaration() {
    }

    public static void publish(LivingEntity target, DamageSource source,
                               float originalAmount, float inflictedAmount) {
        publish(target, source, originalAmount, inflictedAmount, inflictedAmount);
    }

    public static void publish(LivingEntity target, DamageSource source,
                               float originalAmount, float inflictedAmount,
                               float healthDamage) {
        if (target == null || source == null
                || !isValid(originalAmount, inflictedAmount, healthDamage)) return;

        var snapshot = new DamageContainer(source, originalAmount);
        snapshot.setNewDamage(inflictedAmount);
        snapshot.captureInflictedDamage();
        snapshot.setNewDamage(healthDamage);
        snapshot.setPostAttackInvulnerabilityTicks(0);
        snapshot.setShouldCauseSideEffects(false);
        try {
            CommonHooks.onLivingDamagePost(target, snapshot);
        } catch (Throwable error) {
            AcademyCraft.getLogger().warn(
                    "A completed direct damage declaration failed for {}",
                    target.getStringUUID(),
                    error
            );
        }
    }

    static boolean isValid(float originalAmount, float inflictedAmount) {
        return isValid(originalAmount, inflictedAmount, inflictedAmount);
    }

    static boolean isValid(float originalAmount, float inflictedAmount, float healthDamage) {
        return originalAmount > 0.0f && Float.isFinite(originalAmount)
                && inflictedAmount > 0.0f && Float.isFinite(inflictedAmount)
                && healthDamage >= 0.0f && Float.isFinite(healthDamage)
                && healthDamage <= inflictedAmount;
    }

    /**
     * Resolves declaration-only health damage after an accepted true-health write. A target may
     * intentionally use a health value whose float spacing is larger than the resolved hit (damage
     * meters commonly do this). In that case the storage write is a representational no-op, but the
     * completed hit still needs an output signal. The returned value is never fed back into health.
     */
    static float resolveHealthDamageForDeclaration(float before, float expected,
                                                   float observed, float acceptedAmount) {
        if (!(before > 0.0f) || !Float.isFinite(before)
                || expected < 0.0f || !Float.isFinite(expected)
                || observed < 0.0f || !Float.isFinite(observed)
                || !(acceptedAmount > 0.0f) || !Float.isFinite(acceptedAmount)) {
            return 0.0f;
        }

        var observedDamage = Math.max(0.0f, before - observed);
        if (observedDamage > 0.0f) return observedDamage;
        if (expected == before && observed == before) return Math.min(before, acceptedAmount);
        return 0.0f;
    }
}
