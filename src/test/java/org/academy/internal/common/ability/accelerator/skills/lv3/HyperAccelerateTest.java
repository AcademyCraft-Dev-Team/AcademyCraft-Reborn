package org.academy.internal.common.ability.accelerator.skills.lv3;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class HyperAccelerateTest {
    @Test
    void serverChargeRatioAndSpeedAreBounded() {
        assertEquals(0.1f, HyperAccelerate.Server.getChargeRatio(100, 90), 0.0001f);
        assertEquals(0.5f, HyperAccelerate.Server.getChargeRatio(100, 120), 0.0001f);
        assertEquals(1.0f, HyperAccelerate.Server.getChargeRatio(100, 200), 0.0001f);
        assertEquals(1.65, HyperAccelerate.Server.getLaunchSpeed(-1.0f), 0.0001);
        assertEquals(3.0, HyperAccelerate.Server.getLaunchSpeed(2.0f), 0.0001);
    }
}
