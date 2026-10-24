package org.academy.internal.common.world.entity.misaka.ai;

import java.util.EnumSet;
import net.minecraft.core.BlockPos;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.pathfinder.PathComputationType;
import net.minecraft.world.phys.Vec3;
import org.academy.internal.common.world.entity.misaka.MisakaSisterEntity;

/**
 * Land exit after {@link net.minecraft.world.entity.ai.goal.FloatGoal} /
 * {@link net.minecraft.world.entity.ai.goal.BreathAirGoal} get the sister to the surface.
 * Vanilla has no swim-ashore goal for ground pathfinders — they only bob in place.
 */
public final class MisakaSwimAshoreGoal extends Goal {
    private static final double SPEED = 1.2;
    private static final int SEARCH_RANGE = 10;

    private final MisakaSisterEntity sister;
    private BlockPos shorePos;
    private int recalcCooldown;

    public MisakaSwimAshoreGoal(MisakaSisterEntity sister) {
        this.sister = sister;
        this.setFlags(EnumSet.of(Flag.MOVE, Flag.JUMP));
    }

    @Override
    public boolean canUse() {
        // Let BreathAirGoal (prio 0) own MOVE while drowning; once air recovers, leave water.
        return sister.isInWater()
                && !sister.onGround()
                && sister.getAirSupply() >= 140
                && findShore() != null;
    }

    @Override
    public boolean canContinueToUse() {
        return sister.isInWater() && !sister.onGround() && shorePos != null;
    }

    @Override
    public void start() {
        shorePos = findShore();
        recalcCooldown = 0;
        if (shorePos != null) {
            sister.getNavigation().moveTo(shorePos.getX() + 0.5, shorePos.getY(), shorePos.getZ() + 0.5, SPEED);
        }
    }

    @Override
    public void stop() {
        shorePos = null;
        sister.getNavigation().stop();
    }

    @Override
    public boolean requiresUpdateEveryTick() {
        return true;
    }

    @Override
    public void tick() {
        if (shorePos == null || recalcCooldown-- <= 0 || sister.getNavigation().isDone()) {
            shorePos = findShore();
            recalcCooldown = 20;
            if (shorePos != null) {
                sister.getNavigation().moveTo(shorePos.getX() + 0.5, shorePos.getY(), shorePos.getZ() + 0.5, SPEED);
            }
        }
        if (shorePos == null) {
            return;
        }

        var toward = new Vec3(
                shorePos.getX() + 0.5 - sister.getX(),
                0.0,
                shorePos.getZ() + 0.5 - sister.getZ()
        );
        var horiz = toward.horizontalDistanceSqr();
        if (horiz < 1.0E-4) {
            return;
        }
        toward = toward.normalize();

        // Ground nav often fails at the waterline — shove + jump onto the bank.
        var nearShore = horiz < 9.0 || atWaterline();
        if (nearShore || sister.getNavigation().isDone()) {
            var motion = sister.getDeltaMovement();
            var boost = nearShore ? 0.16 : 0.08;
            sister.setDeltaMovement(
                    motion.x + toward.x * boost,
                    Math.max(motion.y, nearShore ? 0.22 : 0.06),
                    motion.z + toward.z * boost
            );
            if (sister.getRandom().nextFloat() < (nearShore ? 0.55f : 0.25f)) {
                sister.getJumpControl().jump();
            }
        }
    }

    private boolean atWaterline() {
        var eyes = BlockPos.containing(sister.getX(), sister.getEyeY(), sister.getZ());
        return sister.level().getFluidState(eyes).isEmpty()
                || sister.getFluidHeight(FluidTags.WATER) <= sister.getFluidJumpThreshold() + 0.4;
    }

    private BlockPos findShore() {
        var origin = sister.blockPosition();
        BlockPos best = null;
        var bestDist = Double.MAX_VALUE;
        for (var dx = -SEARCH_RANGE; dx <= SEARCH_RANGE; dx++) {
            for (var dz = -SEARCH_RANGE; dz <= SEARCH_RANGE; dz++) {
                if (dx * dx + dz * dz < 4) {
                    continue;
                }
                for (var dy = -3; dy <= 4; dy++) {
                    var feet = origin.offset(dx, dy, dz);
                    if (!isDryStand(feet)) {
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
        return best;
    }

    private boolean isDryStand(BlockPos feet) {
        var level = sister.level();
        if (!level.getFluidState(feet).isEmpty() || !level.getFluidState(feet.above()).isEmpty()) {
            return false;
        }
        if (level.getFluidState(feet.below()).is(FluidTags.WATER)
                || level.getFluidState(feet.below()).is(FluidTags.LAVA)) {
            return false;
        }
        var below = level.getBlockState(feet.below());
        if (below.isAir() || below.is(Blocks.POWDER_SNOW)) {
            return false;
        }
        if (!level.getBlockState(feet).isPathfindable(PathComputationType.LAND)
                || !level.getBlockState(feet.above()).isPathfindable(PathComputationType.LAND)) {
            return false;
        }
        return level.getBlockState(feet).getCollisionShape(level, feet).isEmpty()
                && level.getBlockState(feet.above()).getCollisionShape(level, feet.above()).isEmpty();
    }
}
