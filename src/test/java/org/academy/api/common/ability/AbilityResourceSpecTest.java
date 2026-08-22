package org.academy.api.common.ability;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class AbilityResourceSpecTest {
    @Test
    void derivesCapacityAndOccupationFromCp() {
        var spec = new AbilityResourceSpec(0.20f, 2.0f);
        assertEquals(99, spec.capacity(499.99f));
        assertEquals(18.0f, spec.occupiedCp(9.0f));
        assertEquals(0, spec.capacity(Float.NaN));
        assertEquals(0.0f, spec.occupiedCp(Float.POSITIVE_INFINITY));
    }

    @Test
    void rejectsInvalidContracts() {
        assertThrows(IllegalArgumentException.class,
                () -> new AbilityResourceSpec(-0.1f, 2.0f));
        assertThrows(IllegalArgumentException.class,
                () -> new AbilityResourceSpec(0.2f, 0.0f));
    }
}
