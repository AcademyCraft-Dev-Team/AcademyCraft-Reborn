package org.academy.api.server.ability.vector;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.projectile.Projectile;
import org.academy.api.server.ability.AbilityActorContext;

/**
 * Server-owned admission, called at prepare, apply and every control tick. Check current learning,
 * enabled skill and any actor-specific control restrictions. No callback may charge CP or mutate
 * the world. Constructing AbilityActorContext does not supply these permissions.
 */
@FunctionalInterface
public interface VectorOperationAuthority {
    boolean mayOperate(AbilityActorContext actor, Projectile effect, VectorOperationRequest request);

    /** Additional target admission while the operation's control lease is active. */
    default boolean mayHit(AbilityActorContext actor, Projectile effect, Entity target) {
        return true;
    }
}
