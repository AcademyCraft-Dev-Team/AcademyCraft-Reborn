package org.academy.internal.common.ability.accelerator.skills.lv2;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class VectorAccelTest {
    @Test
    void serverChargeRatioIsBounded() {
        assertEquals(0.0f, VectorAccel.Server.getChargeRatio(100, 90), 0.0001f);
        assertEquals(0.5f, VectorAccel.Server.getChargeRatio(100, 120), 0.0001f);
        assertEquals(1.0f, VectorAccel.Server.getChargeRatio(100, 200), 0.0001f);
    }

    @Test
    void referenceSpeedCurveIsBoundedBySeven() {
        assertEquals(
                Math.sin(0.4) * VectorAccel.Server.MAX_VELOCITY_SCALAR,
                VectorAccel.Server.getSpeed(-1.0f),
                0.0001
        );
        assertEquals(
                Math.sin(1.0) * VectorAccel.Server.MAX_VELOCITY_SCALAR,
                VectorAccel.Server.getSpeed(2.0f),
                0.0001
        );
    }
}
