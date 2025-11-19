package org.academy.internal.common.world.level.block.entity;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.AnimationState;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.AABB;
import org.academy.api.common.wireless.WirelessNode;
import org.academy.api.common.wireless.WirelessUser;
import org.academy.internal.client.definitions.AbilityDeveloperAnimation;
import org.jspecify.annotations.Nullable;

import java.util.Objects;

public final class AbilityDeveloperBlockEntity extends MultiBlockEntity implements WirelessUser {
    @Nullable
    public String name;
    public int energyStored;
    public int ticks;
    public final AnimationState openingState = new AnimationState();
    public final AnimationState closingState = new AnimationState();
    public final AnimationState standingState = new AnimationState();
    public final AnimationState lyingDownState = new AnimationState();
    public boolean isOpen = false;
    @Nullable
    private BlockPos connectedNodePos = null;
    private boolean playerNearby = false;

    public AbilityDeveloperBlockEntity(BlockPos pos, BlockState blockState) {
        super(BlockEntityTypes.ABILITY_DEVELOPER.get(), pos, blockState);
        if (isMain()) {
            lyingDownState.start(ticks);
        }
    }

    public void setOpen(boolean open) {
        var previousIsOpen = isOpen;
        isOpen = open;
        var currentAnimationState = previousIsOpen ? openingState : closingState;
        var targetAnimationState = open ? openingState : closingState;
        var targetAnimationDefinition = open ? AbilityDeveloperAnimation.OPENING : AbilityDeveloperAnimation.CLOSING;
        var elapsedMillis = currentAnimationState.getTimeInMillis(ticks);
        openingState.stop();
        closingState.stop();
        if (elapsedMillis > 0) {
            var elapsedSeconds = elapsedMillis / 1000.0f;
            var totalDuration = targetAnimationDefinition.lengthInSeconds();
            var targetStartSeconds = Math.max(0.0f, Math.min(totalDuration, totalDuration - elapsedSeconds));
            var targetElapsedTicks = (long) (targetStartSeconds * 20.0f);
            var adjustedStartTick = ticks - targetElapsedTicks;
            targetAnimationState.start((int) Math.max(Integer.MIN_VALUE, Math.min(Integer.MAX_VALUE, adjustedStartTick)));
        } else {
            targetAnimationState.start(ticks);
        }
    }

    private void handleStateChangeOnClient(boolean wasNearby, boolean isNearby) {
        if (wasNearby == isNearby) return;

        var currentAnimationState = wasNearby ? standingState : lyingDownState;
        var targetAnimationState = isNearby ? standingState : lyingDownState;
        var targetAnimationDefinition = isNearby ? AbilityDeveloperAnimation.STANDING : AbilityDeveloperAnimation.LYING_DOWN;

        var elapsedMillis = currentAnimationState.getTimeInMillis(ticks);
        currentAnimationState.stop();

        if (elapsedMillis > 0) {
            var elapsedSeconds = elapsedMillis / 1000.0f;
            var totalDuration = targetAnimationDefinition.lengthInSeconds();
            var targetStartSeconds = Math.max(0.0f, Math.min(totalDuration, totalDuration - elapsedSeconds));
            var targetElapsedTicks = (long) (targetStartSeconds * 20.0f);
            var adjustedStartTick = ticks - targetElapsedTicks;
            targetAnimationState.start((int) Math.max(Integer.MIN_VALUE, Math.min(Integer.MAX_VALUE, adjustedStartTick)));
        } else {
            targetAnimationState.start(ticks);
        }
    }

    @Nullable
    @Override
    public BlockPos getConnectedNodePosition() {
        if (!isMain() && level != null && mainPos != null) {
            var mainBE = getMain();
            if (mainBE instanceof AbilityDeveloperBlockEntity mainDevBE) {
                return mainDevBE.getConnectedNodePosition();
            }
            return null;
        }
        return connectedNodePos;
    }

    @Override
    public void setConnectedNodePosition(@Nullable BlockPos nodePos) {
        if (!isMain() && level != null && mainPos != null) {
            var mainBE = getMain();
            if (mainBE instanceof AbilityDeveloperBlockEntity mainDevBE) {
                mainDevBE.setConnectedNodePosition(nodePos);
            }
            return;
        }
        if (!Objects.equals(connectedNodePos, nodePos)) {
            connectedNodePos = nodePos;
            setChanged();
            if (level != null && !level.isClientSide()) {
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
        var maxEnergyCanStore = getMaxEnergyStorage();
        var energyStoredDouble = getEnergyStored();
        var maxCanReceive = Math.max(0, maxEnergyCanStore - energyStoredDouble);
        var energyToReceive = Math.min(maxReceive, maxCanReceive);
        if (energyToReceive <= 0) return 0;
        if (!simulate) setEnergyStored(getEnergyStored() + energyToReceive);
        return energyToReceive;
    }

    public void setEnergyStored(int newEnergy) {
        var clamped = Math.max(0, Math.min(newEnergy, getMaxEnergyStorage()));
        if (clamped != energyStored) {
            energyStored = clamped;
            setChanged();
            if (level != null && !level.isClientSide()) {
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
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        if (isMain()) {
            output.putInt("energy_stored", energyStored);
            output.putBoolean("is_open", isOpen);
            if (connectedNodePos != null) {
                output.putLong("connected_node_pos", connectedNodePos.asLong());
            }
        }
    }

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        if (isMain()) {
            energyStored = input.getIntOr("energy_stored", 0);
            connectedNodePos = BlockPos.of(input.getLongOr("connected_node_pos", 0));
            isOpen = input.getBooleanOr("is_open", false);
        } else {
            connectedNodePos = null;
            if (level != null && mainPos != null && level.isClientSide()) {
                var mainBE = level.getBlockEntity(mainPos);
                if (mainBE instanceof AbilityDeveloperBlockEntity mainDevBE) {
                    isOpen = mainDevBE.isOpen;
                    energyStored = mainDevBE.getEnergyStored();
                    connectedNodePos = mainDevBE.connectedNodePos;
                }
            }
        }
    }

    public void clientTick() {
        ticks++;

        if (isMain() && level != null) {
            var wasNearby = playerNearby;
            var nearbyPlayers = level.getEntitiesOfClass(Player.class, new AABB(worldPosition).inflate(10.0));
            playerNearby = !nearbyPlayers.isEmpty() && energyStored > 0;

            if (wasNearby != playerNearby) {
                handleStateChangeOnClient(wasNearby, playerNearby);
            }
        }
    }

    public void serverTick(ServerLevel level) {
        ticks++;
        if (isMain()) {
            if (connectedNodePos == null) {
                setConnectedNodePosition(null);
            } else {
                var nodeBE = level.getBlockEntity(connectedNodePos);
                if (!(nodeBE instanceof WirelessNode)) {
                    setConnectedNodePosition(null);
                }
            }
        }
    }

    public AABB getRenderBoundingBox() {
        var pos = getBlockPos().getCenter();
        var radius = 5d;
        return new AABB(pos.x - radius, pos.y - radius, pos.z - radius, pos.x + radius, pos.y + radius, pos.z + radius);
    }
}