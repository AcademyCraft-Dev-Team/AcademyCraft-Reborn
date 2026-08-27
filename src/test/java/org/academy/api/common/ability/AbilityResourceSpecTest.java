package org.academy.api.common.ability;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

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
    void fixedCapacityDoesNotDependOnMaximumCp() {
        var spec = AbilityResourceSpec.fixed(128);
        assertEquals(128, spec.capacity(0.0f));
        assertEquals(128, spec.capacity(10_000.0f));
        assertTrue(spec.hasFixedCapacity());
    }

    @Test
    void rejectsInvalidContracts() {
        assertThrows(IllegalArgumentException.class,
                () -> new AbilityResourceSpec(-0.1f, 2.0f));
        assertThrows(IllegalArgumentException.class,
                () -> new AbilityResourceSpec(0.2f, 0.0f));
        assertThrows(IllegalArgumentException.class,
                () -> AbilityResourceSpec.fixed(-1));
    }
}
