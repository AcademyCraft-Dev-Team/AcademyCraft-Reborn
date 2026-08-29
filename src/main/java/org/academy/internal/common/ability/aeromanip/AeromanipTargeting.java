package org.academy.internal.common.ability.aeromanip;

import net.minecraft.network.protocol.game.ClientboundSetEntityMotionPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.boss.enderdragon.EnderDragon;
import net.minecraft.world.entity.boss.wither.WitherBoss;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.academy.api.server.team.TeamRelations;
import org.academy.internal.common.world.damagesource.FriendlyFireSetting;
import org.academy.internal.common.entitycontrol.EntityMotionGuard;

import java.util.function.Predicate;

public final class AeromanipTargeting {
    private static final double MAX_SPEED = 3.0;

    private AeromanipTargeting() {
    }

    public static boolean canAffectNegatively(ServerPlayer owner, Entity target) {
        if (target == null || target == owner || target.isRemoved() || target.isSpectator()) return false;
        if (target instanceof LivingEntity living && FriendlyFireSetting.shouldPrevent(owner, living)) return false;
        return !TeamRelations.areAllied(owner, target);
    }

    public static boolean isBoss(Entity entity) {
        return entity instanceof EnderDragon || entity instanceof WitherBoss;
    }

    public static double forceMultiplier(ServerPlayer owner, Entity entity) {
        if (isBoss(entity)) return 0.0;
        return entity instanceof Player ? AeromanipConfig.pvpForce(owner) : 1.0;
    }

    public static boolean isProjectile(Entity entity) {
        return entity instanceof Projectile;
    }

    public static void addClampedVelocity(Entity entity, Vec3 delta) {
        if (!finite(delta)) return;
        setClampedVelocity(entity, entity.getDeltaMovement().add(delta));
    }

    public static void accelerateAlong(Entity entity, Vec3 direction, double acceleration, double maxForwardSpeed) {
        setClampedVelocity(entity, acceleratedVelocity(
                entity.getDeltaMovement(), direction, acceleration, maxForwardSpeed));
    }

    /** High-speed variant used by stacked jet nozzles, with an explicit total-speed ceiling. */
    public static void accelerateAlong(
            Entity entity,
            Vec3 direction,
            double acceleration,
            double maxForwardSpeed,
            double maxTotalSpeed
    ) {
        setClampedVelocity(entity, acceleratedVelocity(
                entity.getDeltaMovement(), direction, acceleration,
                maxForwardSpeed, maxTotalSpeed), maxTotalSpeed);
    }

    public static void steerVelocity(Entity entity, Vec3 direction, double response, double targetSpeed) {
        setClampedVelocity(entity, steeredVelocity(
                entity.getDeltaMovement(), direction, response, targetSpeed));
    }

    public static void scaleVelocity(Entity entity, double factor) {
        if (!Double.isFinite(factor)) return;
        setClampedVelocity(entity, entity.getDeltaMovement().scale(Math.max(0.0, factor)));
    }

    public static Vec3 updraftDirection(Vec3 center, Vec3 target, double liftHeight) {
        if (!finite(center) || !finite(target) || !Double.isFinite(liftHeight)) return Vec3.ZERO;
        return center.add(0.0, Math.max(0.0, liftHeight), 0.0).subtract(target);
    }

    public static double adjustControlDistance(double current, int steps, double stepSize,
                                               double minDistance, double maxDistance) {
        if (!Double.isFinite(stepSize) || !Double.isFinite(minDistance) || !Double.isFinite(maxDistance)) {
            return 0.0;
        }
        var minimum = Math.max(0.0, minDistance);
        var maximum = Math.max(minimum, maxDistance);
        var value = Double.isFinite(current) ? current : minimum;
        var adjustment = Integer.signum(steps) * Math.max(0.0, stepSize);
        return Mth.clamp(value + adjustment, minimum, maximum);
    }

    /** Selects the nearest living target intersected by a widened view-ray AABB. */
    public static LivingEntity findLivingTargetAlongView(
            ServerPlayer owner,
            double range,
            double searchHalfWidth,
            double searchHalfHeight,
            double targetBoxInflate,
            Predicate<LivingEntity> predicate
    ) {
        if (owner == null || !Double.isFinite(range) || range <= 0.0) return null;
        var eye = owner.getEyePosition();
        var direction = owner.getLookAngle().normalize();
        if (direction.lengthSqr() < 1.0e-6) return null;
        var resolvedRange = Math.max(0.0, range);
        var resolvedWidth = Math.max(0.0, searchHalfWidth);
        var resolvedHeight = Math.max(0.0, searchHalfHeight);
        var resolvedInflate = Math.max(0.0, targetBoxInflate);
        var end = eye.add(direction.scale(resolvedRange));
        var searchBox = new AABB(eye, end)
                .inflate(resolvedWidth, resolvedHeight, resolvedWidth);

        LivingEntity best = null;
        var bestProjection = Double.MAX_VALUE;
        for (var candidate : owner.level().getEntitiesOfClass(
                LivingEntity.class,
                searchBox,
                entity -> entity != owner && entity.isAlive() && !entity.isSpectator()
                        && (predicate == null || predicate.test(entity)))) {
            var candidateBox = candidate.getBoundingBox().inflate(resolvedInflate);
            var projection = candidateBox.getCenter().subtract(eye).dot(direction);
            if (projection < 0.0 || projection > resolvedRange || !owner.hasLineOfSight(candidate)) continue;
            var closestPoint = eye.add(direction.scale(projection));
            if (distanceToBoxSqr(closestPoint, candidateBox)
                    > resolvedInflate * resolvedInflate) continue;
            if (projection < bestProjection) {
                bestProjection = projection;
                best = candidate;
            }
        }
        return best;
    }

    static double distanceToBoxSqr(Vec3 point, AABB box) {
        var dx = Math.max(Math.max(box.minX - point.x, 0.0), point.x - box.maxX);
        var dy = Math.max(Math.max(box.minY - point.y, 0.0), point.y - box.maxY);
        var dz = Math.max(Math.max(box.minZ - point.z, 0.0), point.z - box.maxZ);
        return dx * dx + dy * dy + dz * dz;
    }

    /** Finds a point just outside an AABB along the supplied outward direction. */
    public static Vec3 pointOutside(AABB box, Vec3 direction, double offset) {
        var center = box.getCenter();
        var normalized = direction == null || direction.lengthSqr() <= 1.0e-8
                ? new Vec3(0.0, 1.0, 0.0)
                : direction.normalize();
        var distance = Double.POSITIVE_INFINITY;
        if (normalized.x > 1.0e-8) distance = Math.min(distance, (box.maxX - center.x) / normalized.x);
        if (normalized.x < -1.0e-8) distance = Math.min(distance, (box.minX - center.x) / normalized.x);
        if (normalized.y > 1.0e-8) distance = Math.min(distance, (box.maxY - center.y) / normalized.y);
        if (normalized.y < -1.0e-8) distance = Math.min(distance, (box.minY - center.y) / normalized.y);
        if (normalized.z > 1.0e-8) distance = Math.min(distance, (box.maxZ - center.z) / normalized.z);
        if (normalized.z < -1.0e-8) distance = Math.min(distance, (box.minZ - center.z) / normalized.z);
        if (!Double.isFinite(distance)) distance = 0.0;
        return center.add(normalized.scale(Math.max(0.0, distance) + Math.max(0.0, offset)));
    }

    static Vec3 acceleratedVelocity(Vec3 velocity, Vec3 direction, double acceleration, double maxForwardSpeed) {
        return acceleratedVelocity(velocity, direction, acceleration, maxForwardSpeed, MAX_SPEED);
    }

    static Vec3 acceleratedVelocity(
            Vec3 velocity,
            Vec3 direction,
            double acceleration,
            double maxForwardSpeed,
            double maxTotalSpeed
    ) {
        if (!finite(velocity) || !finite(direction)
                || !Double.isFinite(acceleration) || !Double.isFinite(maxForwardSpeed)
                || !Double.isFinite(maxTotalSpeed) || maxTotalSpeed <= 0.0
                || acceleration <= 0.0 || maxForwardSpeed <= 0.0 || direction.lengthSqr() <= 1.0e-8) {
            return finite(velocity) ? velocity : Vec3.ZERO;
        }
        var normalized = direction.normalize();
        var forwardSpeed = velocity.dot(normalized);
        var applied = Math.min(acceleration, maxForwardSpeed - forwardSpeed);
        return applied > 0.0
                ? clampVelocity(velocity.add(normalized.scale(applied)), maxTotalSpeed)
                : clampVelocity(velocity, maxTotalSpeed);
    }

    static Vec3 steeredVelocity(Vec3 velocity, Vec3 direction, double response, double targetSpeed) {
        if (!finite(velocity) || !finite(direction)
                || !Double.isFinite(response) || !Double.isFinite(targetSpeed)
                || response <= 0.0 || targetSpeed < 0.0 || direction.lengthSqr() <= 1.0e-8) {
            return finite(velocity) ? velocity : Vec3.ZERO;
        }
        var blend = Math.min(1.0, response);
        var desired = direction.normalize().scale(targetSpeed);
        return clampVelocity(velocity.scale(1.0 - blend).add(desired.scale(blend)));
    }

    private static void setClampedVelocity(Entity entity, Vec3 velocity) {
        setClampedVelocity(entity, velocity, MAX_SPEED);
    }

    private static void setClampedVelocity(Entity entity, Vec3 velocity, double maxSpeed) {
        if (entity == null || !finite(velocity)) return;
        if (EntityMotionGuard.currentMotionSourceEntity() instanceof ServerPlayer owner) {
            AeromanipDisplacementTracker.mark(owner, entity);
        }
        entity.setDeltaMovement(clampVelocity(velocity, maxSpeed));
        entity.hurtMarked = true;
        if (entity instanceof ServerPlayer player) {
            player.connection.send(new ClientboundSetEntityMotionPacket(player));
        }
    }

    private static Vec3 clampVelocity(Vec3 velocity) {
        return clampVelocity(velocity, MAX_SPEED);
    }

    private static Vec3 clampVelocity(Vec3 velocity, double maxSpeed) {
        var speed = velocity.length();
        var cap = Double.isFinite(maxSpeed) ? Math.max(0.0, maxSpeed) : MAX_SPEED;
        return speed > cap ? velocity.scale(cap / speed) : velocity;
    }

    public static Vec3 horizontalDirection(Vec3 direction) {
        if (!finite(direction)) return Vec3.ZERO;
        var horizontal = new Vec3(direction.x, 0.0, direction.z);
        return horizontal.lengthSqr() > 1.0e-8 ? horizontal.normalize() : Vec3.ZERO;
    }

    private static boolean finite(Vec3 value) {
        return value != null
                && Double.isFinite(value.x)
                && Double.isFinite(value.y)
                && Double.isFinite(value.z);
    }
}
