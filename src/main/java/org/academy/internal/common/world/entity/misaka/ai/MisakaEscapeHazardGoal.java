package org.academy.internal.common.world.entity.misaka.ai;

import net.minecraft.core.BlockPos;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.entity.ai.goal.PanicGoal;
import net.minecraft.world.entity.ai.util.DefaultRandomPos;
import net.minecraft.world.level.block.PowderSnowBlock;
import net.minecraft.world.phys.Vec3;
import org.academy.internal.common.world.entity.misaka.MisakaSisterEntity;

/**
 * Thin PanicGoal extension: vanilla already covers fire/lava/cactus/freeze <em>after</em>
 * damage via {@link DamageTypeTags#PANIC_CAUSES}. Powder snow freezes for seconds first and
 * pathfinding treats it as blocked, so we only add preemptive snow exit + a shove assist.
 *
 * <p>Water: vanilla {@link net.minecraft.world.entity.ai.goal.BreathAirGoal} +
 * {@link net.minecraft.world.entity.ai.goal.FloatGoal} surface; shore exit is
 * {@link MisakaSwimAshoreGoal} (vanilla has no land-exit goal for ground mobs).
 */
public final class MisakaEscapeHazardGoal extends PanicGoal {
    private static final double SPEED = 1.35;
    private final MisakaSisterEntity sister;

    public MisakaEscapeHazardGoal(MisakaSisterEntity sister) {
        super(sister, SPEED, DamageTypeTags.PANIC_CAUSES);
        this.sister = sister;
    }

    @Override
    protected boolean shouldPanic() {
        // Preempt freeze damage; everything else is vanilla panic tags after a hit.
        return sister.isInPowderSnow
                || sister.isFreezing()
                || standingInPowderSnow()
                || super.shouldPanic();
    }

    @Override
    protected boolean findRandomPosition() {
        if (sister.isInPowderSnow || sister.isFreezing() || standingInPowderSnow()) {
            var safe = findNearestNonSnowStand();
            if (safe != null) {
                this.posX = safe.getX() + 0.5;
                this.posY = safe.getY();
                this.posZ = safe.getZ() + 0.5;
                return true;
            }
        }
        // On fire: PanicGoal already prefers water via lookForWater in canUse.
        return super.findRandomPosition();
    }

    @Override
    public void tick() {
        // Pathfinding cannot route through powder snow — keep a horizontal shove.
        if (sister.isInPowderSnow || sister.isFreezing() || standingInPowderSnow()) {
            if (sister.getNavigation().isDone() || sister.tickCount % 15 == 0) {
                if (findRandomPosition()) {
                    sister.getNavigation().moveTo(this.posX, this.posY, this.posZ, SPEED);
                }
            }
            var toward = new Vec3(posX - sister.getX(), 0.0, posZ - sister.getZ());
            if (toward.lengthSqr() < 1.0E-4) {
                toward = new Vec3(
                        sister.getRandom().nextDouble() - 0.5,
                        0.0,
                        sister.getRandom().nextDouble() - 0.5
                );
            }
            toward = toward.normalize().scale(0.18);
            var motion = sister.getDeltaMovement();
            sister.setDeltaMovement(motion.x + toward.x, Math.max(motion.y, 0.12), motion.z + toward.z);
            if (sister.getRandom().nextFloat() < 0.4f) {
                sister.getJumpControl().jump();
            }
        }
    }

    @Override
    public boolean canContinueToUse() {
        if (sister.isInPowderSnow || sister.isFreezing() || standingInPowderSnow()) {
            return true;
        }
        return super.canContinueToUse();
    }

    private boolean standingInPowderSnow() {
        return sister.level().getBlockState(sister.blockPosition()).getBlock() instanceof PowderSnowBlock
                || sister.level().getBlockState(BlockPos.containing(sister.getX(), sister.getY() + 0.2, sister.getZ()))
                .getBlock() instanceof PowderSnowBlock;
    }

    private BlockPos findNearestNonSnowStand() {
        var origin = sister.blockPosition();
        BlockPos best = null;
        var bestDist = Double.MAX_VALUE;
        for (var dx = -6; dx <= 6; dx++) {
            for (var dz = -6; dz <= 6; dz++) {
                if (dx == 0 && dz == 0) {
                    continue;
                }
                for (var dy = -2; dy <= 2; dy++) {
                    var feet = origin.offset(dx, dy, dz);
                    if (!isClearStand(feet)) {
                        continue;
                    }
                    var dist = sister.distanceToSqr(feet.getX() + 0.5, feet.getY(), feet.getZ() + 0.5);
                    if (dist < bestDist) {
                        bestDist = dist;
                        best = feet.immutable();
                    }
                }
            }
        }
        if (best != null) {
            return best;
        }
        var fallback = DefaultRandomPos.getPos(sister, 8, 4);
        return fallback == null ? null : BlockPos.containing(fallback);
    }

    private boolean isClearStand(BlockPos feet) {
        var level = sister.level();
        if (level.getBlockState(feet).getBlock() instanceof PowderSnowBlock
                || level.getBlockState(feet.above()).getBlock() instanceof PowderSnowBlock
                || level.getBlockState(feet.below()).getBlock() instanceof PowderSnowBlock) {
            return false;
        }
        var below = level.getBlockState(feet.below());
        if (below.isAir() || below.getBlock() instanceof PowderSnowBlock) {
            return false;
        }
        return level.getBlockState(feet).getCollisionShape(level, feet).isEmpty()
                && level.getBlockState(feet.above()).getCollisionShape(level, feet.above()).isEmpty();
    }
}
