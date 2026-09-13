package org.academy.internal.common.world.inventory;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import org.academy.internal.common.world.item.NetworkRelaySatelliteItem;
import org.academy.internal.common.world.level.block.Blocks;
import org.academy.internal.common.world.level.block.entity.SatelliteLaunchPadBlockEntity;
import org.jspecify.annotations.Nullable;

public final class SatelliteLaunchPadMenu extends AbstractContainerMenu {
    public static final int BUTTON_LAUNCH = 0;
    public static final int BUTTON_CYCLE_DIM = 1;
    public static final int BUTTON_CYCLE_LASER = 2;
    /** Select a networked laser by list index: BASE + index (0..MAX-1). */
    public static final int BUTTON_SELECT_LASER_BASE = 100;
    public static final int BUTTON_SELECT_LASER_MAX = 64;
    public final ContainerLevelAccess access;
    private final @Nullable SatelliteLaunchPadBlockEntity blockEntity;
    private final ContainerData viewerData = OwnedDeviceViewerData.create();

    public @Nullable SatelliteLaunchPadBlockEntity getBlockEntity() {
        return blockEntity;
    }

    public SatelliteLaunchPadMenu(
            int containerId,
            Inventory playerInventory,
            ContainerLevelAccess access,
            Container padContainer
    ) {
        super(MenuTypes.SATELLITE_LAUNCH_PAD.get(), containerId);
        this.access = access;
        this.blockEntity = padContainer instanceof SatelliteLaunchPadBlockEntity pad ? pad : null;
        if (this.blockEntity != null && this.blockEntity.getLevel() instanceof ServerLevel serverLevel) {
            this.blockEntity.syncLaunchSnapshot(serverLevel);
        }
        addSlot(new Slot(padContainer, 0, 80, 35) {
            @Override
            public boolean mayPlace(ItemStack stack) {
                return NetworkRelaySatelliteItem.isSatellite(stack);
            }

            @Override
            public int getMaxStackSize() {
                return 16;
            }
        });
        OwnedDeviceViewerData.sync(viewerData, this.blockEntity, playerInventory.player);
        addPlayerInv(playerInventory);
        addDataSlots(viewerData);
    }

    public SatelliteLaunchPadMenu(int id, Inventory playerInventory) {
        this(id, playerInventory, ContainerLevelAccess.NULL, new SimpleContainer(1));
    }

    public boolean viewerIsOwner() {
        return OwnedDeviceViewerData.isOwner(viewerData);
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
            case BUTTON_CYCLE_DIM -> {
                blockEntity.cycleHyperDimension(serverLevel);
                broadcastChanges();
                yield true;
            }
            case BUTTON_CYCLE_LASER -> {
                blockEntity.cycleSelectedLaser(serverLevel);
                yield true;
            }
            default -> {
                if (id >= BUTTON_SELECT_LASER_BASE && id < BUTTON_SELECT_LASER_BASE + BUTTON_SELECT_LASER_MAX) {
                    yield blockEntity.trySelectLaser(serverLevel, id - BUTTON_SELECT_LASER_BASE);
                }
                yield false;
            }
        };
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        ItemStack result = ItemStack.EMPTY;
        Slot slot = slots.get(index);
        if (slot.hasItem()) {
            ItemStack stack = slot.getItem();
            result = stack.copy();
            if (index == 0) {
                if (!moveItemStackTo(stack, 1, slots.size(), true)) {
                    return ItemStack.EMPTY;
                }
            } else if (NetworkRelaySatelliteItem.isSatellite(stack)) {
                if (!moveItemStackTo(stack, 0, 1, false)) {
                    return ItemStack.EMPTY;
                }
            } else if (index < 28) {
                if (!moveItemStackTo(stack, 28, slots.size(), false)) {
                    return ItemStack.EMPTY;
                }
            } else if (!moveItemStackTo(stack, 1, 28, false)) {
                return ItemStack.EMPTY;
            }
            if (stack.isEmpty()) {
                slot.setByPlayer(ItemStack.EMPTY);
            } else {
                slot.setChanged();
            }
        }
        return result;
    }

    @Override
    public boolean stillValid(Player player) {
        OwnedDeviceViewerData.sync(viewerData, blockEntity, player);
        return stillValid(access, player, Blocks.SATELLITE_LAUNCH_PAD.get());
    }
}
