package org.academy.internal.common.world.level.block.entity;

import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.AnimationState;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import org.academy.AcademyCraft;
import org.academy.api.common.wireless.WirelessUser;
import org.jetbrains.annotations.Nullable;

import java.util.Objects;

public final class SolarGenBlockEntity extends BlockEntity implements WirelessUser {
    public int ticks;
    public int energyStored;

    @Nullable
    private BlockPos connectedNodePos = null;

    public final AnimationState idleState = new AnimationState();
    public final AnimationState unfoldingState = new AnimationState();

    private static final int MAX_ENERGY_STORAGE = 100_000;
    private static final int GENERATION_RATE = 50;

    public SolarGenBlockEntity(BlockPos pos, BlockState blockState) {
        super(BlockEntityTypes.SOLAR_GEN.get(), pos, blockState);
    }

    public static void tick(Level level, BlockPos pos, BlockState state, SolarGenBlockEntity blockEntity) {
        blockEntity.ticks++;
        var brightness = level.getBrightness(LightLayer.SKY, pos) - level.getSkyDarken();
        var sunAngle = level.getSunAngle(1.0F);
        var adjustedAngle = sunAngle < (float) Math.PI ? 0.0F : (float) (Math.PI * 2);
        sunAngle += (adjustedAngle - sunAngle) * 0.2F;
        brightness = Math.round(brightness * Mth.cos(sunAngle));
        brightness = Math.clamp(brightness, 0, 15);
        var hasBrightness = brightness != 0;
        blockEntity.idleState.animateWhen(!hasBrightness, blockEntity.ticks);
        blockEntity.unfoldingState.animateWhen(hasBrightness, blockEntity.ticks);
        blockEntity.setEnergyStored(blockEntity.energyStored + brightness * GENERATION_RATE);
    }

    @Override
    public @Nullable BlockPos getConnectedNodePosition() {
        return this.connectedNodePos;
    }

    @Override
    public void setConnectedNodePosition(@Nullable BlockPos nodePos) {
        if (!Objects.equals(this.connectedNodePos, nodePos)) {
            this.connectedNodePos = nodePos;
            setChanged();
            if (level != null) {
                level.sendBlockUpdated(getBlockPos(), getBlockState(), getBlockState(), Block.UPDATE_ALL);
            }
        }
    }

    @Override
    public int extractEnergy(int maxExtract, boolean simulate) {
        var energyToExtract = Math.min(maxExtract, this.energyStored);
        if (energyToExtract <= 0) {
            return 0;
        }
        if (!simulate) {
            setEnergyStored(this.energyStored - energyToExtract);
        }
        return energyToExtract;
    }

    @Override
    public int receiveEnergy(int maxReceive, boolean simulate) {
        return 0;
    }

    @Override
    public int getEnergyStored() {
        return this.energyStored;
    }

    @Override
    public int getMaxEnergyStorage() {
        return MAX_ENERGY_STORAGE;
    }

    public void setEnergyStored(int newEnergy) {
        var clamped = Math.max(0, Math.min(newEnergy, getMaxEnergyStorage()));
        if (clamped != this.energyStored) {
            this.energyStored = clamped;
            setChanged();
            if (level != null && !level.isClientSide()) {
                level.sendBlockUpdated(getBlockPos(), getBlockState(), getBlockState(), 3);
            }
        }
    }

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        output.putInt("energy_stored", this.energyStored);
        if (this.connectedNodePos != null) {
            output.putLong("connected_node_pos", this.connectedNodePos.asLong());
        }
    }

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        this.energyStored = input.getIntOr("energy_stored", 0);
        input.getLong("connected_node_pos").ifPresent(nodePos -> this.connectedNodePos = BlockPos.of(nodePos));
    }
}