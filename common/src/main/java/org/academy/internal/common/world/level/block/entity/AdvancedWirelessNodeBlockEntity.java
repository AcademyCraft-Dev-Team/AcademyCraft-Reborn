package org.academy.internal.common.world.level.block.entity;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
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
    private double energyStored = 0;
    private static final double MAX_ENERGY = 2_400_000.0;
    private static final double TRANSFER_RATE = 5000.0;

    private WorldData.WirelessNetworkData.NodeConfig cachedConfig = null;

    public AdvancedWirelessNodeBlockEntity(BlockPos pos, BlockState blockState) {
        super(BlockEntityTypes.ADVANCED_WIRELESS_NODE_BLOCK_ENTITY_BLOCK_ENTITY_TYPE, pos, blockState);
    }

    public static <T extends BlockEntity> void tick(Level level, BlockPos pos, BlockState state, T blockEntity) {
        if (level instanceof ServerLevel serverLevel && blockEntity instanceof AdvancedWirelessNodeBlockEntity nodeBE) {
            nodeBE.serverTick(serverLevel, pos, state);
        }
    }

    private void serverTick(ServerLevel serverLevel, BlockPos pos, BlockState state) {
        if (level == null) return;

        if (level.getGameTime() % 60 == 0 || cachedConfig == null) {
            WorldData.WirelessNetworkData networkData = WorldData.WirelessNetworkData.get(serverLevel);
            cachedConfig = networkData.getNodeConfig(pos);
            // --- Check level null again before logging ---
            if (cachedConfig == null && level != null && level.getGameTime() > 1) {
                AcademyCraft.LOGGER.warn("Wireless Node BE at {} ticking but not (yet?) registered in SavedData!", pos);
            }
        }

        if (cachedConfig == null) return;

        double transferRate = getEnergyTransferRate();
        double energyReceivedThisTick = 0;
        double energySentThisTick = 0;
        boolean changed = false;

        List<BlockPos> connectedUserPositions = List.copyOf(cachedConfig.connectedUsers);

        for (BlockPos userPos : connectedUserPositions) {
            if (energyStored >= getMaxEnergyStorage()) break;
            BlockEntity userBE = serverLevel.getBlockEntity(userPos);
            if (userBE instanceof WirelessUser user) {
                double space = getMaxEnergyStorage() - energyStored;
                double maxPull = Math.min(transferRate, space);
                if (maxPull <= 0) continue;
                double extracted = this.extractFromUser(userPos, maxPull, false);
                if (extracted > 0) {
                    this.energyStored += extracted;
                    energyReceivedThisTick += extracted;
                    changed = true;
                }
            } else {
                handleUserDisconnect(serverLevel, userPos);
            }
        }

        if (energyStored > 0 && !connectedUserPositions.isEmpty()) {
            List<BlockPos> usersToProvide = new ArrayList<>(connectedUserPositions);
            Collections.shuffle(usersToProvide);
            for (BlockPos userPos : usersToProvide) {
                if (energyStored <= 0) break;
                BlockEntity userBE = serverLevel.getBlockEntity(userPos);
                if (userBE instanceof WirelessUser user) {
                    double maxPush = Math.min(transferRate, energyStored);
                    if (maxPush <= 0) continue;
                    double accepted = this.insertIntoUser(userPos, maxPush, false);
                    if (accepted > 0) {
                        this.energyStored -= accepted;
                        energySentThisTick += accepted;
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
    public double getEnergyStored() {
        return this.energyStored;
    }

    @Override
    public void setEnergyStored(double energy) {
        double oldEnergy = this.energyStored;
        this.energyStored = Math.max(0.0, Math.min(energy, getMaxEnergyStorage()));
        if (oldEnergy != this.energyStored) {
            setChanged();
        }
    }

    @Override
    public double getMaxEnergyStorage() {
        return MAX_ENERGY;
    }

    @Override
    public double getEnergyTransferRate() {
        return TRANSFER_RATE;
    }

    @Override
    public double extractFromUser(BlockPos userPos, double maxAmount, boolean simulate) {
        if (level == null || level.isClientSide || maxAmount <= 0) return 0;
        BlockEntity be = level.getBlockEntity(userPos);
        if (be instanceof WirelessUser user) {
            try {
                return user.extractEnergy(maxAmount, simulate);
            } catch (Exception e) {
                AcademyCraft.LOGGER.error("Error extracting energy from user at {}: {}", userPos, e.getMessage());
                return 0;
            }
        }
        return 0;
    }

    @Override
    public double insertIntoUser(BlockPos userPos, double maxAmount, boolean simulate) {
        if (level == null || level.isClientSide || maxAmount <= 0) return 0;
        BlockEntity be = level.getBlockEntity(userPos);
        if (be instanceof WirelessUser user) {
            try {
                return user.receiveEnergy(maxAmount, simulate);
            } catch (Exception e) {
                AcademyCraft.LOGGER.error("Error inserting energy into user at {}: {}", userPos, e.getMessage());
                return 0;
            }
        }
        return 0;
    }

    @Override
    protected void saveAdditional(@NotNull CompoundTag tag) {
        super.saveAdditional(tag);
        tag.putDouble("EnergyStored", this.energyStored);
    }

    @Override
    public void load(@NotNull CompoundTag tag) {
        super.load(tag);
        if (tag.contains("EnergyStored", Tag.TAG_DOUBLE)) {
            this.energyStored = tag.getDouble("EnergyStored");
        } else if (tag.contains("EnergyStored", Tag.TAG_INT)) {
            this.energyStored = tag.getInt("EnergyStored");
            AcademyCraft.LOGGER.debug("Loaded legacy int energy for node at {}, converting to double.", worldPosition);
        } else {
            this.energyStored = 0;
        }
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