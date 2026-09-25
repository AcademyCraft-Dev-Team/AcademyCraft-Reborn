package org.academy.internal.common.ability.aeromanip;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class AeromanipChargeTierTest {
    @Test
    void chargeThresholdsUseServerTickBoundaries() {
        assertEquals(AeromanipChargeTier.INSTANT, AeromanipChargeTier.fromTicks(-1));
        assertEquals(AeromanipChargeTier.INSTANT, AeromanipChargeTier.fromTicks(7));
        assertEquals(AeromanipChargeTier.HALF, AeromanipChargeTier.fromTicks(8));
        assertEquals(AeromanipChargeTier.HALF, AeromanipChargeTier.fromTicks(23));
        assertEquals(AeromanipChargeTier.FULL, AeromanipChargeTier.fromTicks(24));
    }

    @Test
    void elapsedChargeTimeNeverBecomesNegative() {
        assertEquals(0L, AeromanipChargeContext.elapsedTicks(100L, 99L));
        assertEquals(0L, AeromanipChargeContext.elapsedTicks(100L, 100L));
        assertEquals(24L, AeromanipChargeContext.elapsedTicks(100L, 124L));
    }
}
