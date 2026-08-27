package org.academy.internal.common.world.level.block.entity;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.NonNullList;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
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

import static net.minecraft.world.level.block.Block.UPDATE_CLIENTS;
import static net.minecraft.world.level.block.Block.UPDATE_NEIGHBORS;

public final class WirelessNodeBlockEntity extends BlockEntity implements WirelessNode, WirelessUser, Container {
    private static final Logger LOGGER = AcademyCraft.getLogger();

    private static final int MAX_ENERGY = 2_400_000;
    private static final int TRANSFER_RATE = 20000;
    public final AnimationState coreState = new AnimationState();
    public WirelessNetworkData.@Nullable NodeConfig cachedConfig = null;
    public NonNullList<ItemStack> items = NonNullList.withSize(2, ItemStack.EMPTY);
    public int connectedUsersCount;
    public int maxConnectedUsers;
    public int radius;
    public int ticks;
    private int energyStored = 5000;
    @Nullable
    private BlockPos connectedNodePos = null;

    public WirelessNodeBlockEntity(BlockPos pos, BlockState blockState) {
        super(BlockEntityTypes.WIRELESS_NODE.get(), pos, blockState);
        coreState.start(ticks);
    }

    public void serverTick(ServerLevel serverLevel, BlockPos pos) {
        var networkData = WirelessNetworkData.get(serverLevel);
        if (cachedConfig == null) {
            cachedConfig = networkData.getNodeConfig(pos);
            if (cachedConfig == null && level != null && level.getGameTime() > 1) {
                LOGGER.warn("Wireless Node BE at {} ticking but not (yet?) registered in SavedData!", pos);
            }
            if (cachedConfig == null) return;
        }

        Map<WirelessUser, WirelessNetworkData.UserConfig> userMap = new LinkedHashMap<>();
        List<BlockPos> toRemove = new ArrayList<>();

        var connectedUsers = new ArrayList<>(cachedConfig.connectedUsers.entrySet());
        connectedUsers.sort(Comparator.comparingLong(entry -> entry.getKey().asLong()));
        for (var entry : connectedUsers) {
            var userPos = entry.getKey();
            if (!serverLevel.isLoaded(userPos)) {
                continue;
            }
            var userBE = serverLevel.getBlockEntity(userPos);
            if (userPos.equals(pos) || !(userBE instanceof WirelessUser user)
                    || !Objects.equals(user.getConnectedNodePosition(), pos)
                    || userPos.distSqr(pos) > (double) cachedConfig.radius * cachedConfig.radius) {
                toRemove.add(userPos);
            } else {
                userMap.put(user, entry.getValue());
            }
        }

        for (var blockPos : toRemove) {
            handleUserDisconnect(serverLevel, blockPos);
        }

        var newConnectedUsersCount = cachedConfig.connectedUsers.size();
        var networkInfoChanged = connectedUsersCount != newConnectedUsersCount
                || maxConnectedUsers != cachedConfig.maxConnections
                || radius != cachedConfig.radius;
        connectedUsersCount = newConnectedUsersCount;
        maxConnectedUsers = cachedConfig.maxConnections;
        radius = cachedConfig.radius;

        WirelessManager.balanceEnergy(this, userMap);

        if (networkInfoChanged) {
            setChanged();
            serverLevel.sendBlockUpdated(getBlockPos(), getBlockState(), getBlockState(), Block.UPDATE_ALL);
        }
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
        if (userBE instanceof WirelessUser user
                && Objects.equals(user.getConnectedNodePosition(), worldPosition)) {
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
        var oldEnergy = energyStored;
        energyStored = Mth.clamp(energy, 0, getMaxEnergyStorage());
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
    public boolean suppliesWirelessEnergy() {
        return false;
    }

    @Override
    public boolean acceptsWirelessEnergy() {
        return true;
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
        if (connectedNodePos != null) {
            output.putLong("connected_node_pos", connectedNodePos.asLong());
        }
    }

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        items = NonNullList.withSize(getContainerSize(), ItemStack.EMPTY);
        ContainerHelper.loadAllItems(input, items);
        energyStored = Mth.clamp(input.getIntOr("energy_stored", 0), 0, getMaxEnergyStorage());
        connectedUsersCount = Math.max(0, input.getIntOr("connected_users_count", 0));
        maxConnectedUsers = Math.max(0, input.getIntOr("max_connected_users", 0));
        radius = Math.max(0, input.getIntOr("radius", 0));
        connectedNodePos = null;
        input.getLong("connected_node_pos").ifPresent(nodePos -> connectedNodePos = BlockPos.of(nodePos));
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
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), UPDATE_NEIGHBORS | UPDATE_CLIENTS);
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
