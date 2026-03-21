package org.academy.internal.common.world.level.block.entity;

import net.minecraft.core.BlockPos;
import net.minecraft.core.NonNullList;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.Containers;
import net.minecraft.world.entity.AnimationState;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.AABB;
import org.academy.api.common.wireless.WirelessUser;
import org.academy.internal.client.definitions.WindGenBaseAnimation;
import org.academy.internal.client.gui.world.WindGenWorldGui;
import org.academy.internal.common.world.level.block.Blocks;
import org.jspecify.annotations.Nullable;

import java.util.Objects;

public final class WindGenBaseBlockEntity extends MultiBlockEntity implements Container, WirelessUser {
    public int ticks;
    public int energyStored;
    public final AnimationState setupState = new AnimationState();
    public final AnimationState shutdownState = new AnimationState();
    @Nullable
    public WindGenWorldGui windGenWorldGUI;
    public Completeness completeness = Completeness.BASE_ONLY;
    private NonNullList<ItemStack> items = NonNullList.withSize(1, ItemStack.EMPTY);
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
            windGenWorldGUI = new WindGenWorldGui();
            windGenWorldGUI.onInit();
            shutdownState.start(ticks);
        }
    }

    public static void tick(Level level, BlockPos pos, BlockState state, WindGenBaseBlockEntity blockEntity) {
        blockEntity.ticks++;

        if (level.isClientSide()) {
            tickClient(level, pos, state, blockEntity);
        }
        if (level instanceof ServerLevel serverLevel) {
            tickServer(serverLevel, pos, state, blockEntity);
        }
    }

    public static void tickClient(Level level, BlockPos pos, BlockState state, WindGenBaseBlockEntity blockEntity) {
        blockEntity.clientTick();
    }

    public static void tickServer(ServerLevel level, BlockPos pos, BlockState state, WindGenBaseBlockEntity blockEntity) {
        blockEntity.serverTick();
    }

    private void serverTick() {
        updateState();
        if (level != null && completeness == Completeness.COMPLETE && topBlockEntity != null && topBlockEntity.hasFan) {
            energyStored += 10;
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), Block.UPDATE_CLIENTS);
        }
    }

    private void clientTick() {
        if (isMain() && level != null) {
            var wasNearby = playerNearby;
            var nearbyPlayers = level.getEntitiesOfClass(Player.class, new AABB(worldPosition).inflate(10.0));
            playerNearby = !nearbyPlayers.isEmpty() && energyStored > 0;

            if (wasNearby != playerNearby) {
                handleStateChangeOnClient(wasNearby, playerNearby);
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
            var targetStartSeconds = Math.clamp(totalDuration, 0.0f, totalDuration - elapsedSeconds);
            var targetElapsedTicks = (long) (targetStartSeconds * 20.0f);
            var adjustedStartTick = ticks - targetElapsedTicks;
            targetAnimationState.start(Math.clamp(adjustedStartTick, Integer.MIN_VALUE, Integer.MAX_VALUE));
        } else {
            targetAnimationState.start(ticks);
        }
    }

    private void updateState() {
        if (level != null) {
            var pillars = 0;
            var hasTop = false;
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
                calculatedCompleteness = pillars >= 20
                        ? Completeness.COMPLETE
                        : Completeness.NO_TOP;
            } else {
                if (pillars >= 20) {
                    calculatedCompleteness = Completeness.NO_TOP;
                }
            }
            if (calculatedCompleteness != completeness) {
                completeness = calculatedCompleteness;
                setChanged();
                level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), Block.UPDATE_CLIENTS);
            }
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
            setChanged();
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
        var clamped = Math.clamp(energyStorage, 0, getMaxEnergyStorage());
        if (clamped != energyStored) {
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

    public AABB getRenderBoundingBox() {
        var pos = getBlockPos().getCenter();
        var radius = 1.5f;
        return new AABB(pos.x - radius, pos.y - radius, pos.z - radius, pos.x + radius, pos.y + radius, pos.z + radius);
    }
}