package org.academy.internal.common.ability.aeromanip;

import net.minecraft.network.protocol.game.ClientboundSetEntityMotionPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.boss.enderdragon.EnderDragon;
import net.minecraft.world.entity.boss.wither.WitherBoss;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.phys.Vec3;
import org.academy.internal.common.world.damagesource.FriendlyFireSetting;
import net.minecraft.util.Mth;

public final class AeromanipTargeting {
    private static final double MAX_SPEED = 3.0;

    private AeromanipTargeting() {
    }

    public static boolean canAffectNegatively(ServerPlayer owner, Entity target) {
        if (target == null || target == owner || target.isRemoved() || target.isSpectator()) return false;
        if (target instanceof LivingEntity living && FriendlyFireSetting.shouldPrevent(owner, living)) return false;
        return !owner.isAlliedTo(target);
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

    static Vec3 acceleratedVelocity(Vec3 velocity, Vec3 direction, double acceleration, double maxForwardSpeed) {
        if (!finite(velocity) || !finite(direction)
                || !Double.isFinite(acceleration) || !Double.isFinite(maxForwardSpeed)
                || acceleration <= 0.0 || maxForwardSpeed <= 0.0 || direction.lengthSqr() <= 1.0e-8) {
            return finite(velocity) ? velocity : Vec3.ZERO;
        }
        var normalized = direction.normalize();
        var forwardSpeed = velocity.dot(normalized);
        var applied = Math.min(acceleration, maxForwardSpeed - forwardSpeed);
        return applied > 0.0 ? clampVelocity(velocity.add(normalized.scale(applied))) : clampVelocity(velocity);
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
        if (entity == null || !finite(velocity)) return;
        entity.setDeltaMovement(clampVelocity(velocity));
        entity.hurtMarked = true;
        if (entity instanceof ServerPlayer player) {
            player.connection.send(new ClientboundSetEntityMotionPacket(player));
        }
    }

    private static Vec3 clampVelocity(Vec3 velocity) {
        var speed = velocity.length();
        return speed > MAX_SPEED ? velocity.scale(MAX_SPEED / speed) : velocity;
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
