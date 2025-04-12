package org.academy.fabric.internal.common.world.level.block.entity.fabric;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;
import org.academy.internal.common.world.level.block.entity.AbilityDeveloperBlockEntity;

public class AbilityDeveloperBlockEntityFabric extends AbilityDeveloperBlockEntity {
    public AbilityDeveloperBlockEntityFabric(BlockPos pos, BlockState blockState) {
        super(BlockEntityTypesFabric.ABILITY_DEVELOPER, pos, blockState);
        if (isMain()) {
            setMainPos(pos);
        }
    }

    @Override
    public int getMaxEnergyStorage() {
        return 640000;
    }
}