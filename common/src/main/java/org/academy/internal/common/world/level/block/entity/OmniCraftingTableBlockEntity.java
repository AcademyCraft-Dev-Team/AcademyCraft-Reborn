package org.academy.internal.common.world.level.block.entity;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.AnimationState;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

public class OmniCraftingTableBlockEntity extends BlockEntity {
    public int ticks;

    public final AnimationState unfoldingState = new AnimationState();

    public OmniCraftingTableBlockEntity(BlockPos pos, BlockState blockState) {
        super(BlockEntityTypes.OMNI_CRAFTING_TABLE, pos, blockState);
        unfoldingState.start(ticks);
    }

    public void tick() {
        ticks++;
    }
}