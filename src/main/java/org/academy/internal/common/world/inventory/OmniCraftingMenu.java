package org.academy.internal.common.world.inventory;

import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.*;
import net.minecraft.world.item.ItemStack;
import org.academy.internal.common.world.level.block.Blocks;
import org.jetbrains.annotations.NotNull;

public final class OmniCraftingMenu extends AbstractContainerMenu {
    private final ContainerLevelAccess access;
    private final CraftingContainer craftSlots;
    private final ResultContainer resultSlots;

    public OmniCraftingMenu(int containerId, Inventory playerInventory, ContainerLevelAccess levelAccess, Container container) {
        super(MenuTypes.OMNI_CRAFTING_TABLE.get(), containerId);
        access = levelAccess;
        craftSlots = new TransientCraftingContainer(this, 3, 3);
        resultSlots = new ResultContainer();
        addSlot(new Slot(container, 0, 62, 59));
        addSlot(new ResultSlot(playerInventory.player, craftSlots, resultSlots, 0, 134, 29));

        for (var i = 0; i < 3; ++i) {
            for (var j = 0; j < 3; ++j) {
                addSlot(new Slot(craftSlots, j + i * 3, 62 + j * 18, -7 + i * 18));
            }
        }

        for (var k = 0; k < 3; ++k) {
            for (var i1 = 0; i1 < 9; ++i1) {
                addSlot(new Slot(playerInventory, i1 + k * 9 + 9, 8 + i1 * 18, 84 + k * 18));
            }
        }

        for (var l = 0; l < 9; ++l) {
            addSlot(new Slot(playerInventory, l, 8 + l * 18, 142));
        }
    }

    public OmniCraftingMenu(int id, Inventory playerInventory) {
        this(id, playerInventory, ContainerLevelAccess.NULL, new SimpleContainer(1));
    }

    @Override
    public @NotNull ItemStack quickMoveStack(@NotNull Player player, int index) {
        var itemStack = ItemStack.EMPTY;
        var slot = slots.get(index);

        if (!slot.hasItem()) return itemStack;

        var sourceStack = slot.getItem();
        itemStack = sourceStack.copy();

        if (!tryMoveStack(sourceStack, itemStack, slot, index)) return ItemStack.EMPTY;

        if (sourceStack.isEmpty()) slot.set(ItemStack.EMPTY);
        else slot.setChanged();

        if (sourceStack.getCount() == itemStack.getCount()) return ItemStack.EMPTY;

        slot.onTake(player, sourceStack);
        if (index == 1) player.drop(sourceStack, false);

        return itemStack;
    }

    private boolean tryMoveStack(ItemStack sourceStack, ItemStack copyStack, Slot slot, int index) {
        // Slot 1: Output
        if (index == 1) return moveFromOutputSlot(sourceStack, copyStack, slot);
            // Slot 11-46: Player Inventory
        else if (index >= 11 && index < 47) return moveFromPlayerInventory(sourceStack, index);
            // Other Slots (0, 2-10): Input/Machine -> Player Inventory
        else return moveItemStackTo(sourceStack, 11, 47, false);
    }

    private boolean moveFromOutputSlot(ItemStack sourceStack, ItemStack copyStack, Slot slot) {
        // Output -> Player Inventory meow
        if (!moveItemStackTo(sourceStack, 11, 47, true)) return false;
        slot.onQuickCraft(sourceStack, copyStack);
        return true;
    }

    private boolean moveFromPlayerInventory(ItemStack sourceStack, int index) {
        // Player Inventory -> Input Slot (0) meow
        if (!moveItemStackTo(sourceStack, 0, 1, false)) {
            // Player Inventory -> Other Machine Slots (2-10) meow
            if (!moveItemStackTo(sourceStack, 2, 11, false)) {
                return swapPlayerInventory(sourceStack, index);
            }
        }
        return true;
    }

    private boolean swapPlayerInventory(ItemStack sourceStack, int index) {
        // Main Inventory (11-37) -> Hotbar (38-46) meow
        if (index < 38) return moveItemStackTo(sourceStack, 38, 47, false);
            // Hotbar -> Main Inventory meow
        else return moveItemStackTo(sourceStack, 11, 38, false);
    }

    @Override
    public void removed(@NotNull Player player) {
        super.removed(player);
        access.execute((_, _) -> clearContainer(player, craftSlots));
    }

    @Override
    public boolean canTakeItemForPickAll(@NotNull ItemStack stack, Slot slot) {
        return slot.container != resultSlots && super.canTakeItemForPickAll(stack, slot);
    }

    @Override
    public boolean stillValid(@NotNull Player player) {
        return stillValid(access, player, Blocks.OMNI_CRAFTING_TABLE.get());
    }
}