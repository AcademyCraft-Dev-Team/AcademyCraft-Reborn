package org.academy.internal.common.world.inventory;

import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.NotNull;

public final class WirelessNodeMenu extends AbstractContainerMenu {
    public final ContainerLevelAccess access;
    public final Container container;

    public WirelessNodeMenu(int containerId, Inventory playerInventory, ContainerLevelAccess levelAccess, Container nodeContainer) {
        super(MenuTypes.NODE.get(), containerId);
        access = levelAccess;
        container = nodeContainer;

        addSlot(new Slot(nodeContainer, 0, 44, -11));
        addSlot(new Slot(nodeContainer, 1, 44, 59));

        for (var i = 0; i < 3; ++i) {
            for (var j = 0; j < 9; ++j) {
                addSlot(new Slot(playerInventory, j + i * 9 + 9, 8 + j * 18, 84 + i * 18));
            }
        }

        for (var k = 0; k < 9; ++k) {
            addSlot(new Slot(playerInventory, k, 8 + k * 18, 142));
        }
    }

    public WirelessNodeMenu(int id, Inventory playerInventory) {
        this(id, playerInventory, ContainerLevelAccess.NULL, new SimpleContainer(2));
    }

    public @NotNull ItemStack quickMoveStack(@NotNull Player pPlayer, int index) {
        var itemstack = ItemStack.EMPTY;
        var slot = slots.get(index);
        if (slot.hasItem()) {
            var itemStack = slot.getItem();
            itemstack = itemStack.copy();
            if (index < 2) {
                if (!moveItemStackTo(itemStack, 2, slots.size(), true)) {
                    return ItemStack.EMPTY;
                }
            } else if (!moveItemStackTo(itemStack, 0, 2, false)) {
                return ItemStack.EMPTY;
            }

            if (itemStack.isEmpty()) {
                slot.setByPlayer(ItemStack.EMPTY);
            } else {
                slot.setChanged();
            }
        }

        return itemstack;
    }

    @Override
    public boolean stillValid(@NotNull Player player) {
        return container.stillValid(player);
    }
}