package org.academy.internal.common.world.level.block.entity;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.NonNullList;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.entity.AnimationState;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import org.academy.AcademyCraft;
import org.academy.api.common.wireless.WirelessNode;
import org.academy.api.common.wireless.WirelessUser;
import org.academy.api.server.wireless.WirelessManager;
import org.academy.internal.server.world.level.storage.WirelessNetworkData;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;

import java.util.*;

public final class WirelessNodeBlockEntity extends BlockEntity implements WirelessNode, WirelessUser, Container {
    private static final Logger LOGGER = AcademyCraft.getLogger();
    
    private static final int MAX_ENERGY = 2_400_000;
    private static final int TRANSFER_RATE = 20000;

    private int energyStored = 5000;
    public WirelessNetworkData.@Nullable NodeConfig cachedConfig = null;
    public NonNullList<ItemStack> items = NonNullList.withSize(2, ItemStack.EMPTY);
    @Nullable
    private BlockPos connectedNodePos = null;
    public int connectedUsersCount;
    public int maxConnectedUsers;
    public int radius;
    public int ticks;
    public final AnimationState coreState = new AnimationState();

    public WirelessNodeBlockEntity(BlockPos pos, BlockState blockState) {
        super(BlockEntityTypes.WIRELESS_NODE.get(), pos, blockState);
        coreState.start(ticks);
    }

    public void serverTick(ServerLevel serverLevel, BlockPos pos) {
        if (cachedConfig == null) {
            var networkData = WirelessNetworkData.get(serverLevel);
            cachedConfig = networkData.getNodeConfig(pos);
            if (cachedConfig == null && level != null && level.getGameTime() > 1) {
                LOGGER.warn("Wireless Node BE at {} ticking but not (yet?) registered in SavedData!", pos);
            }
            return;
        }

        connectedUsersCount = cachedConfig.connectedUsers.size();
        maxConnectedUsers = cachedConfig.maxConnections;
        radius = cachedConfig.radius;

        Map<WirelessUser, WirelessNetworkData.UserConfig> userMap = new HashMap<>(connectedUsersCount);
        List<BlockPos> toRemove = new ArrayList<>();

        for (var entry : cachedConfig.connectedUsers.entrySet()) {
            var userPos = entry.getKey();
            var userBE = serverLevel.getBlockEntity(userPos);
            if (!(userBE instanceof WirelessUser user)) {
                toRemove.add(userPos);
            } else {
                userMap.put(user, entry.getValue());
            }
        }

        for (var blockPos : toRemove) {
            handleUserDisconnect(serverLevel, blockPos);
            cachedConfig.connectedUsers.remove(blockPos);
        }

        WirelessManager.balanceEnergy(this, new HashMap<>(userMap));

        setChanged();
        serverLevel.sendBlockUpdated(getBlockPos(), getBlockState(), getBlockState(), Block.UPDATE_ALL);
    }

    private void handleUserDisconnect(ServerLevel level, BlockPos userPos) {
        LOGGER.warn("Node at {} detected invalid or missing user at {}. Requesting disconnect from SavedData.", worldPosition, userPos);
        var networkData = WirelessNetworkData.get(level);
        var removed = networkData.disconnectUserFromNode(worldPosition, userPos);
        if (removed) {
            cachedConfig = networkData.getNodeConfig(worldPosition);
            LOGGER.debug("Successfully disconnected user {} from node {} in SavedData.", userPos, worldPosition);
        } else {
            LOGGER.warn("Failed request to disconnect user {} from node {} in SavedData.", userPos, worldPosition);
        }
        var userBE = level.getBlockEntity(userPos);
        if (userBE instanceof WirelessUser user) {
            try {
                user.setConnectedNodePosition(null);
            } catch (Exception e) {
                LOGGER.error("Error notifying potentially invalid user BE at {} about disconnect: {}", userPos, e.getMessage());
            }
        }
    }

    @Override
    public int getEnergyStored() {
        return energyStored;
    }

    @Override
    public void setEnergyStored(int energy) {
        double oldEnergy = energyStored;
        energyStored = Math.max(0, Math.min(energy, getMaxEnergyStorage()));
        if (oldEnergy != energyStored) {
            setChanged();
            if (level != null && !level.isClientSide()) {
                level.sendBlockUpdated(getBlockPos(), getBlockState(), getBlockState(), Block.UPDATE_ALL);
            }
        }
    }

    @Override
    public int extractFromUser(WirelessUser user, int maxAmount, boolean simulate) {
        try {
            return user.extractEnergy(maxAmount, simulate);
        } catch (Exception e) {
            LOGGER.error("Error extracting energyCost from user at {}: {}", user, e.getMessage());
            return 0;
        }
    }

    @Override
    public int insertIntoUser(WirelessUser user, int maxAmount, boolean simulate) {
        try {
            return user.receiveEnergy(maxAmount, simulate);
        } catch (Exception e) {
            LOGGER.error("Error inserting energyCost into user at {}: {}", user, e.getMessage());
            return 0;
        }
    }

    @Override
    public @Nullable BlockPos getConnectedNodePosition() {
        return connectedNodePos;
    }

    @Override
    public void setConnectedNodePosition(@Nullable BlockPos nodePos) {
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

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        ContainerHelper.saveAllItems(output, items);
        output.putInt("energy_stored", energyStored);
        output.putInt("connected_users_count", connectedUsersCount);
        output.putInt("max_connected_users", maxConnectedUsers);
        output.putInt("radius", radius);
    }

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        items = NonNullList.withSize(getContainerSize(), ItemStack.EMPTY);
        ContainerHelper.loadAllItems(input, items);
        energyStored = input.getIntOr("energy_stored", 0);
        connectedUsersCount = input.getIntOr("connected_users_count", 0);
        maxConnectedUsers = input.getIntOr("max_connected_users", 0);
        radius = input.getIntOr("radius", 0);
        cachedConfig = null;
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
    public ItemStack getItem(int slot) {
        return items.get(slot);
    }

    @Override
    public ItemStack removeItem(int slot, int amount) {
        var itemstack = ContainerHelper.removeItem(items, slot, amount);
        if (!itemstack.isEmpty()) setChanged();
        return itemstack;
    }

    @Override
    public ItemStack removeItemNoUpdate(int slot) {
        return ContainerHelper.takeItem(items, slot);
    }

    @Override
    public void setItem(int slot, ItemStack stack) {
        items.set(slot, stack);
        if (stack.getCount() > getMaxStackSize()) stack.setCount(getMaxStackSize());
        setChanged();
        if (level != null && !level.isClientSide()) {
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
        }
    }

    @Override
    public boolean stillValid(Player player) {
        return Container.stillValidBlockEntity(this, player);
    }

    @Override
    public void clearContent() {
        items.clear();
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        return saveWithoutMetadata(registries);
    }

    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    @Override
    public int getMaxEnergyStorage() {
        return MAX_ENERGY;
    }

    @Override
    public int getEnergyTransferRate() {
        return TRANSFER_RATE;
    }
}