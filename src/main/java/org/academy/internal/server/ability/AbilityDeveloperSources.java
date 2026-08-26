package org.academy.internal.server.ability;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import org.academy.api.common.ability.DevelopmentSource;
import org.academy.api.common.wireless.WirelessUser;
import org.academy.internal.common.world.item.AbilityControlTabletItem;
import org.academy.internal.common.world.item.Items;
import org.academy.internal.common.world.level.block.entity.AbilityDeveloperBlockEntity;
import org.jspecify.annotations.Nullable;

import java.util.Optional;

public final class AbilityDeveloperSources {
    private AbilityDeveloperSources() {
    }

    public static @Nullable WirelessUser resolve(ServerPlayer player, DevelopmentSource source) {
        if (player == null || source == null) return null;
        if (!source.portable()) {
            var pos = source.blockPos();
            if (pos == null || !player.level().hasChunkAt(pos)
                    || player.position().distanceToSqr(Vec3.atCenterOf(pos)) > 64.0) return null;
            var blockEntity = player.level().getBlockEntity(pos);
            return blockEntity instanceof AbilityDeveloperBlockEntity developer && developer.isMain()
                    ? developer
                    : null;
        }

        var hand = source.hand();
        if (hand == null || !player.getItemInHand(hand).is(Items.ABILITY_CONTROL_TABLET.get())) return null;
        return new TabletEnergyUser(player, hand);
    }

    private record TabletEnergyUser(ServerPlayer player, InteractionHand hand) implements WirelessUser {
        @Override
        public boolean suppliesWirelessEnergy() {
            return false;
        }

        @Override
        public boolean acceptsWirelessEnergy() {
            return true;
        }

        @Override
        public @Nullable BlockPos getConnectedNodePosition() {
            return null;
        }

        @Override
        public void setConnectedNodePosition(@Nullable BlockPos nodePos) {
        }

        @Override
        public int extractEnergy(int maxExtract, boolean simulate) {
            var extracted = tablet().map(stack -> ((AbilityControlTabletItem) stack.getItem())
                    .extractEnergy(stack, maxExtract, simulate)).orElse(0);
            if (!simulate && extracted > 0) player.getInventory().setChanged();
            return extracted;
        }

        @Override
        public int receiveEnergy(int maxReceive, boolean simulate) {
            var received = tablet().map(stack -> ((AbilityControlTabletItem) stack.getItem())
                    .receiveEnergy(stack, maxReceive, simulate)).orElse(0);
            if (!simulate && received > 0) player.getInventory().setChanged();
            return received;
        }

        @Override
        public int getEnergyStored() {
            return tablet().map(AbilityControlTabletItem::storedEnergy).orElse(0);
        }

        @Override
        public int getMaxEnergyStorage() {
            return tablet().isPresent() ? AbilityControlTabletItem.ENERGY_CAPACITY : 0;
        }

        private Optional<ItemStack> tablet() {
            var stack = player.getItemInHand(hand);
            return stack.is(Items.ABILITY_CONTROL_TABLET.get())
                    ? Optional.of(stack)
                    : Optional.empty();
        }
    }
}
