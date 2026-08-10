package org.academy.internal.common.ability.accelerator.skills.lv2;

import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class VectorAccelTest {
    @Test
    void serverChargeRatioIsBounded() {
        assertEquals(0.0f, VectorAccel.Server.getChargeRatio(100, 90), 0.0001f);
        assertEquals(0.5f, VectorAccel.Server.getChargeRatio(100, 120), 0.0001f);
        assertEquals(0.5125f, VectorAccel.Server.getChargeRatio(100.25, 120.75), 0.0001f);
        assertEquals(1.0f, VectorAccel.Server.getChargeRatio(100, 200), 0.0001f);
    }

    @Test
    void referenceSpeedCurveIsBoundedBySeven() {
        assertEquals(
                Mth.sin(0.4f) * VectorAccel.Server.MAX_VELOCITY_SCALAR,
                VectorAccel.Server.getSpeed(-1.0f),
                0.0001
        );
        assertEquals(
                Mth.sin(1.0f) * VectorAccel.Server.MAX_VELOCITY_SCALAR,
                VectorAccel.Server.getSpeed(2.0f),
                0.0001
        );
    }

    @Test
    void dashDirectionUsesTheSameNormalizationAndDownwardLimit() {
        var horizontal = VectorAccel.Server.normalizeDashDirection(new Vec3(4.0, 0.0, 0.0));
        assertEquals(1.0, horizontal.x, 0.0001);
        assertEquals(0.0, horizontal.y, 0.0001);

        var downward = VectorAccel.Server.normalizeDashDirection(new Vec3(0.0, -0.8, 0.6));
        assertEquals(-0.5 / Math.sqrt(0.61), downward.y, 0.0001);
        assertEquals(0.6 / Math.sqrt(0.61), downward.z, 0.0001);

        assertEquals(Vec3.ZERO, VectorAccel.Server.normalizeDashDirection(new Vec3(Double.NaN, 0.0, 0.0)));
    }
}
