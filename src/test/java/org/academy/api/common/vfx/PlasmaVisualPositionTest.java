package org.academy.api.common.vfx;

import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class PlasmaVisualPositionTest {
    private static final Vec3 FOCUS = new Vec3(1200, 95, -1800);
    private static final Vec3 ORIGIN = FOCUS.add(0, -31, -6);
    private static final Vec3 TARGET = FOCUS.add(100, 0, 0);

    @Test
    void chargingAndReadyFocusUseSnapshotPositionFromFirstFrame() {
        for (float progress : new float[]{0, 0.5f, 1}) {
            var state = snapshot(progress, false, 0, 1);
            assertEquals(FOCUS, state.positionAt(0));
            assertEquals(FOCUS, state.positionAt(25));
            assertEquals(new Vec3(0, 31, 6), state.positionAt(0).subtract(ORIGIN));
        }
    }

    @Test
    void launchDelayKeepsProjectileAtTheSameFocus() {
        var state = snapshot(1, true, 4, 1);
        assertEquals(FOCUS, state.positionAt(0));
        assertEquals(FOCUS, state.positionAt(3.5f));
        assertEquals(FOCUS, state.positionAt(4));
        assertEquals(FOCUS.add(2.5, 0, 0), state.positionAt(5));
    }

    @Test
    void flightUsesTickRateAndStopsAtTarget() {
        var state = snapshot(1, true, 4, 2);
        assertEquals(FOCUS, state.positionAt(2));
        assertEquals(FOCUS.add(5, 0, 0), state.positionAt(3));
        assertEquals(TARGET, state.positionAt(100));
    }

    @Test
    void updatedSnapshotBecomesTheNewTrajectoryAnchor() {
        var updatedPosition = FOCUS.add(40, 0, 0);
        var state = new SkillVfxState.Plasma(updatedPosition, ORIGIN, TARGET, 1, 2.5f, 0, true, 1, 0);
        assertEquals(updatedPosition, state.positionAt(0));
        assertEquals(updatedPosition.add(5, 0, 0), state.positionAt(2));
    }

    @Test
    void zeroLengthFlightRemainsAtItsTarget() {
        var state = new SkillVfxState.Plasma(FOCUS, ORIGIN, FOCUS, 1, 2.5f, 0, true, 1, 0);
        assertEquals(FOCUS, state.positionAt(0));
        assertEquals(FOCUS, state.positionAt(25));
    }

    private static SkillVfxState.Plasma snapshot(float progress, boolean launched, int delay, float tickRate) {
        return new SkillVfxState.Plasma(FOCUS, ORIGIN, TARGET, progress, 2.5f, delay, launched, tickRate, 1f / 240f);
    }
}
