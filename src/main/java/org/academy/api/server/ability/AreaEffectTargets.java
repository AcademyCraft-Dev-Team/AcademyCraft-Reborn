package org.academy.api.server.ability;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.List;
import java.util.function.Predicate;

/** Reusable area queries; callers supply ownership, team and skill-specific filters. */
public final class AreaEffectTargets {
    private AreaEffectTargets() {
    }

    public static List<LivingEntity> inSphere(ServerLevel level, Vec3 center, double radius,
                                               Predicate<LivingEntity> filter) {
        if (!valid(center) || !Double.isFinite(radius) || radius <= 0) return List.of();
        return level.getEntitiesOfClass(LivingEntity.class, new AABB(center, center).inflate(radius),
                target -> target.isAlive() && contains(center, target.position(), radius) && filter.test(target));
    }

    /** Uses the entity origin, matching ground contact at the impact circle rather than AABB corners. */
    public static boolean contains(Vec3 center, Vec3 point, double radius) {
        return valid(center) && valid(point) && Double.isFinite(radius) && radius > 0
                && center.distanceToSqr(point) <= radius * radius;
    }

    private static boolean valid(Vec3 value) {
        return value != null && Double.isFinite(value.x) && Double.isFinite(value.y) && Double.isFinite(value.z);
    }
}
