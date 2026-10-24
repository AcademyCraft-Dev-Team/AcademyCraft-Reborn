package org.academy.internal.common.sublevel;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;

import java.util.Objects;
import java.util.function.Supplier;

/** Delegates slots to the real plot BlockEntity while validating the host-space player distance. */
final class SubLevelContainer implements Container {
    private final ServerLevel host;
    private final Container chest;
    private final SubLevelId subLevelId;
    private final BlockPos local;
    private final Supplier<Vec3> worldCenter;

    SubLevelContainer(ServerLevel host, Container chest, SubLevelId subLevelId, BlockPos local,
                      Supplier<Vec3> worldCenter) {
        this.host = Objects.requireNonNull(host, "host");
        this.chest = Objects.requireNonNull(chest, "chest");
        this.subLevelId = Objects.requireNonNull(subLevelId, "subLevelId");
        this.local = Objects.requireNonNull(local, "local").immutable();
        this.worldCenter = Objects.requireNonNull(worldCenter, "worldCenter");
    }

    SubLevelId subLevelId() {
        return subLevelId;
    }

    BlockPos local() {
        return local;
    }

    @Override public int getContainerSize() { return chest.getContainerSize(); }
    @Override public boolean isEmpty() { return chest.isEmpty(); }
    @Override public ItemStack getItem(int slot) { return chest.getItem(slot); }
    @Override public ItemStack removeItem(int slot, int amount) { return chest.removeItem(slot, amount); }
    @Override public ItemStack removeItemNoUpdate(int slot) { return chest.removeItemNoUpdate(slot); }
    @Override public void setItem(int slot, ItemStack stack) { chest.setItem(slot, stack); }
    @Override public void setChanged() { chest.setChanged(); }
    @Override public boolean stillValid(Player player) {
        var runtime = SubLevelServer.runtime(host).orElse(null);
        var body = runtime == null ? null : runtime.get(subLevelId).orElse(null);
        var entity = body == null ? null : runtime.storage().blockEntity(body.plot(), local);
        if (entity == null || entity.isRemoved() || !SubLevelServer.published(body)
                || org.academy.internal.common.structure.transaction.PayloadIsolation.denied(entity)
                || !(entity instanceof Container container)
                || !(chest == entity || chest instanceof net.minecraft.world.CompoundContainer pair && pair.contains(container))) return false;
        if (chest instanceof net.minecraft.world.CompoundContainer pair) {
            var state = entity.getBlockState();
            if (!(state.getBlock() instanceof net.minecraft.world.level.block.ChestBlock)
                    || state.getValue(net.minecraft.world.level.block.ChestBlock.TYPE)
                    == net.minecraft.world.level.block.state.properties.ChestType.SINGLE) return false;
            var partner = entity.getLevel().getBlockEntity(entity.getBlockPos().relative(
                    net.minecraft.world.level.block.ChestBlock.getConnectedDirection(state)));
            if (!(partner instanceof Container other) || partner.isRemoved() || !pair.contains(other)
                    || org.academy.internal.common.structure.transaction.PayloadIsolation.denied(partner)) return false;
        }
        return player.level() == host
                && player.distanceToSqr(worldCenter.get()) <= 64.0;
    }
    @Override public void clearContent() { chest.clearContent(); }
    @Override public int getMaxStackSize() { return chest.getMaxStackSize(); }
    @Override public boolean canPlaceItem(int slot, ItemStack stack) { return chest.canPlaceItem(slot, stack); }
}
