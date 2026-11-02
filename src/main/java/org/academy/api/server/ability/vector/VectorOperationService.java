package org.academy.api.server.ability.vector;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.projectile.Projectile;
import org.academy.api.common.ability.vector.EffectDescriptor;
import org.academy.api.common.ability.vector.EffectHandle;
import org.academy.api.common.ability.vector.EffectObservation;
import org.academy.api.server.ability.AbilityActorContext;
import org.academy.api.server.ability.AbilityExecutionHandle;
import org.academy.internal.common.ability.accelerator.vector.VectorOperationRuntime;

/**
 * Server-thread effects shared by player skills, NPC executors and program actions. Entry points
 * authorize casts and settle CP once; this service never charges. Cancelling releases control,
 * preserving the projectile's current native owner, motion and payload rather than undoing hits.
 */
public final class VectorOperationService {
    private VectorOperationService() {}

    public static void registerProvider(ExternalEffectProvider provider) {
        VectorOperationRuntime.register(provider);
    }

    public static EffectDescriptor describe(Projectile projectile) {
        return VectorOperationRuntime.describe(projectile);
    }

    public static EffectObservation observe(Projectile projectile) {
        return VectorOperationRuntime.observe(projectile);
    }

    public static VectorOperationPreparation prepare(AbilityActorContext actor, VectorOperationAuthority authority,
                                                     EffectHandle effect, VectorOperationRequest request) {
        return VectorOperationRuntime.prepare(actor, authority, effect, request);
    }

    public static VectorOperationResult apply(AbilityActorContext actor, VectorOperationPlan plan) {
        return VectorOperationRuntime.apply(actor, plan);
    }

    /**
     * Additional server-thread impact admission for providers with custom collision or side effects.
     * A true result never overrides native target checks. While a lease is active this enforces its
     * authority, self/team protection and mayHit policy; an uncontrolled projectile is unrestricted.
     * Native living damage attributed directly to the projectile is also checked automatically.
     * Other on-hit effects, block changes and damage that hides its projectile source require the
     * provider to call this method before applying them. This does not undo effects already applied.
     */
    public static boolean mayAffect(Projectile projectile, Entity target) {
        return VectorOperationRuntime.mayAffect(projectile, target);
    }

    public static void stop(AbilityExecutionHandle handle) {
        if (handle != null) handle.stop();
    }
}
