package org.academy.internal.server.ability;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class DevelopDataTest {
    @Test
    void cumulativeEnergyTargetsChargeTheExactNonDivisibleTotal() {
        assertEquals(0, DevelopData.targetEnergy(7, 4, 0));
        assertEquals(1, DevelopData.targetEnergy(7, 4, 1));
        assertEquals(3, DevelopData.targetEnergy(7, 4, 2));
        assertEquals(5, DevelopData.targetEnergy(7, 4, 3));
        assertEquals(7, DevelopData.targetEnergy(7, 4, 4));
        assertEquals(7, DevelopData.targetEnergy(7, 4, 20));
    }
}
