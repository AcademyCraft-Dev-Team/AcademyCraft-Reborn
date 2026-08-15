package org.academy.internal.server.ability;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.transfer.access.ItemAccess;
import net.neoforged.neoforge.transfer.energy.EnergyHandler;
import net.neoforged.neoforge.transfer.transaction.Transaction;
import org.academy.api.common.ability.DevelopmentSource;
import org.academy.api.common.wireless.WirelessUser;
import org.academy.internal.common.world.item.Items;
import org.academy.internal.common.world.level.block.entity.AbilityDeveloperBlockEntity;
import org.jspecify.annotations.Nullable;

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
        var slot = hand == InteractionHand.MAIN_HAND
                ? player.getInventory().getSelectedSlot()
                : Inventory.SLOT_OFFHAND;
        var access = ItemAccess.forPlayerSlot(player, slot);
        var energy = access.getCapability(Capabilities.Energy.ITEM);
        return energy == null ? null : new TabletEnergyUser(energy);
    }

    private record TabletEnergyUser(EnergyHandler energy) implements WirelessUser {
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
            if (maxExtract <= 0) return 0;
            try (var transaction = Transaction.openRoot()) {
                var extracted = energy.extract(maxExtract, transaction);
                if (!simulate && extracted > 0) transaction.commit();
                return extracted;
            }
        }

        @Override
        public int receiveEnergy(int maxReceive, boolean simulate) {
            if (maxReceive <= 0) return 0;
            try (var transaction = Transaction.openRoot()) {
                var inserted = energy.insert(maxReceive, transaction);
                if (!simulate && inserted > 0) transaction.commit();
                return inserted;
            }
        }

        @Override
        public int getEnergyStored() {
            return energy.getAmountAsInt();
        }

        @Override
        public int getMaxEnergyStorage() {
            return energy.getCapacityAsInt();
        }
    }
}
