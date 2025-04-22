package org.academy.fabric.internal.common.world.level.block.fabric;

import net.fabricmc.fabric.api.transfer.v1.transaction.Transaction;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.academy.fabric.internal.common.world.level.block.entity.fabric.AbilityDeveloperBlockEntityFabric;
import org.academy.fabric.internal.common.world.level.block.entity.fabric.RadioFrequencyEnergyOutputBridgeBlockEntity;
import org.academy.internal.common.world.level.block.AbilityDeveloperBlock;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import team.reborn.energy.api.base.SimpleEnergyStorage;

@SuppressWarnings({"UnstableApiUsage"})
public class AbilityDeveloperBlockFabric extends AbilityDeveloperBlock {
    public AbilityDeveloperBlockFabric(Properties properties) {
        super(properties);
        this.registerDefaultState(this.stateDefinition.any().setValue(TYPE, MultiBlockType.MAIN).setValue(FACING, Direction.NORTH));
    }

    @Override
    public @Nullable BlockEntity newBlockEntity(@NotNull BlockPos pos, @NotNull BlockState state) {
        return new AbilityDeveloperBlockEntityFabric(pos, state);
    }

    @Override
    protected <T extends BlockEntity> void tick(@NotNull Level serverLevel, @NotNull BlockPos pos, @NotNull BlockState state, @NotNull T blockEntity) {
        if (serverLevel.isClientSide() || state.getValue(TYPE) == MultiBlockType.SUBJECT) return;
        if (blockEntity instanceof AbilityDeveloperBlockEntityFabric abilityDeveloperBlockEntity) {
            BlockEntity radio = serverLevel.getBlockEntity(pos.below());
            if (radio instanceof RadioFrequencyEnergyOutputBridgeBlockEntity radioFrequencyEnergyOutputBridgeBlockEntity) {
                SimpleEnergyStorage source = radioFrequencyEnergyOutputBridgeBlockEntity.energyStorage;
                try (Transaction transaction = Transaction.openOuter()) {
                    if (abilityDeveloperBlockEntity.energyStored < abilityDeveloperBlockEntity.getMaxEnergyStorage()) {
                        int amountExtracted;
                        int shouldExtract;
                        shouldExtract = (int) (abilityDeveloperBlockEntity.getMaxEnergyStorage() - abilityDeveloperBlockEntity.energyStored);
                        if (abilityDeveloperBlockEntity.getMaxEnergyStorage() - abilityDeveloperBlockEntity.energyStored >= abilityDeveloperBlockEntity.getMaxEnergyStorage()) {
                            shouldExtract = (int) source.maxExtract;
                        }
                        amountExtracted = (int) source.extract(shouldExtract, transaction);
                        if (amountExtracted == source.maxExtract) {
                            abilityDeveloperBlockEntity.energyStored += amountExtracted;
                            transaction.commit();
                            serverLevel.sendBlockUpdated(pos, state, state, 3);
                        }
                    }
                }
            }
        }
    }
}