package org.academy.internal.common.world.level.block.entity;

import net.minecraft.core.BlockPos;
import net.minecraft.core.NonNullList;
import net.minecraft.util.Mth;
import net.minecraft.world.Container;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.attribute.EnvironmentAttributes;
import net.minecraft.world.entity.AnimationState;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.AABB;
import org.academy.api.common.wireless.WirelessUser;
import org.jspecify.annotations.Nullable;

import java.util.Objects;

public final class SolarGenBlockEntity extends BlockEntity implements WirelessUser, Container {
    public int ticks;
    public int energyStored;

    @Nullable
    private BlockPos connectedNodePos = null;
    private NonNullList<ItemStack> items = NonNullList.withSize(1, ItemStack.EMPTY);

    public final AnimationState foldingState = new AnimationState();
    public final AnimationState unfoldingState = new AnimationState();

    private static final int MAX_ENERGY_STORAGE = 100_000;
    private static final int GENERATION_RATE = 50;

    private State state = State.SUNNY;

    public SolarGenBlockEntity(BlockPos pos, BlockState blockState) {
        super(BlockEntityTypes.SOLAR_GEN.get(), pos, blockState);
    }

    public static void tick(Level level, BlockPos pos, BlockState state, SolarGenBlockEntity blockEntity) {
        blockEntity.ticks++;

        var target = level.getBrightness(LightLayer.SKY, pos) - level.getSkyDarken();
        var sunAngle = level.environmentAttributes().getValue(EnvironmentAttributes.SUN_ANGLE, pos) * (float) (Math.PI / 180.0);
        if (target > 0) {
            var offset = sunAngle < (float) Math.PI ? 0.0F : (float) (Math.PI * 2);
            sunAngle += (offset - sunAngle) * 0.2F;
            target = Math.round(target * Mth.cos(sunAngle));
        }

        var brightness = Mth.clamp(target, 0, 15);
        var hasBrightness = brightness != 0;
        blockEntity.foldingState.animateWhen(!hasBrightness, blockEntity.ticks);
        blockEntity.unfoldingState.animateWhen(hasBrightness, blockEntity.ticks);
        blockEntity.setEnergyStored(blockEntity.energyStored + brightness * GENERATION_RATE);

        if (level.isRainingAt(pos) || level.isRainingAt(pos.above())) {
            blockEntity.state = State.RAINY;
        } else {
            if (hasBrightness) {
                blockEntity.state = State.SUNNY;
            } else {
                blockEntity.state = State.NIGHT;
            }
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
            if (level != null) {
                level.sendBlockUpdated(getBlockPos(), getBlockState(), getBlockState(), Block.UPDATE_ALL);
            }
        }
    }

    @Override
    public int extractEnergy(int maxExtract, boolean simulate) {
        var energyToExtract = Math.min(maxExtract, energyStored);
        if (energyToExtract <= 0) {
            return 0;
        }
        if (!simulate) {
            setEnergyStored(energyStored - energyToExtract);
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
        return MAX_ENERGY_STORAGE;
    }

    public void setEnergyStored(int newEnergy) {
        var clamped = Math.clamp(newEnergy, 0, getMaxEnergyStorage());
        if (clamped != energyStored) {
            energyStored = clamped;
            setChanged();
            if (level != null && !level.isClientSide()) {
                level.sendBlockUpdated(getBlockPos(), getBlockState(), getBlockState(), Block.UPDATE_ALL);
            }
        }
    }

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        ContainerHelper.saveAllItems(output, items);
        output.putInt("energy_stored", energyStored);
        if (connectedNodePos != null) {
            output.putLong("connected_node_pos", connectedNodePos.asLong());
        }
    }

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        items = NonNullList.withSize(getContainerSize(), ItemStack.EMPTY);
        ContainerHelper.loadAllItems(input, items);
        energyStored = input.getIntOr("energy_stored", 0);
        input.getLong("connected_node_pos").ifPresent(nodePos -> connectedNodePos = BlockPos.of(nodePos));
    }

    public AABB getRenderBoundingBox() {
        var pos = getBlockPos().getCenter();
        var radius = 0.8;
        return new AABB(pos.x - radius, pos.y - radius, pos.z - radius, pos.x + radius, pos.y + radius, pos.z + radius);
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
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), Block.UPDATE_ALL);
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

    public State getState() {
        return state;
    }

    public enum State {
        NIGHT, RAINY, SUNNY
    }
}