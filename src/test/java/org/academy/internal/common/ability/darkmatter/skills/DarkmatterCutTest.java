package org.academy.internal.common.ability.darkmatter.skills;

import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DarkmatterCutTest {
    private static final Vec3 ORIGIN = Vec3.ZERO;
    private static final Vec3 FORWARD = new Vec3(0, 0, 1);

    @Test
    void acceptsTargetsInsideForwardCone() {
        assertTrue(DarkmatterCut.Server.insideCone(ORIGIN, FORWARD,
                new Vec3(2, 0, 6), DarkmatterCut.RADIUS, DarkmatterCut.MIN_DOT));
    }

    @Test
    void rejectsTargetsBehindOrOutsideRange() {
        assertFalse(DarkmatterCut.Server.insideCone(ORIGIN, FORWARD,
                new Vec3(0, 0, -3), DarkmatterCut.RADIUS, DarkmatterCut.MIN_DOT));
        assertFalse(DarkmatterCut.Server.insideCone(ORIGIN, FORWARD,
                new Vec3(0, 0, 9), DarkmatterCut.RADIUS, DarkmatterCut.MIN_DOT));
    }
}
