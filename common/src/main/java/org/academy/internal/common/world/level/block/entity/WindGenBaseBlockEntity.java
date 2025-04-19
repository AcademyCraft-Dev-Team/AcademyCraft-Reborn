package org.academy.internal.common.world.level.block.entity;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.AnimationState;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;

public class WindGenBaseBlockEntity extends BlockEntity {
    public int ticks;
    public boolean active;
    public final AnimationState activeState = new AnimationState();
    public final AnimationState shuttingState = new AnimationState();

    public WindGenBaseBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState blockState) {
        super(type, pos, blockState);
    }
}