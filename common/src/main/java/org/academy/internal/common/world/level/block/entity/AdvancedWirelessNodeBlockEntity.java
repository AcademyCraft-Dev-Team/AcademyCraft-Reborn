package org.academy.internal.common.world.level.block.entity;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import org.academy.api.common.wireless.WirelessManager;
import org.academy.api.common.wireless.WirelessNode;
import org.academy.api.common.wireless.WirelessUser;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

public class AdvancedWirelessNodeBlockEntity extends BlockEntity implements WirelessNode {
    public final List<WirelessUser> wirelessUsers = new ArrayList<>();
    public String nodeName = "Unnamed";
    public String nodePassword = "";
    public int energyStored;

    public AdvancedWirelessNodeBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState blockState) {
        super(type, pos, blockState);
        WirelessManager.WIRELESS_NODES.put(pos, this);
    }

    @Override
    public @NotNull CompoundTag getUpdateTag() {
        CompoundTag compoundTag = super.getUpdateTag();
        saveAdditional(compoundTag);
        return compoundTag;
    }

    @Override
    public ClientboundBlockEntityDataPacket getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    @Override
    protected void saveAdditional(@NotNull CompoundTag tag) {
        super.saveAdditional(tag);
        tag.putString("nodeName", nodeName);
        tag.putInt("energyStored", energyStored);
    }

    @Override
    public void load(@NotNull CompoundTag tag) {
        super.load(tag);
        nodeName = tag.getString("nodeName");
        energyStored = tag.getInt("energyStored");
    }

    @Override
    public String getNodeName() {
        return nodeName;
    }

    @Override
    public void setNodeName(String nodeName) {
        this.nodeName = nodeName;
        if (level != null) {
            level.sendBlockUpdated(getBlockPos(), getBlockState(), getBlockState(), 3);
        }
    }

    @Override
    public void setNodePassword(String nodePassword) {
        this.nodePassword = nodePassword;
    }

    @Override
    public String getNodePassword() {
        return nodePassword;
    }

    @Override
    public int getRadius() {
        return 32;
    }

    @Override
    public int getEnergyStored() {
        return energyStored;
    }

    @Override
    public void setEnergyStored(int energyStored) {
        this.energyStored = energyStored;
        if (level != null) {
            level.sendBlockUpdated(getBlockPos(), getBlockState(), getBlockState(), 3);
        }
    }

    @Override
    public int getMaxEnergyStorage() {
        return 2400000;
    }

    @Override
    public int getTranslateSpeed() {
        return 64;
    }

    @Override
    public List<WirelessUser> getWirelessNodes() {
        return wirelessUsers;
    }
}