package org.academy.internal.client.animation;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class GeckoPlayerModelAdapterTest {
    private static final float EPSILON = 0.0001f;

    @Test
    void leavesTheRootUnchangedWithoutRotation() {
        var compensation = GeckoPlayerModelAdapter.rootPivotCompensation(0.0f, 0.0f, 0.0f);

        assertEquals(0.0f, compensation.x, EPSILON);
        assertEquals(0.0f, compensation.y, EPSILON);
        assertEquals(0.0f, compensation.z, EPSILON);
    }

    @Test
    void rotatesTheVanillaRootAroundTheGeckoFeetPivot() {
        var compensation = GeckoPlayerModelAdapter.rootPivotCompensation(
                (float) (Math.PI / 2.0), 0.0f, 0.0f);

        assertEquals(0.0f, compensation.x, EPSILON);
        assertEquals(GeckoPlayerModelAdapter.ROOT_PIVOT_Y, compensation.y, EPSILON);
        assertEquals(-GeckoPlayerModelAdapter.ROOT_PIVOT_Y, compensation.z, EPSILON);
    }
}
