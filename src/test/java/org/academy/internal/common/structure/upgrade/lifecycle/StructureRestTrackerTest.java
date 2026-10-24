package org.academy.internal.common.structure.upgrade.lifecycle;

import net.minecraft.world.phys.Vec3;
import org.academy.api.common.structure.StructurePose;
import org.academy.internal.common.structure.upgrade.control.OrientationTargets;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class StructureRestTrackerTest {
    private static final UUID RUNTIME = UUID.randomUUID();
    private static StructureRestTracker.Observation sample(StructurePose pose, Vec3 velocity, boolean ready, boolean controlled) {
        return new StructureRestTracker.Observation(RUNTIME, 1, pose, velocity, Vec3.ZERO, ready, controlled);
    }
    private static StructureRestTracker.Observation resting() {
        return sample(StructurePose.identity(Vec3.ZERO), Vec3.ZERO, true, false);
    }
    private static void beforeThreshold(StructureRestTracker tracker) {
        for (int tick = 0; tick < 100; tick += 20) assertFalse(tracker.observe(resting(), tick, 100, 40));
    }

    @Test void requiresFullRestPeriodAndDoesNotCountAnUnobservedPast() {
        var tracker = new StructureRestTracker();
        beforeThreshold(tracker);
        assertTrue(tracker.observe(resting(), 100, 100, 40));
        var fresh = new StructureRestTracker();
        assertFalse(fresh.observe(resting(), 100_000, 100, 40));
    }

    @Test void pausedZeroVelocityNeverLooksLikeRest() {
        var tracker = new StructureRestTracker();
        beforeThreshold(tracker);
        assertFalse(tracker.observe(sample(resting().pose(), Vec3.ZERO, false, false), 100, 100, 40));
        assertFalse(tracker.observe(resting(), 120, 100, 40));
    }

    @Test void controlOrMotionRestartsTimerInsteadOfAccumulatingSeparateRests() {
        for (var interruption : new StructureRestTracker.Observation[]{
                sample(resting().pose(), Vec3.ZERO, true, true),
                sample(resting().pose(), new Vec3(.1, 0, 0), true, false)}) {
            var tracker = new StructureRestTracker();
            beforeThreshold(tracker);
            assertFalse(tracker.observe(interruption, 100, 100, 40));
            for (int tick = 120; tick < 220; tick += 20) assertFalse(tracker.observe(resting(), tick, 100, 40));
            assertTrue(tracker.observe(resting(), 220, 100, 40));
        }
    }

    @Test void cumulativeTranslationAndRotationCannotSneakBelowPerSampleTolerance() {
        for (boolean rotate : new boolean[]{false, true}) {
            var tracker = new StructureRestTracker();
            for (int i = 0; i < 10; i++) {
                var pose = rotate ? OrientationTargets.axisAngle(new Vec3(0, 1, 0), i * .01)
                        : StructurePose.identity(new Vec3(i * .01, 0, 0));
                assertFalse(tracker.observe(sample(pose, Vec3.ZERO, true, false), i * 20, 100, 40));
            }
        }
    }

    @Test void reloadContentChangeLongGapAndTimeReversalInvalidatePreviousRest() {
        var same = resting();
        for (var changed : new StructureRestTracker.Observation[]{
                new StructureRestTracker.Observation(UUID.randomUUID(), 1, same.pose(), Vec3.ZERO, Vec3.ZERO, true, false),
                new StructureRestTracker.Observation(RUNTIME, 2, same.pose(), Vec3.ZERO, Vec3.ZERO, true, false)}) {
            var tracker = new StructureRestTracker();
            beforeThreshold(tracker);
            assertFalse(tracker.observe(changed, 100, 100, 40));
        }
        for (long tick : new long[]{500, 0}) {
            var tracker = new StructureRestTracker();
            beforeThreshold(tracker);
            assertFalse(tracker.observe(resting(), tick, 100, 40));
        }
    }

    @Test void transientControlBetweenSamplesAlsoResetsKnownRest() {
        var tracker = new StructureRestTracker();
        beforeThreshold(tracker);
        tracker.reset();
        assertFalse(tracker.observe(resting(), 100, 100, 40));
    }

    @Test void invalidOrFastAngularVelocityCannotQualify() {
        for (var velocity : new Vec3[]{new Vec3(Double.NaN, 0, 0), new Vec3(0, .05, 0)}) {
            assertFalse(StructureRestTracker.eligible(new StructureRestTracker.Observation(
                    RUNTIME, 1, resting().pose(), Vec3.ZERO, velocity, true, false)));
        }
    }
}
