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
import net.minecraft.world.item.Items;
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

    public static final int SLOT_SATELLITE = SatelliteLaunchPadBlockEntity.SLOT_SATELLITE;
    public static final int SLOT_OBSIDIAN = SatelliteLaunchPadBlockEntity.SLOT_OBSIDIAN;
    public static final int SLOT_TNT = SatelliteLaunchPadBlockEntity.SLOT_TNT;
    public static final int PAD_SLOT_COUNT = SatelliteLaunchPadBlockEntity.SLOT_COUNT;

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
        addSlot(new Slot(padContainer, SLOT_SATELLITE, 80, 12) {
            @Override
            public boolean mayPlace(ItemStack stack) {
                return NetworkRelaySatelliteItem.isSatellite(stack);
            }

            @Override
            public int getMaxStackSize() {
                return 16;
            }
        });
        addSlot(new Slot(padContainer, SLOT_OBSIDIAN, 80, 30) {
            @Override
            public boolean mayPlace(ItemStack stack) {
                return stack.is(Items.OBSIDIAN);
            }

            @Override
            public int getMaxStackSize() {
                return 1;
            }
        });
        addSlot(new Slot(padContainer, SLOT_TNT, 80, 48) {
            @Override
            public boolean mayPlace(ItemStack stack) {
                return stack.is(Items.TNT);
            }

            @Override
            public int getMaxStackSize() {
                return SatelliteLaunchPadBlockEntity.TNT_REQUIRED;
            }
        });
        OwnedDeviceViewerData.sync(viewerData, this.blockEntity, playerInventory.player);
        addPlayerInv(playerInventory);
        addDataSlots(viewerData);
    }

    public SatelliteLaunchPadMenu(int id, Inventory playerInventory) {
        this(id, playerInventory, ContainerLevelAccess.NULL, new SimpleContainer(PAD_SLOT_COUNT));
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
            case BUTTON_LAUNCH -> blockEntity.tryLaunch(serverLevel, player);
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
            if (index < PAD_SLOT_COUNT) {
                if (!moveItemStackTo(stack, PAD_SLOT_COUNT, slots.size(), true)) {
                    return ItemStack.EMPTY;
                }
            } else if (NetworkRelaySatelliteItem.isSatellite(stack)) {
                if (!moveItemStackTo(stack, SLOT_SATELLITE, SLOT_SATELLITE + 1, false)) {
                    return ItemStack.EMPTY;
                }
            } else if (stack.is(Items.OBSIDIAN)) {
                if (!moveItemStackTo(stack, SLOT_OBSIDIAN, SLOT_OBSIDIAN + 1, false)) {
                    return ItemStack.EMPTY;
                }
            } else if (stack.is(Items.TNT)) {
                if (!moveItemStackTo(stack, SLOT_TNT, SLOT_TNT + 1, false)) {
                    return ItemStack.EMPTY;
                }
            } else if (index < PAD_SLOT_COUNT + 27) {
                if (!moveItemStackTo(stack, PAD_SLOT_COUNT + 27, slots.size(), false)) {
                    return ItemStack.EMPTY;
                }
            } else if (!moveItemStackTo(stack, PAD_SLOT_COUNT, PAD_SLOT_COUNT + 27, false)) {
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
