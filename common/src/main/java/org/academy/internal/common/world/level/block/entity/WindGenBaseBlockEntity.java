package org.academy.internal.common.world.level.block.entity;

import net.minecraft.core.BlockPos;
import net.minecraft.core.NonNullList;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.Container;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.entity.AnimationState;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.academy.api.common.wireless.WirelessUser;
import org.academy.internal.client.gui.world.WindGenWorldGUI;
import org.academy.internal.common.world.level.block.Blocks;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Objects;

public class WindGenBaseBlockEntity extends MultiBlockEntity implements Container, WirelessUser {
    public int ticks;
    public int energyStored;
    public final AnimationState activeState = new AnimationState();
    public WindGenWorldGUI windGenWorldGUI;
    public Completeness completeness = Completeness.BASE_ONLY;
    public NonNullList<ItemStack> items = NonNullList.withSize(1, ItemStack.EMPTY);
    public WindGenTopBlockEntity topBlockEntity;
    private static final String NBT_COMPLETENESS = "Completeness";
    private BlockPos connectedNodePos = null;

    public WindGenBaseBlockEntity(BlockPos pos, BlockState blockState) {
        super(BlockEntityTypes.WIND_GEN_BASE, pos, blockState);
        activeState.start(ticks);
        if (isMain()) {
            windGenWorldGUI = new WindGenWorldGUI();
            windGenWorldGUI.onInit();
        }
    }

    public void tick() {
        ticks++;
        if (this.level != null && !this.level.isClientSide) {
            updateState();
            setChanged();
            level.sendBlockUpdated(getBlockPos(), getBlockState(), getBlockState(), 3);
        }
        if (completeness == Completeness.COMPLETE && topBlockEntity != null && topBlockEntity.hasFan) {
            energyStored += 10;
        }
    }

    private void updateState() {
        if (level != null) {
            int pillars = 0;
            boolean hasTop = false;
            BlockPos p = getBlockPos();
            Completeness calculatedCompleteness = Completeness.BASE_ONLY;

            for (int y = p.getY() + 2; y < level.getMaxBuildHeight(); ++y) {
                BlockPos pos = new BlockPos(p.getX(), y, p.getZ());
                BlockState currentState = level.getBlockState(pos);
                if (currentState.isAir()) {
                    y = level.getMaxBuildHeight();
                }
                Block block = currentState.getBlock();

                if (block == Blocks.WIND_GEN_PILLAR_BLOCK) {
                    ++pillars;
                    if (pillars > 64) {
                        break;
                    }
                } else if (block == Blocks.WIND_GEN_TOP_BLOCK) {
                    if (currentState.hasBlockEntity()) {
                        if (level.getBlockEntity(pos) instanceof WindGenTopBlockEntity windGenTopBlockEntity) {
                            topBlockEntity = windGenTopBlockEntity;
                            hasTop = true;
                        }
                    }
                    break;
                } else {
                    break;
                }
            }

            if (hasTop) {
                calculatedCompleteness = (pillars >= 20)
                        ? Completeness.COMPLETE
                        : Completeness.NO_TOP;
            } else {
                if (pillars >= 20) {
                    calculatedCompleteness = Completeness.NO_TOP;
                }
            }

            this.completeness = calculatedCompleteness;
        }
    }

    @Override
    protected void saveAdditional(@NotNull CompoundTag tag) {
        super.saveAdditional(tag);
        tag.putString(NBT_COMPLETENESS, this.completeness.name());
        ContainerHelper.saveAllItems(tag, this.items);
        if (isMain()) {
            tag.putInt("energy_stored", energyStored);
        }
    }

    @Override
    public void load(@NotNull CompoundTag tag) {
        super.load(tag);
        if (tag.contains(NBT_COMPLETENESS, CompoundTag.TAG_STRING)) {
            try {
                this.completeness = Completeness.valueOf(tag.getString(NBT_COMPLETENESS));
            } catch (IllegalArgumentException ignored) {
            }
        } else {
            this.completeness = Completeness.BASE_ONLY;
        }
        items = NonNullList.withSize(this.getContainerSize(), ItemStack.EMPTY);
        ContainerHelper.loadAllItems(tag, this.items);
        if (isMain()) {
            energyStored = tag.getInt("energy_stored");
        }
    }

    @Override
    public int getContainerSize() {
        return 1;
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
    public Level getOwningLevel() {
        return level;
    }

    @Override
    public BlockPos getPosition() {
        return getBlockPos();
    }

    @Override
    public @Nullable BlockPos getConnectedNodePosition() {
        if (!isMain() && level != null && mainPos != null) {
            BlockEntity mainBE = getMain();
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

    @Override
    public int receiveEnergy(int maxReceive, boolean simulate) {
        return 0;
    }

    @Override
    public int getEnergyStored() {
        return energyStored;
    }

    @Override
    public int getMaxEnergyStorage() {
        return 4800_000;
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

    public enum Completeness {
        BASE_ONLY, NO_TOP, COMPLETE, COMPLETE_NOT_WORKING
    }

    @Override
    public @Nullable Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }
}