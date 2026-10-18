package org.academy.internal.common.sublevel;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;

/** Delegates chest slots to the real plot BlockEntity while validating the host-space player. */
final class SubLevelChestContainer implements Container {
    private final ServerLevel host;
    private final Container chest;
    private final Vec3 worldCenter;

    SubLevelChestContainer(ServerLevel host, Container chest, Vec3 worldCenter) {
        this.host = host;
        this.chest = chest;
        this.worldCenter = worldCenter;
    }

    @Override public int getContainerSize() { return chest.getContainerSize(); }
    @Override public boolean isEmpty() { return chest.isEmpty(); }
    @Override public ItemStack getItem(int slot) { return chest.getItem(slot); }
    @Override public ItemStack removeItem(int slot, int amount) { return chest.removeItem(slot, amount); }
    @Override public ItemStack removeItemNoUpdate(int slot) { return chest.removeItemNoUpdate(slot); }
    @Override public void setItem(int slot, ItemStack stack) { chest.setItem(slot, stack); }
    @Override public void setChanged() { chest.setChanged(); }
    @Override public boolean stillValid(Player player) {
        return player.level() == host
                && player.distanceToSqr(worldCenter) <= 64.0;
    }
    @Override public void clearContent() { chest.clearContent(); }
    @Override public int getMaxStackSize() { return chest.getMaxStackSize(); }
    @Override public boolean canPlaceItem(int slot, ItemStack stack) { return chest.canPlaceItem(slot, stack); }
}
