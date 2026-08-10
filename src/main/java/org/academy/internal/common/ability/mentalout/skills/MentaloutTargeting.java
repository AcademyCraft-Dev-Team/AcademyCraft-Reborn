package org.academy.internal.common.ability.mentalout.skills;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.academy.api.common.entitycontrol.ControlDestination;
import org.jspecify.annotations.Nullable;

public final class MentaloutTargeting {
    static final double MAX_RANGE = 16.0;
    public static final double MAX_SIGHT_RANGE = 64.0;
    public static final double PROFICIENCY_MAX_SIGHT_RANGE = 80.0;

    private MentaloutTargeting() {
    }

    static LivingEntity findLookedAtLiving(ServerPlayer player) {
        return findLookedAtLiving(player, MAX_RANGE);
    }

    public static LivingEntity findLookedAtLiving(ServerPlayer player, double range) {
        return findLookedAtLiving(player, range, MAX_RANGE);
    }

    public static LivingEntity findPrecisionLookedAtLiving(ServerPlayer player) {
        return findLookedAtLiving(player, MAX_SIGHT_RANGE, MAX_SIGHT_RANGE);
    }

    public static LivingEntity findLookedAtLivingExtended(ServerPlayer player, double range) {
        return findLookedAtLiving(player, range, PROFICIENCY_MAX_SIGHT_RANGE);
    }

    private static LivingEntity findLookedAtLiving(ServerPlayer player, double range, double maximumRange) {
        range = Math.clamp(Double.isFinite(range) ? range : maximumRange, 1.0, maximumRange);
        var level = player.level();
        var eye = player.getEyePosition();
        var look = player.getLookAngle();
        if (look.lengthSqr() <= 1.0e-6) return null;

        var end = eye.add(look.normalize().scale(range));
        var blockHit = level.clip(new ClipContext(
                eye,
                end,
                ClipContext.Block.COLLIDER,
                ClipContext.Fluid.NONE,
                player
        ));
        var entityRayEnd = blockHit.getType() == HitResult.Type.MISS
                ? end
                : blockHit.getLocation();
        var entityHit = ProjectileUtil.getEntityHitResult(
                level,
                player,
                eye,
                entityRayEnd,
                new AABB(eye, entityRayEnd).inflate(1.0),
                entity -> entity instanceof LivingEntity living
                        && living != player
                        && living.isAlive()
                        && living.isPickable()
                        && !living.isSpectator(),
                0.3f
        );
        if (entityHit == null || !(entityHit.getEntity() instanceof LivingEntity living)) return null;
        return isValidTarget(player, living, range, maximumRange) ? living : null;
    }

    static boolean isValidTarget(ServerPlayer player, LivingEntity target) {
        return isValidTarget(player, target, MAX_RANGE);
    }

    public static @Nullable ControlDestination findSightDestination(LivingEntity observer, double range) {
        if (observer == null || !observer.isAlive() || observer.isRemoved()) return null;
        range = Math.clamp(Double.isFinite(range) ? range : PROFICIENCY_MAX_SIGHT_RANGE,
                1.0, PROFICIENCY_MAX_SIGHT_RANGE);
        var level = observer.level();
        var eye = observer.getEyePosition();
        var look = observer.getLookAngle();
        if (look.lengthSqr() <= 1.0e-6) return null;

        var end = eye.add(look.normalize().scale(range));
        var blockHit = level.clip(new ClipContext(
                eye,
                end,
                ClipContext.Block.COLLIDER,
                ClipContext.Fluid.NONE,
                observer
        ));
        var entityRayEnd = blockHit.getType() == HitResult.Type.MISS ? end : blockHit.getLocation();
        var entityHit = ProjectileUtil.getEntityHitResult(
                level,
                observer,
                eye,
                entityRayEnd,
                new AABB(eye, entityRayEnd).inflate(1.0),
                entity -> isSightTarget(observer, entity),
                0.3f
        );
        if (entityHit != null && entityHit.getEntity() instanceof LivingEntity living) {
            return new ControlDestination.Entity(living.getUUID());
        }
        return blockHit.getType() == HitResult.Type.BLOCK
                ? new ControlDestination.Position(
                level.dimension().identifier(),
                Vec3.atBottomCenterOf(blockHit.getBlockPos().relative(blockHit.getDirection()))
        )
                : null;
    }

    private static boolean isSightTarget(LivingEntity observer, Entity entity) {
        return entity instanceof LivingEntity living
                && living != observer
                && living.isAlive()
                && living.isPickable()
                && !living.isSpectator();
    }

    public static boolean isValidTarget(ServerPlayer player, LivingEntity target, double range) {
        return isValidTarget(player, target, range, MAX_RANGE);
    }

    private static boolean isValidTarget(
            ServerPlayer player,
            LivingEntity target,
            double range,
            double maximumRange
    ) {
        range = Math.clamp(Double.isFinite(range) ? range : maximumRange, 1.0, maximumRange);
        return target != player
                && target.isAlive()
                && !target.isRemoved()
                && target.level() == player.level()
                && target.getBoundingBox().distanceToSqr(player.getEyePosition()) <= range * range;
    }
}
