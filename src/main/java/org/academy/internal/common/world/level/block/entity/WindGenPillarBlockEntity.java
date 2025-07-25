package org.academy.internal.common.world.level.block.entity;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

public class WindGenPillarBlockEntity extends BlockEntity {
    public WindGenPillarBlockEntity(BlockPos pos, BlockState blockState) {
        super(BlockEntityTypes.WIND_GEN_PILLAR.get(), pos, blockState);
    }
}