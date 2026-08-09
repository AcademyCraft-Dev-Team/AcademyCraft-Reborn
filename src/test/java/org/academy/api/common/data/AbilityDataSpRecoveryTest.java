package org.academy.api.common.data;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AbilityDataSpRecoveryTest {
    @Test
    void doesNotRecoverWithoutAnActiveFoodRecoveryPeriod() {
        var data = AbilityData.builder().currSP(500).build();

        for (var tick = 0; tick < 200; tick++) {
            assertFalse(data.tickFoodSpRecovery());
        }

        assertEquals(500, data.getCurrSP());
    }

    @Test
    void activeFoodRecoveryRestoresOneSpEveryTwentyTicksIncludingFromZero() {
        var data = AbilityData.builder().currSP(0).build();
        data.addFoodSpRecoveryTicks(40);

        for (var tick = 0; tick < 19; tick++) {
            assertFalse(data.tickFoodSpRecovery());
        }
        assertEquals(0, data.getCurrSP());

        assertTrue(data.tickFoodSpRecovery());
        assertEquals(1, data.getCurrSP());

        for (var tick = 0; tick < 20; tick++) data.tickFoodSpRecovery();
        assertEquals(2, data.getCurrSP());
        assertEquals(0, data.getFoodSpRecoveryTicks());
    }

    @Test
    void repeatedFoodExtendsDurationWithoutIncreasingRecoveryRate() {
        var data = AbilityData.builder().currSP(500).build();
        data.addFoodSpRecoveryTicks(20);
        data.addFoodSpRecoveryTicks(20);

        for (var tick = 0; tick < 20; tick++) data.tickFoodSpRecovery();

        assertEquals(501, data.getCurrSP());
        assertEquals(20, data.getFoodSpRecoveryTicks());
    }
}
