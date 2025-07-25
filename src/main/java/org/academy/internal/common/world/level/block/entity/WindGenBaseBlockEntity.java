package org.academy.internal.common.world.level.block.entity;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.NonNullList;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.Container;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.entity.AnimationState;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.academy.api.common.wireless.WirelessUser;
import org.academy.internal.client.gui.world.WindGenWorldGUI;
import org.academy.internal.client.model.WindGenBaseModel;
import org.academy.internal.common.world.level.block.Blocks;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Objects;

public class WindGenBaseBlockEntity extends MultiBlockEntity implements Container, WirelessUser {
    public int ticks;
    public int energyStored;
    public final AnimationState setupState = new AnimationState();
    public final AnimationState shutdownState = new AnimationState();
    public WindGenWorldGUI windGenWorldGUI;
    public Completeness completeness = Completeness.BASE_ONLY;
    public NonNullList<ItemStack> items = NonNullList.withSize(1, ItemStack.EMPTY);
    public WindGenTopBlockEntity topBlockEntity;
    private static final String NBT_COMPLETENESS = "Completeness";
    private BlockPos connectedNodePos = null;
    public int altitude;
    private boolean playerNearby = false;
    public boolean isDisplayActive = false;

    public WindGenBaseBlockEntity(BlockPos pos, BlockState blockState) {
        super(BlockEntityTypes.WIND_GEN_BASE.get(), pos, blockState);
        if (isMain()) {
            windGenWorldGUI = new WindGenWorldGUI();
            windGenWorldGUI.onInit();
            setupState.start(ticks);
        }
    }

    public void tick() {
        ticks++;
        if (this.level != null) {
            if (this.level.isClientSide) {
                clientTick();
            } else {
                serverTick();
            }
        }

        if (completeness == Completeness.COMPLETE && topBlockEntity != null && topBlockEntity.hasFan) {
            energyStored += 10;
        }
    }

    private void serverTick() {
        updateState();

        if (isMain()) {
            List<Player> nearbyPlayers = level.getEntitiesOfClass(Player.class, new AABB(worldPosition).inflate(10.0));
            boolean wasNearby = this.playerNearby;
            this.playerNearby = !nearbyPlayers.isEmpty();

            if (wasNearby != this.playerNearby) {
                setChanged();
                level.sendBlockUpdated(getBlockPos(), getBlockState(), getBlockState(), Block.UPDATE_ALL_IMMEDIATE);
            }
        }
    }

    private void clientTick() {
        if (isMain()) {
            if (this.setupState.isStarted() && this.setupState.getAccumulatedTime() >= WindGenBaseModel.setup.lengthInSeconds() * 1000L) {
                this.isDisplayActive = true;
                this.setupState.stop();
            }

            if (this.shutdownState.isStarted()) {
                this.isDisplayActive = false;
            }
        }
    }

    private void handleStateChangeOnClient(boolean wasNearby, boolean isNearby) {
        if (wasNearby == isNearby) return;

        setupState.stop();
        shutdownState.stop();

        if (isNearby) {
            setupState.start(this.ticks);
        } else {
            shutdownState.start(this.ticks);
            isDisplayActive = false;
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

                if (block == Blocks.WIND_GEN_PILLAR.get()) {
                    ++pillars;
                    if (pillars > 64) {
                        break;
                    }
                } else if (block == Blocks.WIND_GEN_TOP.get()) {
                    if (currentState.hasBlockEntity()) {
                        if (level.getBlockEntity(pos) instanceof WindGenTopBlockEntity windGenTopBlockEntity && windGenTopBlockEntity.isMain()) {
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
            if (topBlockEntity != null) {
                altitude = topBlockEntity.getBlockPos().getY();
            }
        }
    }

    @Override
    protected void loadAdditional(@NotNull CompoundTag tag, HolderLookup.@NotNull Provider registries) {
        super.loadAdditional(tag, registries);
        items = NonNullList.withSize(getContainerSize(), ItemStack.EMPTY);
        ContainerHelper.loadAllItems(tag, items, registries);
        if (tag.contains(NBT_COMPLETENESS, CompoundTag.TAG_STRING)) {
            try {
                completeness = Completeness.valueOf(tag.getString(NBT_COMPLETENESS));
            } catch (IllegalArgumentException ignored) {
            }
        } else {
            completeness = Completeness.BASE_ONLY;
        }
        if (isMain()) {
            energyStored = tag.getInt("energy_stored");
            if (tag.contains("connected_node_pos", CompoundTag.TAG_LONG)) {
                connectedNodePos = BlockPos.of(tag.getLong("connected_node_pos"));
            }
            altitude = tag.getInt("altitude");

            boolean oldPlayerNearby = this.playerNearby;
            this.playerNearby = tag.getBoolean("playerNearby");

            if (level != null && level.isClientSide) {
                handleStateChangeOnClient(oldPlayerNearby, this.playerNearby);
            }
        }
    }

    @Override
    protected void saveAdditional(@NotNull CompoundTag tag, HolderLookup.@NotNull Provider registries) {
        super.saveAdditional(tag, registries);
        tag.putString(NBT_COMPLETENESS, completeness.name());
        ContainerHelper.saveAllItems(tag, items, registries);
        if (isMain()) {
            tag.putInt("energy_stored", energyStored);
            if (connectedNodePos != null) {
                tag.putLong("connected_node_pos", connectedNodePos.asLong());
            }
            tag.putInt("altitude", altitude);
            tag.putBoolean("playerNearby", this.playerNearby);
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

    @SuppressWarnings("unused")
    public AABB getRenderBoundingBox() {
        Vec3 pos = this.getBlockPos().getCenter();
        double radius = 2.0;
        return new AABB(pos.x - radius, pos.y - radius, pos.z - radius, pos.x + radius, pos.y + radius, pos.z + radius);
    }
}