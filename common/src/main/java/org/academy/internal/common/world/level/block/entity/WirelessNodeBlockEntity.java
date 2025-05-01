package org.academy.internal.common.world.level.block.entity;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import org.academy.AcademyCraft;
import org.academy.api.common.wireless.WirelessNode;
import org.academy.api.common.wireless.WirelessUser;
import org.academy.api.server.wireless.WirelessManager;
import org.academy.internal.server.world.level.storage.WorldData;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;

public abstract class WirelessNodeBlockEntity extends BlockEntity implements WirelessNode {
    private int energyStored = 5000;
    public WorldData.WirelessNetworkData.NodeConfig cachedConfig = null;

    public WirelessNodeBlockEntity(BlockEntityType<? extends WirelessNodeBlockEntity> blockEntityType, BlockPos pos, BlockState blockState) {
        super(blockEntityType, pos, blockState);
    }

    public void serverTick(ServerLevel serverLevel, BlockPos pos) {
        if (level == null) return;

        if (cachedConfig == null) {
            WorldData.WirelessNetworkData networkData = WorldData.WirelessNetworkData.get(serverLevel);
            cachedConfig = networkData.getNodeConfig(pos);
            if (cachedConfig == null && level != null && level.getGameTime() > 1) {
                AcademyCraft.LOGGER.warn("Wireless Node BE at {} ticking but not (yet?) registered in SavedData!", pos);
            }
            return;
        }


        Map<WirelessUser, WorldData.WirelessNetworkData.UserConfig> userMap = new HashMap<>();
        for (BlockPos userPos : cachedConfig.connectedUsers.keySet()) {
            BlockEntity userBE = serverLevel.getBlockEntity(userPos);
            if (!(userBE instanceof WirelessUser user)) {
                handleUserDisconnect(serverLevel, userPos);
            } else {
                userMap.put(user, cachedConfig.connectedUsers.get(userPos));
            }
        }

        if (WirelessManager.balanceEnergy(this, new HashMap<>(userMap))) {
            setChanged();
        }
    }

    private void handleUserDisconnect(ServerLevel level, BlockPos userPos) {
        AcademyCraft.LOGGER.warn("Node at {} detected invalid or missing user at {}. Requesting disconnect from SavedData.", worldPosition, userPos);
        WorldData.WirelessNetworkData networkData = WorldData.WirelessNetworkData.get(level);
        boolean removed = networkData.disconnectUserFromNode(this.worldPosition, userPos);
        if (removed) {
            cachedConfig = networkData.getNodeConfig(this.worldPosition);
            AcademyCraft.LOGGER.debug("Successfully disconnected user {} from node {} in SavedData.", userPos, worldPosition);
        } else {
            AcademyCraft.LOGGER.warn("Failed request to disconnect user {} from node {} in SavedData.", userPos, worldPosition);
        }
        BlockEntity userBE = level.getBlockEntity(userPos);
        if (userBE instanceof WirelessUser user) {
            try {
                user.setConnectedNodePosition(null);
            } catch (Exception e) {
                AcademyCraft.LOGGER.error("Error notifying potentially invalid user BE at {} about disconnect: {}", userPos, e.getMessage());
            }
        }
    }

    @Override
    public int getEnergyStored() {
        return this.energyStored;
    }

    @Override
    public void setEnergyStored(int energy) {
        double oldEnergy = this.energyStored;
        this.energyStored = Math.max(0, Math.min(energy, getMaxEnergyStorage()));
        if (oldEnergy != this.energyStored) {
            setChanged();
        }
    }

    @Override
    public int extractFromUser(WirelessUser user, int maxAmount, boolean simulate) {
        try {
            return user.extractEnergy(maxAmount, simulate);
        } catch (Exception e) {
            AcademyCraft.LOGGER.error("Error extracting energy from user at {}: {}", user, e.getMessage());
            return 0;
        }
    }

    @Override
    public int insertIntoUser(WirelessUser user, int maxAmount, boolean simulate) {
        try {
            return user.receiveEnergy(maxAmount, simulate);
        } catch (Exception e) {
            AcademyCraft.LOGGER.error("Error inserting energy into user at {}: {}", user, e.getMessage());
            return 0;
        }
    }

    @Override
    protected void saveAdditional(@NotNull CompoundTag tag) {
        super.saveAdditional(tag);
        tag.putInt("energy_stored", energyStored);
    }

    @Override
    public void load(@NotNull CompoundTag tag) {
        super.load(tag);
        energyStored = tag.getInt("energy_stored");
        this.cachedConfig = null;
    }

    @Override
    public @NotNull CompoundTag getUpdateTag() {
        return saveWithoutMetadata();
    }

    @Nullable
    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }


}