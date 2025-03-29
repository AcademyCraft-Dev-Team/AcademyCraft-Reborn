package org.academy.forge.internal.common.world.level.block.forge;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import org.academy.forge.internal.common.world.level.block.entity.forge.AbilityDeveloperBlockEntityForge;
import org.academy.forge.internal.common.world.level.block.entity.forge.RadioFrequencyEnergyOutputBridgeBlockEntity;
import org.academy.internal.common.world.level.block.AbilityDeveloperBlock;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class AbilityDeveloperBlockForge extends AbilityDeveloperBlock {
    public AbilityDeveloperBlockForge(Properties properties) {
        super(properties);
        this.registerDefaultState(this.stateDefinition.any().setValue(TYPE, MultiBlockType.MAIN).setValue(FACING, Direction.NORTH));
    }

    @Override
    public @Nullable BlockEntity newBlockEntity(@NotNull BlockPos blockPos, @NotNull BlockState blockState) {
        return new AbilityDeveloperBlockEntityForge(blockPos, blockState);
    }

    @Override
    public @Nullable <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, @NotNull BlockState state, @NotNull BlockEntityType<T> blockEntityType) {
        if (level.isClientSide() || state.getValue(TYPE) == MultiBlockType.SUBJECT) return null;
        return (serverLevel, blockPos, blockState, blockEntity) -> {
            if (blockEntity instanceof AbilityDeveloperBlockEntityForge abilityDeveloperBlockEntity) {
                if (abilityDeveloperBlockEntity.getMaxStored() >= abilityDeveloperBlockEntity.energyStored) return;
                BlockEntity radio = serverLevel.getBlockEntity(blockPos.below());
                if (radio instanceof RadioFrequencyEnergyOutputBridgeBlockEntity radioFrequencyEnergyOutputBridgeBlockEntity) {
                    radioFrequencyEnergyOutputBridgeBlockEntity.getCapability(ForgeCapabilities.ENERGY).ifPresent(iEnergyStorage -> {
                        int shouldExtract = (int) Math.min(iEnergyStorage.getEnergyStored(), abilityDeveloperBlockEntity.getMaxStored() - abilityDeveloperBlockEntity.energyStored);
                        int extractEnergy = iEnergyStorage.extractEnergy(shouldExtract, false);
                        if (extractEnergy > 0) {
                            abilityDeveloperBlockEntity.energyStored += extractEnergy;
                        }
                    });
                }
            }
        };
    }
}