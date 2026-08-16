package org.academy.internal.common.world.level.block.entity;

import net.minecraft.core.BlockPos;
import net.minecraft.core.NonNullList;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.Container;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.entity.AnimationState;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.academy.api.common.wireless.WirelessNode;
import org.academy.api.common.wireless.WirelessUser;
import org.academy.internal.common.world.item.Items;
import org.jspecify.annotations.Nullable;

import java.util.Objects;

public final class OmniCraftingTableBlockEntity extends MultiBlockEntity
        implements Container, WirelessUser {
    public static final int MAX_ENERGY_STORAGE = AbilityDeveloperBlockEntity.MAX_ENERGY_STORAGE;
    public static final int MAX_FLUID_STORAGE = 4_000;
    public static final int FLUID_PER_UNIT = 1_000;

    public final AnimationState unfoldingState = new AnimationState();
    public int ticks;
    public int energyStored;
    public int imagPhaseFluidStored;
    public NonNullList<ItemStack> items = NonNullList.withSize(1, ItemStack.EMPTY);
    @Nullable
    private BlockPos connectedNodePos;

    public OmniCraftingTableBlockEntity(BlockPos pos, BlockState blockState) {
        super(BlockEntityTypes.OMNI_CRAFTING_TABLE.get(), pos, blockState);
        unfoldingState.start(ticks);
    }

    public void tick() {
        ticks++;
        if (!(level instanceof ServerLevel serverLevel) || !isMain()) return;
        validateWirelessConnection(serverLevel);
        acceptFluidUnit();
    }

    private void validateWirelessConnection(ServerLevel level) {
        if (connectedNodePos != null
                && !(level.getBlockEntity(connectedNodePos) instanceof WirelessNode)) {
            setConnectedNodePosition(null);
        }
    }

    private void acceptFluidUnit() {
        if (imagPhaseFluidStored > MAX_FLUID_STORAGE - FLUID_PER_UNIT) return;
        var stack = items.getFirst();
        // There is no separate empty-container output in the authored layout, so units are
        // deliberately accepted one at a time and replaced in place.
        if (stack.getCount() != 1 || !stack.is(Items.IMAG_PHASE_UNIT.get())) return;
        items.set(0, new ItemStack(Items.EMPTY_UNIT.get()));
        setImagPhaseFluidStored(imagPhaseFluidStored + FLUID_PER_UNIT);
    }

    public boolean hasCraftingResources(int energy, int fluid) {
        return getEnergyStored() >= energy && getImagPhaseFluidStored() >= fluid;
    }

    public boolean consumeCraftingResources(int energy, int fluid) {
        if (energy < 0 || fluid < 0 || !hasCraftingResources(energy, fluid)) return false;
        setEnergyStored(getEnergyStored() - energy);
        setImagPhaseFluidStored(getImagPhaseFluidStored() - fluid);
        return true;
    }

    public int getImagPhaseFluidStored() {
        var main = mainEntity();
        return main == null || main == this ? imagPhaseFluidStored : main.getImagPhaseFluidStored();
    }

    public void setImagPhaseFluidStored(int amount) {
        var main = mainEntity();
        if (main != null && main != this) {
            main.setImagPhaseFluidStored(amount);
            return;
        }
        var clamped = Mth.clamp(amount, 0, MAX_FLUID_STORAGE);
        if (clamped == imagPhaseFluidStored) return;
        imagPhaseFluidStored = clamped;
        markAndSync();
    }

    @Nullable
    @Override
    public boolean suppliesWirelessEnergy() {
        return false;
    }

    @Override
    public boolean acceptsWirelessEnergy() {
        return true;
    }

    @Override
    public BlockPos getConnectedNodePosition() {
        var main = mainEntity();
        return main == null || main == this ? connectedNodePos : main.getConnectedNodePosition();
    }

    @Override
    public void setConnectedNodePosition(@Nullable BlockPos nodePos) {
        var main = mainEntity();
        if (main != null && main != this) {
            main.setConnectedNodePosition(nodePos);
            return;
        }
        if (Objects.equals(connectedNodePos, nodePos)) return;
        connectedNodePos = nodePos;
        markAndSync();
    }

    @Override
    public int extractEnergy(int maxExtract, boolean simulate) {
        if (maxExtract <= 0) return 0;
        var main = mainEntity();
        if (main != null && main != this) return main.extractEnergy(maxExtract, simulate);
        var extracted = Math.min(maxExtract, energyStored);
        if (!simulate && extracted > 0) setEnergyStored(energyStored - extracted);
        return extracted;
    }

    @Override
    public int receiveEnergy(int maxReceive, boolean simulate) {
        if (maxReceive <= 0) return 0;
        var main = mainEntity();
        if (main != null && main != this) return main.receiveEnergy(maxReceive, simulate);
        var received = Math.min(maxReceive, getMaxEnergyStorage() - energyStored);
        if (!simulate && received > 0) setEnergyStored(energyStored + received);
        return received;
    }

    @Override
    public int getEnergyStored() {
        var main = mainEntity();
        return main == null || main == this ? energyStored : main.getEnergyStored();
    }

    public void setEnergyStored(int amount) {
        var main = mainEntity();
        if (main != null && main != this) {
            main.setEnergyStored(amount);
            return;
        }
        var clamped = Mth.clamp(amount, 0, getMaxEnergyStorage());
        if (clamped == energyStored) return;
        energyStored = clamped;
        markAndSync();
    }

    @Override
    public int getMaxEnergyStorage() {
        return MAX_ENERGY_STORAGE;
    }

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        if (!isMain()) return;
        ContainerHelper.saveAllItems(output, items);
        output.putInt("energy_stored", energyStored);
        output.putInt("imag_phase_fluid_stored", imagPhaseFluidStored);
        if (connectedNodePos != null) output.putLong("connected_node_pos", connectedNodePos.asLong());
    }

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        items = NonNullList.withSize(getContainerSize(), ItemStack.EMPTY);
        if (!isMain()) {
            connectedNodePos = null;
            return;
        }
        ContainerHelper.loadAllItems(input, items);
        energyStored = Mth.clamp(input.getIntOr("energy_stored", 0), 0, getMaxEnergyStorage());
        imagPhaseFluidStored = Mth.clamp(
                input.getIntOr("imag_phase_fluid_stored", 0), 0, MAX_FLUID_STORAGE);
        connectedNodePos = null;
        input.getLong("connected_node_pos")
                .ifPresent(value -> connectedNodePos = BlockPos.of(value));
    }

    @Override
    public int getContainerSize() {
        return 1;
    }

    @Override
    public boolean isEmpty() {
        var main = mainEntity();
        return main == null || main == this
                ? items.stream().allMatch(ItemStack::isEmpty)
                : main.isEmpty();
    }

    @Override
    public ItemStack getItem(int slot) {
        var main = mainEntity();
        return main == null || main == this ? items.get(slot) : main.getItem(slot);
    }

    @Override
    public ItemStack removeItem(int slot, int amount) {
        var main = mainEntity();
        if (main != null && main != this) return main.removeItem(slot, amount);
        var stack = ContainerHelper.removeItem(items, slot, amount);
        if (!stack.isEmpty()) markAndSync();
        return stack;
    }

    @Override
    public ItemStack removeItemNoUpdate(int slot) {
        var main = mainEntity();
        return main == null || main == this
                ? ContainerHelper.takeItem(items, slot)
                : main.removeItemNoUpdate(slot);
    }

    @Override
    public void setItem(int slot, ItemStack stack) {
        var main = mainEntity();
        if (main != null && main != this) {
            main.setItem(slot, stack);
            return;
        }
        items.set(slot, stack);
        if (stack.getCount() > getMaxStackSize()) stack.setCount(getMaxStackSize());
        markAndSync();
    }

    @Override
    public boolean stillValid(Player player) {
        var main = mainEntity();
        return main == null || main == this
                ? Container.stillValidBlockEntity(this, player)
                : main.stillValid(player);
    }

    @Override
    public void clearContent() {
        var main = mainEntity();
        if (main != null && main != this) {
            main.clearContent();
            return;
        }
        items.clear();
        markAndSync();
    }

    public AABB getRenderBoundingBox() {
        var pos = Vec3.atCenterOf(getBlockPos());
        var radius = 2d;
        return new AABB(pos.x - radius, pos.y - radius, pos.z - radius,
                pos.x + radius, pos.y + radius, pos.z + radius);
    }

    @Nullable
    private OmniCraftingTableBlockEntity mainEntity() {
        if (isMain() || level == null || mainPos == null) return this;
        return getMain() instanceof OmniCraftingTableBlockEntity main ? main : null;
    }

    private void markAndSync() {
        setChanged();
        if (level != null && !level.isClientSide()) {
            level.sendBlockUpdated(getBlockPos(), getBlockState(), getBlockState(),
                    Block.UPDATE_NEIGHBORS | Block.UPDATE_CLIENTS);
        }
    }
}
