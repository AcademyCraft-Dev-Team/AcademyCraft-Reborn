package org.academy.internal.common.util;

import net.neoforged.neoforge.transfer.energy.SimpleEnergyHandler;
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
}
