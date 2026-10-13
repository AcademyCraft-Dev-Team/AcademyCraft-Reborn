package org.academy.api.common.structure;

import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

class StructureTractionSettingsTest {
    @Test
    void supportsFiniteFixedBudgetsAndShortLeaseBoundaries() {
        assertEquals(1000, settings(32, 32, 120, 36, 1000, 3).maxForce());
        assertDoesNotThrow(() -> settings(256, 10_000, 10_000, 0, 10_000, 1));
        assertDoesNotThrow(() -> settings(16, 1, 1, 10_000, 1, 20));
    }

    @Test
    void rejectsNonFiniteOrNonPositiveBudgetValues() {
        for (double invalid : new double[]{Double.NaN, Double.NEGATIVE_INFINITY, Double.POSITIVE_INFINITY, 0, -1}) {
            assertThrows(IllegalArgumentException.class, () -> settings(invalid, 32, 120, 36, 1000, 3));
            assertThrows(IllegalArgumentException.class, () -> settings(32, invalid, 120, 36, 1000, 3));
            assertThrows(IllegalArgumentException.class, () -> settings(32, 32, invalid, 36, 1000, 3));
            assertThrows(IllegalArgumentException.class, () -> settings(32, 32, 120, 36, invalid, 3));
        }
        for (double invalid : new double[]{Double.NaN, Double.NEGATIVE_INFINITY, Double.POSITIVE_INFINITY, -1}) {
            assertThrows(IllegalArgumentException.class, () -> settings(32, 32, 120, invalid, 1000, 3));
        }
    }

    @Test
    void rejectsUnboundedRangesGainsAndLifetime() {
        assertThrows(IllegalArgumentException.class, () -> settings(Math.nextUp(256.0), 32, 120, 36, 1000, 3));
        assertThrows(IllegalArgumentException.class, () -> settings(32, Math.nextUp(10_000.0), 120, 36, 1000, 3));
        assertThrows(IllegalArgumentException.class, () -> settings(32, 32, Math.nextUp(10_000.0), 36, 1000, 3));
        assertThrows(IllegalArgumentException.class, () -> settings(32, 32, 120, Math.nextUp(10_000.0), 1000, 3));
        assertThrows(IllegalArgumentException.class, () -> settings(32, 32, 120, 36, Math.nextUp(10_000.0), 3));
        for (int invalid : new int[]{Integer.MIN_VALUE, 0, 21, Integer.MAX_VALUE}) {
            assertThrows(IllegalArgumentException.class, () -> settings(32, 32, 120, 36, 1000, invalid));
        }
    }

    @Test
    void acquisitionCannotClaimActiveWithoutACapability() {
        assertThrows(IllegalArgumentException.class,
                () -> new StructureTraction.Acquisition(StructureTraction.Status.ACTIVE, Optional.empty()));
        for (var status : StructureTraction.Status.values()) if (status != StructureTraction.Status.ACTIVE) {
            assertEquals(status, new StructureTraction.Acquisition(status, Optional.empty()).status());
        }
    }

    private static StructureTraction.Settings settings(double range, double mass, double stiffness,
                                                        double damping, double force, int ticks) {
        return new StructureTraction.Settings(range, mass, stiffness, damping, force, ticks);
    }
}
