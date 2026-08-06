package org.academy.internal.common.ability.mentalout.skills;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;

public final class MentaloutTargeting {
    static final double MAX_RANGE = 16.0;
    private static final double MAX_RANGE_SQR = MAX_RANGE * MAX_RANGE;

    private MentaloutTargeting() {
    }

    static LivingEntity findLookedAtLiving(ServerPlayer player) {
        return findLookedAtLiving(player, MAX_RANGE);
    }

    public static LivingEntity findLookedAtLiving(ServerPlayer player, double range) {
        range = Math.clamp(Double.isFinite(range) ? range : MAX_RANGE, 1.0, MAX_RANGE);
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
        return isValidTarget(player, living, range) ? living : null;
    }

    static boolean isValidTarget(ServerPlayer player, LivingEntity target) {
        return isValidTarget(player, target, MAX_RANGE);
    }

    public static boolean isValidTarget(ServerPlayer player, LivingEntity target, double range) {
        range = Math.clamp(Double.isFinite(range) ? range : MAX_RANGE, 1.0, MAX_RANGE);
        return target != player
                && target.isAlive()
                && !target.isRemoved()
                && target.level() == player.level()
                && target.getBoundingBox().distanceToSqr(player.getEyePosition()) <= range * range;
    }
}
