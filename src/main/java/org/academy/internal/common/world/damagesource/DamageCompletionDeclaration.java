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
        if (target == null || source == null || !isValid(originalAmount, inflictedAmount)) return;

        var snapshot = new DamageContainer(source, originalAmount);
        snapshot.setNewDamage(inflictedAmount);
        snapshot.captureInflictedDamage();
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
        return originalAmount > 0.0f && Float.isFinite(originalAmount)
                && inflictedAmount > 0.0f && Float.isFinite(inflictedAmount);
    }
}
