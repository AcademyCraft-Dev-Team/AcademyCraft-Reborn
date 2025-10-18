package org.academy.internal.common.world.inventory;

import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import org.academy.internal.common.world.level.block.Blocks;
import org.jetbrains.annotations.NotNull;

public final class SolarGenMenu extends AbstractContainerMenu {
    public final ContainerLevelAccess access;

    public SolarGenMenu(int containerId, Inventory playerInventory, ContainerLevelAccess pAccess, Container windgenContainer) {
        super(MenuTypes.SOLAR_GEN.get(), containerId);
        access = pAccess;
        addSlot(new Slot(windgenContainer, 0, 44, 59));
        for (var i = 0; i < 3; ++i) {
            for (var j = 0; j < 9; ++j) {
                addSlot(new Slot(playerInventory, j + i * 9 + 9, 8 + j * 18, 84 + i * 18));
            }
        }

        for (var k = 0; k < 9; ++k) {
            addSlot(new Slot(playerInventory, k, 8 + k * 18, 142));
        }
    }

    public SolarGenMenu(int id, Inventory playerInventory) {
        this(id, playerInventory, ContainerLevelAccess.NULL, new SimpleContainer(1));
    }

    @Override
    public @NotNull ItemStack quickMoveStack(@NotNull Player player, int index) {
        var movedStack = ItemStack.EMPTY;
        var slot = slots.get(index);
        if (slot.hasItem()) {
            var stackInSlot = slot.getItem();
            movedStack = stackInSlot.copy();
            if (index < 1) {
                if (!moveItemStackTo(stackInSlot, 1, slots.size(), true)) {
                    return ItemStack.EMPTY;
                }
            } else if (!moveItemStackTo(stackInSlot, 0, 1, false)) {
                return ItemStack.EMPTY;
            }
            if (stackInSlot.isEmpty()) {
                slot.set(ItemStack.EMPTY);
            } else {
                slot.set(stackInSlot);
            }
            slot.setChanged();
        }
        return movedStack;
    }

    @Override
    public boolean stillValid(@NotNull Player player) {
        return stillValid(access, player, Blocks.SOLAR_GEN.get());
    }
}