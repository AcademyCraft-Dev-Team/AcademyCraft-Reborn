package org.academy.internal.common.world.level.block;

import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockBehaviour;

public class WindGenPillarBlock extends Block {
    public WindGenPillarBlock() {
        super(BlockBehaviour.Properties.of().noOcclusion());
    }
}