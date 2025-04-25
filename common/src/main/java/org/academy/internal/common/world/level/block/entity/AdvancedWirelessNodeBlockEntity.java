package org.academy.internal.common.world.level.block.entity;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.academy.AcademyCraft;
import org.academy.api.common.wireless.WirelessNode;
import org.academy.api.common.wireless.WirelessUser;
import org.academy.internal.server.world.level.storage.WorldData;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;


public class AdvancedWirelessNodeBlockEntity extends BlockEntity implements WirelessNode {
    private int energyStored = 5000;
    private static final int MAX_ENERGY = 2_400_000;
    private static final int TRANSFER_RATE = 5;

    private WorldData.WirelessNetworkData.NodeConfig cachedConfig = null;

    public AdvancedWirelessNodeBlockEntity(BlockPos pos, BlockState blockState) {
        super(BlockEntityTypes.ADVANCED_WIRELESS_NODE, pos, blockState);
    }

    public static <T extends BlockEntity> void tick(Level level, BlockPos pos, T blockEntity) {
        if (level instanceof ServerLevel serverLevel && blockEntity instanceof AdvancedWirelessNodeBlockEntity nodeBE) {
            nodeBE.serverTick(serverLevel, pos);
        }
    }

    private void serverTick(ServerLevel serverLevel, BlockPos pos) {
        if (level == null) return;

        if (cachedConfig == null) {
            WorldData.WirelessNetworkData networkData = WorldData.WirelessNetworkData.get(serverLevel);
            cachedConfig = networkData.getNodeConfig(pos);
            if (cachedConfig == null && level != null && level.getGameTime() > 1) {
                AcademyCraft.LOGGER.warn("Wireless Node BE at {} ticking but not (yet?) registered in SavedData!", pos);
            }
            return;
        }

        int transferRate = getEnergyTransferRate();
        int remainingTransferBudget = transferRate;
        boolean changed = false;

        List<BlockPos> connectedUserPositions = List.copyOf(cachedConfig.connectedUsers);

        for (BlockPos userPos : connectedUserPositions) {
            if (energyStored >= getMaxEnergyStorage() || remainingTransferBudget <= 0) break;
            BlockEntity userBE = serverLevel.getBlockEntity(userPos);
            if (userBE instanceof WirelessUser user) {
                int space = getMaxEnergyStorage() - energyStored;
                int maxPull = Math.min(Math.min(space, remainingTransferBudget), transferRate);
                if (maxPull <= 0) continue;
                int extracted = this.extractFromUser(user, maxPull, false);
                if (extracted > 0) {
                    this.energyStored += extracted;
                    remainingTransferBudget -= extracted;
                    changed = true;
                }
            } else {
                handleUserDisconnect(serverLevel, userPos);
            }
        }

        if (energyStored > 0 && !connectedUserPositions.isEmpty() && remainingTransferBudget > 0) {
            List<BlockPos> usersToProvide = new ArrayList<>(connectedUserPositions);
            Collections.shuffle(usersToProvide);
            for (BlockPos userPos : usersToProvide) {
                if (energyStored <= 0 || remainingTransferBudget <= 0) break;
                BlockEntity userBE = serverLevel.getBlockEntity(userPos);
                if (userBE instanceof WirelessUser user) {
                    int maxPush = Math.min(Math.min(energyStored, remainingTransferBudget), transferRate);
                    if (maxPush <= 0) continue;
                    int accepted = this.insertIntoUser(user, maxPush, false);
                    if (accepted > 0) {
                        this.energyStored -= accepted;
                        remainingTransferBudget -= accepted;
                        changed = true;
                    }
                }
            }
        }

        if (changed) {
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
    public String getNodeName() {
        return cachedConfig != null ? cachedConfig.name : "Unregistered";
    }

    @Override
    public boolean checkPassword(String passwordAttempt) {
        return cachedConfig != null && cachedConfig.checkPassword(passwordAttempt);
    }

    @Override
    public int getRadius() {
        return cachedConfig != null ? cachedConfig.radius : 0;
    }

    @Override
    public List<BlockPos> getConnectedUserPositions() {
        return cachedConfig != null ? List.copyOf(cachedConfig.connectedUsers) : Collections.emptyList();
    }

    @Override
    public int getMaxConnections() {
        return cachedConfig != null ? cachedConfig.maxConnections : 0;
    }

    @Override
    public Level getOwningLevel() {
        return this.level;
    }

    @Override
    public BlockPos getPosition() {
        return this.worldPosition;
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
    public int getMaxEnergyStorage() {
        return MAX_ENERGY;
    }

    @Override
    public int getEnergyTransferRate() {
        return TRANSFER_RATE;
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