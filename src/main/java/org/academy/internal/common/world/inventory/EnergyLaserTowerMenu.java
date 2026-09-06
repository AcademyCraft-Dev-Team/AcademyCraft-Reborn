package org.academy.internal.common.world.inventory;

import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import org.academy.internal.common.world.level.block.Blocks;
import org.academy.internal.common.world.level.block.entity.EnergyLaserTowerBlockEntity;
import org.jspecify.annotations.Nullable;

public final class EnergyLaserTowerMenu extends AbstractContainerMenu {
    public final ContainerLevelAccess access;
    public final @Nullable EnergyLaserTowerBlockEntity blockEntity;

    public EnergyLaserTowerMenu(
            int containerId,
            Inventory playerInventory,
            ContainerLevelAccess access,
            EnergyLaserTowerBlockEntity blockEntity
    ) {
        super(MenuTypes.ENERGY_LASER_TOWER.get(), containerId);
        this.access = access;
        this.blockEntity = blockEntity;
        addPlayerInv(playerInventory);
    }

    public EnergyLaserTowerMenu(int id, Inventory playerInventory) {
        super(MenuTypes.ENERGY_LASER_TOWER.get(), id);
        this.access = ContainerLevelAccess.NULL;
        this.blockEntity = null;
        addPlayerInv(playerInventory);
    }

    private void addPlayerInv(Inventory playerInventory) {
        for (var i = 0; i < 3; ++i) {
            for (var j = 0; j < 9; ++j) {
                addSlot(new Slot(playerInventory, j + i * 9 + 9, 8 + j * 18, 84 + i * 18));
            }
        }
        for (var k = 0; k < 9; ++k) {
            addSlot(new Slot(playerInventory, k, 8 + k * 18, 142));
        }
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        return ItemStack.EMPTY;
    }

    @Override
    public boolean stillValid(Player player) {
        return stillValid(access, player, Blocks.ENERGY_LASER_TOWER.get());
    }
}
