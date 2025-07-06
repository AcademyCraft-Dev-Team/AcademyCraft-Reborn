package org.academy.internal.common.world.level.block.entity;

import net.minecraft.client.animation.AnimationDefinition;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.AnimationState;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import org.academy.api.common.wireless.WirelessNode;
import org.academy.api.common.wireless.WirelessUser;
import org.academy.internal.client.model.AbilityDeveloperBlockEntityModel;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Objects;

public class AbilityDeveloperBlockEntity extends MultiBlockEntity implements WirelessUser {
    public String name;
    public int energyStored;
    public int ticks;
    public final AnimationState openState = new AnimationState();
    public final AnimationState closingState = new AnimationState();
    public final AnimationState standState = new AnimationState();
    public final AnimationState liedownState = new AnimationState();
    public boolean isOpen = false;
    private BlockPos connectedNodePos = null;

    public AbilityDeveloperBlockEntity(BlockPos pos, BlockState blockState) {
        super(BlockEntityTypes.ABILITY_DEVELOPER, pos, blockState);
        standState.start(ticks);
    }

    public void setOpen(boolean open) {
        boolean previousIsOpen = this.isOpen;
        this.isOpen = open;
        this.openState.animateWhen(this.isOpen, this.ticks);
        this.closingState.animateWhen(!this.isOpen, this.ticks);
        AnimationState currentAnimationState = previousIsOpen ? openState : closingState;
        AnimationState targetAnimationState = open ? openState : closingState;
        AnimationDefinition targetAnimationDefinition = open ? AbilityDeveloperBlockEntityModel.open : AbilityDeveloperBlockEntityModel.close;
        long elapsedMillis = currentAnimationState.getAccumulatedTime();
        currentAnimationState.stop();
        if (elapsedMillis > 0) {
            float elapsedSeconds = elapsedMillis / 1000.0f;
            float totalDuration = targetAnimationDefinition.lengthInSeconds();
            float targetStartSeconds = Math.max(0.0f, Math.min(totalDuration, totalDuration - elapsedSeconds));
            long targetElapsedTicks = (long) (targetStartSeconds * 20.0f);
            long adjustedStartTick = this.ticks - targetElapsedTicks;
            targetAnimationState.start((int) Math.max(Integer.MIN_VALUE, Math.min(Integer.MAX_VALUE, adjustedStartTick)));
        } else {
            targetAnimationState.start(this.ticks);
        }
    }

    @Nullable
    @Override
    public BlockPos getConnectedNodePosition() {
        if (!isMain() && level != null && mainPos != null) {
            BlockEntity mainBE = getMain();
            if (mainBE instanceof AbilityDeveloperBlockEntity mainDevBE) {
                return mainDevBE.getConnectedNodePosition();
            }
            return null;
        }
        return this.connectedNodePos;
    }

    @Override
    public void setConnectedNodePosition(@Nullable BlockPos nodePos) {
        if (!isMain() && level != null && mainPos != null) {
            BlockEntity mainBE = getMain();
            if (mainBE instanceof AbilityDeveloperBlockEntity mainDevBE) {
                mainDevBE.setConnectedNodePosition(nodePos);
            }
            return;
        }
        if (!Objects.equals(this.connectedNodePos, nodePos)) {
            this.connectedNodePos = nodePos;
            setChanged();
            if (level != null && !level.isClientSide) {
                level.sendBlockUpdated(getBlockPos(), getBlockState(), getBlockState(), Block.UPDATE_ALL);
            }
        }
    }

    @Override
    public int extractEnergy(int maxExtract, boolean simulate) {
        return 0;
    }

    @Override
    public int receiveEnergy(int maxReceive, boolean simulate) {
        int maxEnergyCanStore = getMaxEnergyStorage();
        int energyStoredDouble = getEnergyStored();
        int maxCanReceive = Math.max(0, maxEnergyCanStore - energyStoredDouble);
        int energyToReceive = Math.min(maxReceive, maxCanReceive);
        if (energyToReceive <= 0) return 0;
        if (!simulate) setEnergyStored(getEnergyStored() + energyToReceive);
        return energyToReceive;
    }

    public void setEnergyStored(int newEnergy) {
        int clamped = Math.max(0, Math.min(newEnergy, getMaxEnergyStorage()));
        if (clamped != energyStored) {
            energyStored = clamped;
            setChanged();
            if (level != null && !level.isClientSide) {
                level.sendBlockUpdated(getBlockPos(), getBlockState(), getBlockState(), 3);
            }
        }
    }

    @Override
    public int getEnergyStored() {
        return energyStored;
    }

    @Override
    public int getMaxEnergyStorage() {
        return 1440_000;
    }

    @Override
    protected void saveAdditional(@NotNull CompoundTag tag) {
        super.saveAdditional(tag);
        if (isMain()) {
            tag.putInt("energy_stored", energyStored);
            tag.putBoolean("is_open", isOpen);
            if (connectedNodePos != null) {
                tag.put("connected_node_pos", NbtUtils.writeBlockPos(connectedNodePos));
            }
        }
    }

    @Override
    public void load(@NotNull CompoundTag tag) {
        super.load(tag);
        if (isMain()) {
            energyStored = tag.getInt("energy_stored");
            if (tag.contains("connected_node_pos", Tag.TAG_COMPOUND)) {
                connectedNodePos = NbtUtils.readBlockPos(tag.getCompound("connected_node_pos"));
            } else {
                connectedNodePos = null;
            }
            isOpen = tag.getBoolean("is_open");
        } else {
            connectedNodePos = null;
            if (level != null && mainPos != null && level.isClientSide) {
                BlockEntity mainBE = level.getBlockEntity(mainPos);
                if (mainBE instanceof AbilityDeveloperBlockEntity mainDevBE) {
                    isOpen = mainDevBE.isOpen;
                    energyStored = mainDevBE.getEnergyStored();
                    connectedNodePos = mainDevBE.connectedNodePos;
                }
            }
        }
    }

    public void clientTick() {
        this.ticks++;
    }

    public void serverTick(@NotNull ServerLevel level) {
        this.ticks++;
        if (connectedNodePos == null) {
            setConnectedNodePosition(null);
        } else {
            BlockEntity nodeBE = level.getBlockEntity(connectedNodePos);
            if (!(nodeBE instanceof WirelessNode)) {
                setConnectedNodePosition(null);
            }
        }
    }

    // For Forge
    @SuppressWarnings("unused")
    public AABB getRenderBoundingBox() {
        var pos = this.getBlockPos().getCenter();
        var radius = 5d;
        return new AABB(pos.x - radius, pos.y - radius, pos.z - radius, pos.x + radius, pos.y + radius, pos.z + radius);
    }
}