package org.academy.internal.common.ability.accelerator.reflection.compat;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityReference;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.entity.projectile.ShulkerBullet;
import net.minecraft.world.entity.projectile.arrow.AbstractArrow;
import net.minecraft.world.entity.projectile.hurtingprojectile.AbstractHurtingProjectile;
import net.minecraft.world.phys.Vec3;
import org.academy.mixin.common.AbstractArrowVectorAccessor;
import org.academy.mixin.common.ShulkerBulletVectorAccessor;

public final class VectorProjectileStateAdapter {
    private VectorProjectileStateAdapter() {
    }

    public static void applyRedirect(Projectile projectile, Vec3 redirectedVelocity) {
        applyRedirect(projectile, redirectedVelocity,
                projectile == null ? null : projectile.getOwner());
    }

    public static void applyRedirect(Projectile projectile, Vec3 redirectedVelocity,
                                     Entity previousOwner) {
        if (projectile == null || !isFiniteMotion(redirectedVelocity)) return;
        projectile.setDeltaMovement(redirectedVelocity);
        projectile.setOnGround(false);
        projectile.needsSync = true;
        projectile.syncPosition = true;
        projectile.hurtMarked = true;

        if (projectile instanceof AbstractArrow arrow) {
            arrow.shakeTime = 0;
            var accessor = (AbstractArrowVectorAccessor) arrow;
            accessor.academy$setInGround(false);
            accessor.academy$setLife(0);
            accessor.academy$setInGroundTime(0);
            accessor.academy$resetPiercedEntities();
        }

        if (projectile instanceof AbstractHurtingProjectile hurtingProjectile) {
            // This Minecraft version derives acceleration direction from current velocity each tick.
            hurtingProjectile.accelerationPower = Math.abs(hurtingProjectile.accelerationPower);
        }

        if (projectile instanceof ShulkerBullet bullet) {
            var target = previousOwner != null && previousOwner.isAlive()
                    && previousOwner != projectile.getOwner() ? previousOwner : null;
            var accessor = (ShulkerBulletVectorAccessor) bullet;
            accessor.academy$setFinalTarget(
                    target == null ? null : EntityReference.of(target));
            var direction = accessor.academy$getCurrentMoveDirection();
            accessor.academy$selectNextMoveDirection(
                    direction == null ? null : direction.getAxis(), target);
        }
        VectorProjectileTargeting.retargetAfterRedirect(projectile, previousOwner);
    }

    private static boolean isFiniteMotion(Vec3 velocity) {
        return velocity != null
                && Double.isFinite(velocity.x)
                && Double.isFinite(velocity.y)
                && Double.isFinite(velocity.z)
                && velocity.lengthSqr() > 1.0E-8;
    }
}
