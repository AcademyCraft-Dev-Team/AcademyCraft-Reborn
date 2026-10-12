package org.academy.internal.common.structure.space;

import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.ItemStack;

/** Vanilla slots and inventory protocol, bound to one live chest in a structure runtime. */
public final class NativeStructureChestMenu extends ChestMenu {
    private final NativeStructureChestSessions.Session session;
    private boolean removed;
    private boolean dispatchingQuickMove;
    private boolean movingItems;
    private boolean forceResync;

    NativeStructureChestMenu(int containerId, Inventory inventory, NativeStructureChestSessions.Session session) {
        super(MenuType.GENERIC_9x3, containerId, inventory, session.chest, 3);
        this.session = session;
    }

    @Override
    public boolean stillValid(Player player) {
        return player == session.player && session.canRead();
    }

    @Override
    public void clicked(int slot, int button, ContainerInput input, Player player) {
        session.mutate(player, () -> {
            dispatchingQuickMove = input == ContainerInput.QUICK_MOVE;
            try {
                super.clicked(slot, button, input, player);
                return Boolean.TRUE;
            } finally {
                dispatchingQuickMove = false;
            }
        }, Boolean.FALSE);
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        if (player != session.player || movingItems || index < 0 || index >= slots.size()) return ItemStack.EMPTY;
        // Vanilla dispatches quickMoveStack from clicked. Only this specific nested operation
        // shares its guard; arbitrary nested clicked/mutate calls remain refused.
        if (dispatchingQuickMove) return moveStack(player, index);
        return session.mutate(player, () -> moveStack(player, index), ItemStack.EMPTY);
    }

    @Override
    public void setSelectedBundleItemIndex(int slotIndex, int selectedItemIndex) {
        // Vanilla's bundle packet bypasses both clicked and stillValid and has no menu id.
        session.mutate(session.player, () -> {
            super.setSelectedBundleItemIndex(slotIndex, selectedItemIndex);
            return Boolean.TRUE;
        }, Boolean.FALSE);
    }

    private ItemStack moveStack(Player player, int index) {
        movingItems = true;
        try {
            return super.quickMoveStack(player, index);
        } finally {
            movingItems = false;
        }
    }

    void requestResync() {
        forceResync = true;
    }

    /** Called before vanilla sends its close packet or replaces player.containerMenu. */
    public boolean deferCloseIfMutating() {
        return session.deferCloseIfMutating();
    }

    @Override
    public void broadcastChanges() {
        // ServerPlayer ticks broadcast before checking stillValid. Never read an obsolete BE.
        session.read(() -> {
            if (forceResync) {
                // The click handler has now recorded the client's predicted hashes. Reconcile
                // after that update, not from inside clicked while remote updates are suppressed.
                forceResync = false;
                super.broadcastFullState();
            } else {
                super.broadcastChanges();
            }
        });
    }

    @Override
    public void broadcastFullState() {
        session.read(() -> {
            forceResync = false;
            super.broadcastFullState();
        });
    }

    @Override
    public void sendAllDataToRemote() {
        // Also called by openMenu's synchronizer before player.containerMenu is assigned.
        session.read(super::sendAllDataToRemote);
    }

    @Override
    public void removed(Player player) {
        if (removed || session.deferCloseIfMutating()) return;
        removed = true;
        session.beginRemoval();
        try {
            // Vanilla returns/drops carried items in the real player's host, then stopOpen.
            super.removed(player);
        } finally {
            session.finishRemoval();
        }
    }
}
