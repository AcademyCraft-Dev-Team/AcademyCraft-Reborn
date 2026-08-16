package org.academy.internal.common.util;

import net.minecraft.core.BlockPos;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.transfer.energy.SimpleEnergyHandler;
import org.academy.api.common.energy.AcademyEnergyItem;
import org.academy.api.common.wireless.WirelessUser;
import org.academy.internal.common.world.item.AbilityControlTabletItem;
import org.academy.internal.common.world.level.block.entity.AbilityDeveloperBlockEntity;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class EnergyChargeHelperTest {
    @Test
    void commitsTheMaximumAcceptedByOneTransfer() {
        var handler = new SimpleEnergyHandler(100, 30);

        assertEquals(30, EnergyChargeHelper.charge(handler));
        assertEquals(30, handler.getAmountAsInt());
        assertEquals(30, EnergyChargeHelper.charge(handler));
        assertEquals(60, handler.getAmountAsInt());
    }

    @Test
    void returnsZeroForAFullOrMissingHandler() {
        var full = new SimpleEnergyHandler(100, 100, 100, 100);

        assertEquals(0, EnergyChargeHelper.charge(full));
        assertEquals(0, EnergyChargeHelper.charge(null));
    }

    @Test
    void tabletCapacityIsOneQuarterOfAbilityDeveloperCapacity() {
        assertEquals(
                AbilityDeveloperBlockEntity.MAX_ENERGY_STORAGE / 4,
                AbilityControlTabletItem.ENERGY_CAPACITY
        );
    }

    @Test
    void generatorTransferPrefersNativeAcademyItemEnergy() {
        var item = new TestEnergyItem(100);
        var source = new TestEnergySource(80);

        assertEquals(30, EnergyChargeHelper.transferToAcademyItem(source, null, item, 30));
        assertEquals(30, item.getEnergyStored(null));
        assertEquals(50, source.getEnergyStored());
        assertEquals(50, EnergyChargeHelper.transferToAcademyItem(source, null, item, 100));
        assertEquals(80, item.getEnergyStored(null));
        assertEquals(0, source.getEnergyStored());
    }

    private static final class TestEnergyItem implements AcademyEnergyItem {
        private final int capacity;
        private int stored;

        private TestEnergyItem(int capacity) {
            this.capacity = capacity;
        }

        @Override
        public int getEnergyStored(ItemStack stack) {
            return stored;
        }

        @Override
        public int getMaxEnergyStored(ItemStack stack) {
            return capacity;
        }

        @Override
        public int receiveEnergy(ItemStack stack, int maxReceive, boolean simulate) {
            var received = Math.min(maxReceive, capacity - getEnergyStored(stack));
            if (!simulate && received > 0) stored += received;
            return received;
        }

        @Override
        public int extractEnergy(ItemStack stack, int maxExtract, boolean simulate) {
            var extracted = Math.min(maxExtract, getEnergyStored(stack));
            if (!simulate && extracted > 0) stored -= extracted;
            return extracted;
        }
    }

    private static final class TestEnergySource implements WirelessUser {
        private int stored;

        private TestEnergySource(int stored) {
            this.stored = stored;
        }

        @Override
        public BlockPos getConnectedNodePosition() {
            return null;
        }

        @Override
        public void setConnectedNodePosition(BlockPos nodePos) {
        }

        @Override
        public int extractEnergy(int maxExtract, boolean simulate) {
            var extracted = Math.min(maxExtract, stored);
            if (!simulate) stored -= extracted;
            return extracted;
        }

        @Override
        public int receiveEnergy(int maxReceive, boolean simulate) {
            return 0;
        }

        @Override
        public int getEnergyStored() {
            return stored;
        }

        @Override
        public int getMaxEnergyStorage() {
            return 80;
        }
    }
}
