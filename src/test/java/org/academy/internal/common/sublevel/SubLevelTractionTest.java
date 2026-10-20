package org.academy.internal.common.sublevel;

import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class SubLevelTractionTest {
    private static final AABB LIMITS = new AABB(-10, 0, -10, 10, 20, 10);
    private static final AABB BODY = new AABB(-1, 5, -1, 1, 7, 1);
    private static final Vec3 POINT = new Vec3(1, 6, 0);

    @Test
    void entireTranslatedBodyMustFitNotOnlyTheSelectedAnchor() {
        assertTrue(SubLevelTraction.fitsTarget(LIMITS, BODY, POINT, new Vec3(9, 6, 0), Vec3.ZERO));
        assertFalse(SubLevelTraction.fitsTarget(LIMITS, BODY, new Vec3(-1, 6, 0), new Vec3(9, 6, 0), Vec3.ZERO));
        assertFalse(SubLevelTraction.fitsTarget(LIMITS, BODY, POINT, new Vec3(1, 19.5, 0), Vec3.ZERO));
    }

    @Test
    void nextTickPredictionAndInvalidValuesAreRejected() {
        assertFalse(SubLevelTraction.fitsTarget(LIMITS, BODY, POINT, new Vec3(9, 6, 0), new Vec3(40, 0, 0)));
        assertFalse(SubLevelTraction.fitsTarget(LIMITS, BODY, POINT, new Vec3(Double.NaN, 6, 0), Vec3.ZERO));
        assertFalse(SubLevelTraction.fitsTarget(LIMITS, BODY.move(0, -10, 0), POINT, POINT, Vec3.ZERO));
    }
}
