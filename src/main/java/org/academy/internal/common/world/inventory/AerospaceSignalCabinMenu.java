package org.academy.internal.common.world.inventory;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import org.academy.internal.common.world.item.NetworkRelaySatelliteItem;
import org.academy.internal.common.world.level.block.Blocks;
import org.academy.internal.common.world.level.block.entity.AerospaceSignalCabinBlockEntity;
import org.jspecify.annotations.Nullable;

public final class AerospaceSignalCabinMenu extends AbstractContainerMenu {
    public static final int BUTTON_LAUNCH = 0;
    public static final int BUTTON_RETARGET = 1;
    public static final int BUTTON_CYCLE_SAT = 2;
    public static final int BUTTON_CYCLE_DIM = 3;
    public static final int BUTTON_CYCLE_LASER = 4;
    public static final int BUTTON_FORCE_CRASH = 5;
    public static final int BUTTON_CANCEL_FORCE_CRASH = 6;
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
            Container cabinContainer
    ) {
        super(MenuTypes.AEROSPACE_SIGNAL_CABIN.get(), containerId);
        this.access = access;
        this.blockEntity = cabinContainer instanceof AerospaceSignalCabinBlockEntity cabin ? cabin : null;
        if (this.blockEntity != null && this.blockEntity.getLevel() instanceof ServerLevel serverLevel) {
            this.blockEntity.syncOpsSnapshot(serverLevel);
        }
        // Place satellite slot in the upper inventory panel (same band as SolarGen machine slot).
        addSlot(new Slot(cabinContainer, 0, 80, 35) {
            @Override
            public boolean mayPlace(ItemStack stack) {
                return NetworkRelaySatelliteItem.isSatellite(stack);
            }

            @Override
            public int getMaxStackSize() {
                return 16;
            }
        });
        addPlayerInv(playerInventory);
    }

    public AerospaceSignalCabinMenu(int id, Inventory playerInventory) {
        this(id, playerInventory, ContainerLevelAccess.NULL, new SimpleContainer(1));
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
            case BUTTON_LAUNCH -> blockEntity.tryLaunch(serverLevel);
            case BUTTON_RETARGET -> blockEntity.tryRetarget(serverLevel);
            case BUTTON_CYCLE_SAT -> {
                blockEntity.cycleSelected();
                yield true;
            }
            case BUTTON_CYCLE_DIM -> {
                blockEntity.cycleHyperDimension();
                broadcastChanges();
                yield true;
            }
            case BUTTON_CYCLE_LASER -> {
                blockEntity.cycleSelectedLaser(serverLevel);
                yield true;
            }
            case BUTTON_FORCE_CRASH -> blockEntity.tryScheduleForceCrash(serverLevel);
            case BUTTON_CANCEL_FORCE_CRASH -> blockEntity.tryCancelForceCrash(serverLevel);
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
            if (index < 1) {
                if (!moveItemStackTo(stack, 1, slots.size(), true)) {
                    return ItemStack.EMPTY;
                }
            } else if (!moveItemStackTo(stack, 0, 1, false)) {
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
