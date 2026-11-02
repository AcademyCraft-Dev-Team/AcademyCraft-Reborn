package org.academy.api.server.ability.vector;

import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.projectile.Projectile;
import org.academy.api.common.ability.vector.EffectDescriptor;
import org.academy.api.server.ability.AbilityActorContext;
import org.academy.api.server.ability.AbilityExecutionHandle;

import java.util.Optional;

/**
 * Register before AcademyRegistrationsReadyEvent. Discovery and rejectionReason must be pure.
 * An offered operation promises correct motion, owner/attribution, collision and synchronization
 * for this instance. Custom guidance must be explicitly supported, not inferred from its class name.
 */
public interface ExternalEffectProvider {
    Identifier id();
    default int priority() { return 0; }
    Optional<EffectDescriptor> describe(Projectile projectile);
    /** Cached generation of the executable binding. Advance it when hooks or protocol bindings change. */
    default long bindingGeneration(Projectile projectile) { return 0; }
    default String rejectionReason(AbilityActorContext actor, Projectile projectile, VectorOperationRequest request) {
        return "";
    }

    /**
     * The service has claimed the instance and changed standard Projectile ownership. Apply motion
     * without replacing the original entity/payload or charging CP. Return a non-null handle; even
     * instantaneous implementations must have idempotent stop. tick is called at most once per
     * server tick after authority/binding revalidation. stop releases only resources this invocation
     * owns. If start throws, clean up any hook/session created before throwing; the service reports
     * partial failure because native world effects and ownership cannot in general be rolled back.
     */
    AbilityExecutionHandle start(AbilityActorContext actor, Projectile projectile, VectorOperationRequest request);
}
