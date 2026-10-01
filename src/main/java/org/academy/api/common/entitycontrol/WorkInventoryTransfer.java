package org.academy.api.common.entitycontrol;

import net.minecraft.world.Container;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.HopperBlockEntity;

/** Bounded insertion into a container. The caller owns and saves the mutable remainder. */
public final class WorkInventoryTransfer {
    public record Result(int nextSlot, int visited, int moved, boolean completePass) {}
    private WorkInventoryTransfer() {}
    public static Result insert(Container container, ItemStack remainder, int start, int maximumSlots, WorkBudget budget) {
        return insert(container, remainder, start, maximumSlots, budget, container.getContainerSize());
    }
    /** slotCount can restrict player insertion to the non-equipment inventory. */
    public static Result insert(Container container, ItemStack remainder, int start, int maximumSlots, WorkBudget budget, int slotCount) {
        if (maximumSlots < 0 || slotCount < 0) throw new IllegalArgumentException("Negative slot limit");
        int size = Math.min(slotCount, container.getContainerSize());
        if (size == 0 || remainder.isEmpty()) return new Result(0, 0, 0, true);
        // Vanilla additionally maintains a receiving hopper's transfer cooldown.
        if (container instanceof HopperBlockEntity && size <= maximumSlots) {
            if (!budget.reserve(WorkBudget.Operation.SLOT, size) || !budget.reserve(WorkBudget.Operation.TRANSFER, size)) {
                return new Result(start, 0, 0, false);
            }
            int before = remainder.getCount();
            var rest = HopperBlockEntity.addItem(null, container, remainder.copy(), null);
            remainder.setCount(rest.getCount());
            return new Result(0, size, before - remainder.getCount(), true);
        }
        int slot = Math.floorMod(start, size), visited = 0, movedTotal = 0;
        while (!remainder.isEmpty() && visited < Math.min(maximumSlots, size) && budget.spend(WorkBudget.Operation.SLOT)) {
            var existing = container.getItem(slot);
            if (container.canPlaceItem(slot, remainder) && (existing.isEmpty() || ItemStack.isSameItemSameComponents(existing, remainder))) {
                int moved = Math.clamp(Math.min(container.getMaxStackSize(), remainder.getMaxStackSize())
                        - existing.getCount(), 0, remainder.getCount());
                if (moved > 0) {
                    if (!budget.spend(WorkBudget.Operation.TRANSFER)) break;
                    // Publish the reduced remainder before callbacks from setChanged can re-enter saving.
                    if (existing.isEmpty()) container.setItem(slot, remainder.split(moved));
                    else { existing.grow(moved); remainder.shrink(moved); }
                    movedTotal += moved;
                    container.setChanged();
                }
            }
            visited++;
            slot = (slot + 1) % size;
        }
        return new Result(slot, visited, movedTotal, visited == size);
    }
}
