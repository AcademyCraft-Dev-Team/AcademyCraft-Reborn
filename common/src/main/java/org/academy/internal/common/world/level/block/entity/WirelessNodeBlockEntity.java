package org.academy.internal.common.world.level.block.entity;

import net.minecraft.core.BlockPos;
import net.minecraft.core.NonNullList;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
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
import java.util.Objects;

public abstract class WirelessNodeBlockEntity extends BlockEntity implements WirelessNode, WirelessUser, Container {
    private int energyStored = 5000;
    public WorldData.WirelessNetworkData.NodeConfig cachedConfig = null;
    public NonNullList<ItemStack> items = NonNullList.withSize(2, ItemStack.EMPTY);
    private BlockPos connectedNodePos = null;
    public int connectedUsersCount;
    public int maxConnectedUsers;
    public int radius;

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

        connectedUsersCount = cachedConfig.connectedUsers.size();
        maxConnectedUsers = cachedConfig.maxConnections;
        radius = cachedConfig.radius;

        Map<WirelessUser, WorldData.WirelessNetworkData.UserConfig> userMap = new HashMap<>();
        for (BlockPos userPos : cachedConfig.connectedUsers.keySet()) {
            BlockEntity userBE = serverLevel.getBlockEntity(userPos);
            if (!(userBE instanceof WirelessUser user)) {
                handleUserDisconnect(serverLevel, userPos);
            } else {
                userMap.put(user, cachedConfig.connectedUsers.get(userPos));
            }
        }

        WirelessManager.balanceEnergy(this, new HashMap<>(userMap));
        setChanged();
        if (level != null && !level.isClientSide) {
            level.sendBlockUpdated(getBlockPos(), getBlockState(), getBlockState(), Block.UPDATE_ALL);
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
            if (level != null && !level.isClientSide) {
                level.sendBlockUpdated(getBlockPos(), getBlockState(), getBlockState(), Block.UPDATE_ALL);
            }
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
    public @Nullable BlockPos getConnectedNodePosition() {
        return connectedNodePos;
    }

    @Override
    public void setConnectedNodePosition(@Nullable BlockPos nodePos) {
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
        int energyStored = getEnergyStored();
        int energyToExtract = Math.min(maxExtract, energyStored);
        if (energyToExtract <= 0) {
            return 0;
        }
        if (!simulate) {
            setEnergyStorage(energyStored - energyToExtract);
        }
        return energyToExtract;
    }

    public void setEnergyStorage(int newEnergy) {
        int clamped = Math.max(0, Math.min(newEnergy, getMaxEnergyStorage()));
        if (clamped != this.energyStored) {
            this.energyStored = clamped;
            setChanged();
            if (level != null && !level.isClientSide) {
                level.sendBlockUpdated(getBlockPos(), getBlockState(), getBlockState(), 3);
            }
        }
    }

    @Override
    public int receiveEnergy(int maxReceive, boolean simulate) {
        return 0;
    }

    @Override
    protected void saveAdditional(@NotNull CompoundTag tag) {
        super.saveAdditional(tag);
        ContainerHelper.saveAllItems(tag, this.items);
        tag.putInt("energy_stored", energyStored);
        tag.putInt("connected_users_count", connectedUsersCount);
        tag.putInt("max_connected_users", maxConnectedUsers);
        tag.putInt("radius", radius);
    }

    @Override
    public void load(@NotNull CompoundTag tag) {
        super.load(tag);
        items = NonNullList.withSize(this.getContainerSize(), ItemStack.EMPTY);
        ContainerHelper.loadAllItems(tag, this.items);
        energyStored = tag.getInt("energy_stored");
        connectedUsersCount = tag.getInt("connected_users_count");
        maxConnectedUsers = tag.getInt("max_connected_users");
        radius = tag.getInt("radius");
        this.cachedConfig = null;
    }

    @Override
    public int getContainerSize() {
        return 2;
    }

    @Override
    public boolean isEmpty() {
        return items.stream().allMatch(ItemStack::isEmpty);
    }

    @Override
    public @NotNull ItemStack getItem(int slot) {
        return items.get(slot);
    }

    @Override
    public @NotNull ItemStack removeItem(int slot, int amount) {
        ItemStack itemstack = ContainerHelper.removeItem(items, slot, amount);
        if (!itemstack.isEmpty()) {
            this.setChanged();
        }

        return itemstack;
    }

    @Override
    public @NotNull ItemStack removeItemNoUpdate(int slot) {
        return ContainerHelper.takeItem(this.items, slot);
    }

    @Override
    public void setItem(int slot, @NotNull ItemStack stack) {
        this.items.set(slot, stack);
        if (stack.getCount() > this.getMaxStackSize()) {
            stack.setCount(this.getMaxStackSize());
        }
        this.setChanged();
        if (level != null && !level.isClientSide) {
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
        }
    }

    @Override
    public boolean stillValid(@NotNull Player player) {
        return Container.stillValidBlockEntity(this, player);
    }

    @Override
    public void clearContent() {
        items.clear();
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