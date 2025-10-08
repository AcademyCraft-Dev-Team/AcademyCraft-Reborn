package org.academy.internal.common.world.level.block.entity;

import net.minecraft.core.BlockPos;
import net.minecraft.core.NonNullList;
import net.minecraft.world.Container;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.Containers;
import net.minecraft.world.entity.AnimationState;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.AABB;
import org.academy.api.common.wireless.WirelessUser;
import org.academy.internal.client.definitions.WindGenBaseAnimation;
import org.academy.internal.client.gui.world.WindGenWorldGUI;
import org.academy.internal.common.world.level.block.Blocks;
import org.jetbrains.annotations.Nullable;

import java.util.Objects;

public final class WindGenBaseBlockEntity extends MultiBlockEntity implements Container, WirelessUser {
    public int ticks;
    public int energyStored;
    public final AnimationState setupState = new AnimationState();
    public final AnimationState shutdownState = new AnimationState();
    @Nullable
    public WindGenWorldGUI windGenWorldGUI;
    public Completeness completeness = Completeness.BASE_ONLY;
    public NonNullList<ItemStack> items = NonNullList.withSize(1, ItemStack.EMPTY);
    @Nullable
    public WindGenTopBlockEntity topBlockEntity;
    private static final String NBT_COMPLETENESS = "Completeness";
    @Nullable
    private BlockPos connectedNodePos = null;
    public int altitude;
    private boolean playerNearby = false;
    public boolean isDisplayActive = false;

    public WindGenBaseBlockEntity(BlockPos pos, BlockState blockState) {
        super(BlockEntityTypes.WIND_GEN_BASE.get(), pos, blockState);
        if (isMain()) {
            windGenWorldGUI = new WindGenWorldGUI();
            windGenWorldGUI.onInit();
            shutdownState.start(ticks);
        }
    }

    public void tick() {
        ticks++;
        if (this.level != null) {
            if (this.level.isClientSide()) {
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
    }

    private void clientTick() {
        if (isMain() && level != null) {
            var wasNearby = this.playerNearby;
            var nearbyPlayers = level.getEntitiesOfClass(Player.class, new AABB(worldPosition).inflate(10.0));
            this.playerNearby = !nearbyPlayers.isEmpty() && energyStored > 0;

            if (wasNearby != this.playerNearby) {
                handleStateChangeOnClient(wasNearby, this.playerNearby);
            }
        }
    }

    private void handleStateChangeOnClient(boolean wasNearby, boolean isNearby) {
        if (wasNearby == isNearby) return;

        var currentAnimationState = wasNearby ? setupState : shutdownState;
        var targetAnimationState = isNearby ? setupState : shutdownState;
        var targetAnimationDefinition = isNearby ? WindGenBaseAnimation.SETUP : WindGenBaseAnimation.SHUT;

        if (!isNearby) {
            isDisplayActive = false;
        }

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

    private void updateState() {
        if (level != null) {
            int pillars = 0;
            boolean hasTop = false;
            var p = getBlockPos();
            var calculatedCompleteness = Completeness.BASE_ONLY;

            for (var y = p.getY() + 2; y < level.getMaxY(); ++y) {
                var pos = new BlockPos(p.getX(), y, p.getZ());
                var currentState = level.getBlockState(pos);
                if (currentState.isAir()) {
                    y = level.getMaxY();
                }
                var block = currentState.getBlock();

                if (block == Blocks.WIND_GEN_PILLAR.get()) {
                    ++pillars;
                    if (pillars > 64) {
                        break;
                    }
                } else if (block == Blocks.WIND_GEN_TOP.get()) {
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
            if (topBlockEntity != null) {
                altitude = topBlockEntity.getBlockPos().getY();
            }
        }
    }

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        output.putString(NBT_COMPLETENESS, completeness.name());
        ContainerHelper.saveAllItems(output, items);
        if (isMain()) {
            output.putInt("energy_stored", energyStored);
            if (connectedNodePos != null) {
                output.putLong("connected_node_pos", connectedNodePos.asLong());
            }
            output.putInt("altitude", altitude);
        }
    }

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        items = NonNullList.withSize(getContainerSize(), ItemStack.EMPTY);
        ContainerHelper.loadAllItems(input, items);
        completeness = Completeness.valueOf(input.getString(NBT_COMPLETENESS).orElse(Completeness.BASE_ONLY.name()));

        if (isMain()) {
            energyStored = input.getIntOr("energy_stored", 0);
            input.getLong("connected_node_pos").ifPresent(nodePos -> connectedNodePos = BlockPos.of(nodePos));
            altitude = input.getIntOr("altitude", 0);
        }
    }

    @Override
    public void setRemoved() {
        super.setRemoved();
        var level = getLevel();
        if (level != null) {
            Containers.dropContents(level, getBlockPos(), this);
            level.updateNeighbourForOutputSignal(getBlockPos(), getBlockState().getBlock());
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
    public ItemStack getItem(int slot) {
        return items.get(slot);
    }

    @Override
    public ItemStack removeItem(int slot, int amount) {
        var itemstack = ContainerHelper.removeItem(items, slot, amount);
        if (!itemstack.isEmpty()) {
            this.setChanged();
        }

        return itemstack;
    }

    @Override
    public ItemStack removeItemNoUpdate(int slot) {
        return ContainerHelper.takeItem(items, slot);
    }

    @Override
    public void setItem(int slot, ItemStack stack) {
        items.set(slot, stack);
        if (stack.getCount() > getMaxStackSize()) {
            stack.setCount(getMaxStackSize());
        }
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
    public @Nullable BlockPos getConnectedNodePosition() {
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
        if (!Objects.equals(this.connectedNodePos, nodePos)) {
            this.connectedNodePos = nodePos;
            setChanged();
            if (level != null && !level.isClientSide()) {
                level.sendBlockUpdated(getBlockPos(), getBlockState(), getBlockState(), Block.UPDATE_ALL);
            }
        }
    }

    @Override
    public int extractEnergy(int maxExtract, boolean simulate) {
        var energyStored = getEnergyStored();
        var energyToExtract = Math.min(maxExtract, energyStored);
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

    public void setEnergyStorage(int energyStorage) {
        var clamped = Math.max(0, Math.min(energyStorage, getMaxEnergyStorage()));
        if (clamped != this.energyStored) {
            energyStored = clamped;
            setChanged();
            if (level != null && !level.isClientSide()) {
                level.sendBlockUpdated(getBlockPos(), getBlockState(), getBlockState(), 3);
            }
        }
    }

    public enum Completeness {
        BASE_ONLY, NO_TOP, COMPLETE, COMPLETE_NOT_WORKING
    }
}