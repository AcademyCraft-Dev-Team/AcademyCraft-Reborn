package org.academy.internal.common.world.inventory;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import org.academy.internal.common.world.level.block.Blocks;
import org.academy.internal.common.world.level.block.entity.AerospaceSignalCabinBlockEntity;
import org.jspecify.annotations.Nullable;

public final class AerospaceSignalCabinMenu extends AbstractContainerMenu {
    public static final int BUTTON_RETARGET = 1;
    public static final int BUTTON_CYCLE_SAT = 2;
    public static final int BUTTON_FORCE_CRASH = 5;
    public static final int BUTTON_CANCEL_FORCE_CRASH = 6;
    public static final int BUTTON_BIND_DESIGNATOR = 7;
    public static final int BUTTON_UNBIND_DESIGNATOR = 8;
    /** Select managed satellite by index: BASE + index (0..MAX-1). */
    public static final int BUTTON_SELECT_SAT_BASE = 100;
    public static final int BUTTON_SELECT_SAT_MAX = 32;
    /** Retarget selected satellite to network list index: BASE + index (0..MAX-1). */
    public static final int BUTTON_RETARGET_NET_BASE = 200;
    public static final int BUTTON_RETARGET_NET_MAX = 64;
    /** Rebind selected satellite to unbound laser list index: BASE + index (0..MAX-1). */
    public static final int BUTTON_REBIND_LASER_BASE = 300;
    public static final int BUTTON_REBIND_LASER_MAX = 64;

    public final ContainerLevelAccess access;
    private final @Nullable AerospaceSignalCabinBlockEntity blockEntity;

    public AerospaceSignalCabinMenu(
            int containerId,
            Inventory playerInventory,
            ContainerLevelAccess access,
            @Nullable AerospaceSignalCabinBlockEntity blockEntity
    ) {
        super(MenuTypes.AEROSPACE_SIGNAL_CABIN.get(), containerId);
        this.access = access;
        this.blockEntity = blockEntity;
        if (this.blockEntity != null && this.blockEntity.getLevel() instanceof ServerLevel serverLevel) {
            this.blockEntity.syncOpsSnapshot(serverLevel);
        }
        addPlayerInv(playerInventory);
    }

    public AerospaceSignalCabinMenu(int id, Inventory playerInventory) {
        this(id, playerInventory, ContainerLevelAccess.NULL, null);
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
    public boolean clickMenuButton(Player player, int id) {
        if (blockEntity == null || !(player.level() instanceof ServerLevel serverLevel)) {
            return false;
        }
        return switch (id) {
            case BUTTON_RETARGET -> blockEntity.tryRetarget(serverLevel);
            case BUTTON_CYCLE_SAT -> {
                blockEntity.cycleSelected();
                yield true;
            }
            case BUTTON_FORCE_CRASH -> blockEntity.tryScheduleForceCrash(serverLevel, player);
            case BUTTON_CANCEL_FORCE_CRASH -> blockEntity.tryCancelForceCrash(serverLevel);
            case BUTTON_BIND_DESIGNATOR -> blockEntity.tryBindDesignator(serverLevel, player);
            case BUTTON_UNBIND_DESIGNATOR -> blockEntity.tryUnbindDesignator(serverLevel, player);
            default -> {
                if (id >= BUTTON_SELECT_SAT_BASE && id < BUTTON_SELECT_SAT_BASE + BUTTON_SELECT_SAT_MAX) {
                    blockEntity.selectSatellite(serverLevel, id - BUTTON_SELECT_SAT_BASE);
                    yield true;
                }
                if (id >= BUTTON_RETARGET_NET_BASE && id < BUTTON_RETARGET_NET_BASE + BUTTON_RETARGET_NET_MAX) {
                    yield blockEntity.tryRetargetToNetworkIndex(serverLevel, id - BUTTON_RETARGET_NET_BASE);
                }
                if (id >= BUTTON_REBIND_LASER_BASE && id < BUTTON_REBIND_LASER_BASE + BUTTON_REBIND_LASER_MAX) {
                    yield blockEntity.tryRebindToLaserIndex(serverLevel, id - BUTTON_REBIND_LASER_BASE);
                }
                yield false;
            }
        };
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        var moved = ItemStack.EMPTY;
        var slot = slots.get(index);
        if (slot.hasItem()) {
            var stack = slot.getItem();
            moved = stack.copy();
            // Player-only: main inventory (0..26) <-> hotbar (27..35).
            if (index < 27) {
                if (!moveItemStackTo(stack, 27, 36, false)) {
                    return ItemStack.EMPTY;
                }
            } else if (!moveItemStackTo(stack, 0, 27, false)) {
                return ItemStack.EMPTY;
            }
            if (stack.isEmpty()) {
                slot.set(ItemStack.EMPTY);
            } else {
                slot.setChanged();
            }
        }
        return moved;
    }

    @Override
    public boolean stillValid(Player player) {
        return stillValid(access, player, Blocks.AEROSPACE_SIGNAL_CABIN.get());
    }
}
