package org.academy.internal.common.ability.accelerator.reflection.compat;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.projectile.Projectile;

/**
 * Compatibility policy for projectile implementations with asymmetric owner assumptions.
 */
public final class VectorProjectileCollisionPolicy {
    private VectorProjectileCollisionPolicy() {
    }

    public static boolean blocksUnsafeHit(Projectile projectile, Entity candidate) {
        if (projectile == null || !(candidate instanceof Projectile peer)) return false;
        return blocksOwnerlessPeerCollision(
                projectile.getType() == peer.getType(),
                projectile.getOwner() != null,
                peer.getOwner() != null,
                VectorProjectileCompatRegistry.suppressesOwnerlessPeerCollision(projectile.getType())
        );
    }

    static boolean blocksOwnerlessPeerCollision(
            boolean sameType,
            boolean ownerPresent,
            boolean peerOwnerPresent,
            boolean configured
    ) {
        return configured && sameType && ownerPresent && !peerOwnerPresent;
    }
}
