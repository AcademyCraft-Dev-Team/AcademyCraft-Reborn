package org.academy.internal.common.world.entity.misaka.ai;

import net.minecraft.core.BlockPos;
import net.minecraft.world.Container;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.academy.internal.common.world.entity.misaka.MisakaFoodTraits;
import org.academy.internal.common.world.entity.misaka.MisakaSisterEntity;
import org.academy.internal.common.world.entity.misaka.WanderStyle;

import java.util.EnumSet;

public final class MisakaCropGrazeGoal extends Goal {
    private static final int SEARCH_RADIUS = 6;
    private static final int CONTAINER_RADIUS = 8;
    private final MisakaSisterEntity sister;
    private BlockPos targetPos;

    public MisakaCropGrazeGoal(MisakaSisterEntity sister) {
        this.sister = sister;
        setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
    }

    @Override
    public boolean canUse() {
        if (!sister.isAwakened()
                || sister.getWanderStyle() == WanderStyle.WAITING
                || sister.getFoodLevel() > 6) {
            return false;
        }
        // Plan: graze only when hungry AND forage containers have no edible food.
        if (hasNearbyContainerFood()) {
            return false;
        }
        targetPos = findMatureCrop();
        return targetPos != null;
    }

    @Override
    public boolean canContinueToUse() {
        return targetPos != null
                && sister.getWanderStyle() != WanderStyle.WAITING
                && sister.distanceToSqr(Vec3.atCenterOf(targetPos)) > 1.5;
    }

    @Override
    public void start() {
        if (targetPos != null) {
            sister.getNavigation().moveTo(targetPos.getX() + 0.5, targetPos.getY(), targetPos.getZ() + 0.5, 0.75);
        }
    }

    @Override
    public void tick() {
        if (targetPos == null) {
            return;
        }
        if (sister.distanceToSqr(Vec3.atCenterOf(targetPos)) <= 2.25) {
            grazeCrop(targetPos);
            targetPos = null;
        }
    }

    private boolean hasNearbyContainerFood() {
        var origin = sister.blockPosition();
        for (int dx = -CONTAINER_RADIUS; dx <= CONTAINER_RADIUS; dx++) {
            for (int dy = -2; dy <= 2; dy++) {
                for (int dz = -CONTAINER_RADIUS; dz <= CONTAINER_RADIUS; dz++) {
                    var pos = origin.offset(dx, dy, dz);
                    BlockEntity blockEntity = sister.level().getBlockEntity(pos);
                    if (!(blockEntity instanceof Container container)
                            || !MisakaFoodTraits.isForageContainer(blockEntity)) {
                        continue;
                    }
                    for (int slot = 0; slot < container.getContainerSize(); slot++) {
                        if (MisakaFoodTraits.isForageConsumable(container.getItem(slot))) {
                            return true;
                        }
                    }
                }
            }
        }
        return false;
    }

    private BlockPos findMatureCrop() {
        var origin = sister.blockPosition();
        for (int dx = -SEARCH_RADIUS; dx <= SEARCH_RADIUS; dx++) {
            for (int dz = -SEARCH_RADIUS; dz <= SEARCH_RADIUS; dz++) {
                var pos = origin.offset(dx, -1, dz);
                BlockState state = sister.level().getBlockState(pos);
                Block block = state.getBlock();
                if (block instanceof CropBlock crop && crop.isMaxAge(state)) {
                    return pos.immutable();
                }
            }
        }
        return null;
    }

    private void grazeCrop(BlockPos pos) {
        BlockState state = sister.level().getBlockState(pos);
        Block block = state.getBlock();
        if (!(block instanceof CropBlock crop) || !crop.isMaxAge(state)) {
            return;
        }
        sister.level().setBlock(pos, crop.getStateForAge(0), Block.UPDATE_ALL);
        sister.getFoodData().eat(4, 0.2f);
        sister.syncFoodLevel();
    }
}
