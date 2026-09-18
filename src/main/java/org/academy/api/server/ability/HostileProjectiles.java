package org.academy.api.server.ability;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.phys.Vec3;
import org.academy.api.server.team.TeamRelations;

/** Shared ownership and swept-volume tests. Destruction is paid once on the server thread. */
public final class HostileProjectiles {
    private HostileProjectiles() {}

    public static boolean sweptIntersectsSphere(Vec3 start, Vec3 end, Vec3 center, double radius) {
        return sphereEntry(start, end, center, radius) != null;
    }

    /** Earliest impact along this tick's swept path, for authoritative interception and its VFX. */
    public static @org.jspecify.annotations.Nullable Vec3 sphereEntry(Vec3 start, Vec3 end, Vec3 center, double radius) {
        if (!Double.isFinite(radius) || radius < 0) return null;
        var segment = end.subtract(start);
        var lengthSquared = segment.lengthSqr();
        var offset = start.subtract(center);
        var c = offset.lengthSqr() - radius * radius;
        if (!Double.isFinite(lengthSquared) || !Double.isFinite(c)) return null;
        if (c <= 0) return start;
        if (lengthSquared <= 1.0e-12) return null;
        var b = offset.dot(segment);
        var discriminant = b * b - lengthSquared * c;
        if (discriminant < 0) return null;
        var t = (-b - Math.sqrt(discriminant)) / lengthSquared;
        return t >= 0 && t <= 1 ? start.add(segment.scale(t)) : null;
    }

    public static boolean isThreatTo(LivingEntity actor, Projectile projectile) {
        if (!projectile.isAlive() || projectile.isRemoved() || projectile.level() != actor.level()) return false;
        if (projectile instanceof net.minecraft.world.entity.projectile.arrow.AbstractArrow arrow && ((org.academy.mixin.common.AbstractArrowVectorAccessor) arrow).academy$isInGround()) return false;
        var owner = projectile.getOwner();
        if (owner == actor || owner != null && TeamRelations.areAllied(actor, owner)) return false;
        if (owner instanceof LivingEntity living) return HostileTargets.isHostile(actor, living);
        // Unowned utility projectiles (pearls, fishing hooks, eggs...) are not attacks.
        var type = projectile.getType();
        return projectile instanceof net.minecraft.world.entity.projectile.arrow.AbstractArrow
                || type == net.minecraft.world.entity.EntityTypes.FIREBALL
                || type == net.minecraft.world.entity.EntityTypes.SMALL_FIREBALL
                || type == net.minecraft.world.entity.EntityTypes.DRAGON_FIREBALL
                || type == net.minecraft.world.entity.EntityTypes.WITHER_SKULL
                || type == net.minecraft.world.entity.EntityTypes.BREEZE_WIND_CHARGE;
    }

    public static boolean tryDestroy(LivingEntity actor, Projectile projectile, AbilityResourceAccount resource,
                                     double radius, double cost) {
        if (actor.level().isClientSide() || !isThreatTo(actor, projectile)
                || !sweptIntersectsSphere(projectile.position(), projectile.position().add(projectile.getDeltaMovement()),
                actor.getBoundingBox().getCenter(), radius) || !resource.tryConsume(cost)) return false;
        projectile.discard();
        if (!projectile.isRemoved()) {
            resource.recover(cost);
            return false;
        }
        return true;
    }
}
