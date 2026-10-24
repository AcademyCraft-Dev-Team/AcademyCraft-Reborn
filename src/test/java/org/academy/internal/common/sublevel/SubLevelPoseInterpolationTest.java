package org.academy.internal.common.sublevel;

import net.minecraft.world.phys.Vec3;
import org.academy.api.common.structure.StructurePose;
import org.joml.Quaterniond;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class SubLevelPoseInterpolationTest {
    @Test
    void matchesEntityPreviousCurrentPhaseIndependentlyOfFrameRate() {
        var interpolation = new SubLevelPoseInterpolation();
        interpolation.accept(pose(0));
        interpolation.accept(pose(2.5));
        assertEquals(pose(0), interpolation.current());
        assertEquals(pose(2.5), interpolation.advance());
        for (int frame = 0; frame < 240; frame++) interpolation.sample(frame / 240.0);
        assertEquals(pose(0), interpolation.sample(0));
        assertEquals(1.25, interpolation.sample(0.5).worldOrigin().x, 1.0E-9);
        assertEquals(interpolation.current(), interpolation.sample(1));
        // A simulation tick without a packet consumes the last movement once.
        interpolation.advance();
        assertEquals(pose(2.5), interpolation.sample(0));
        assertEquals(pose(2.5), interpolation.sample(0.5));
    }

    @Test
    void packetsBetweenFramesCoalesceWithoutMovingTheCurrentRenderInterval() {
        var interpolation = new SubLevelPoseInterpolation();
        interpolation.accept(pose(0));
        interpolation.accept(pose(10));
        interpolation.advance();
        var displayed = interpolation.sample(0.25);
        interpolation.accept(pose(20));
        interpolation.accept(pose(30));
        assertEquals(displayed, interpolation.sample(0.25));
        assertEquals(pose(10), interpolation.current());
        assertEquals(pose(30), interpolation.advance());
        assertEquals(pose(10), interpolation.sample(0));
        assertEquals(20, interpolation.sample(0.5).worldOrigin().x, 1.0E-9);
    }

    @Test
    void rotationUsesTheSameSimulationTickEndpointsAsTranslation() {
        var interpolation = new SubLevelPoseInterpolation();
        var start = pose(0);
        var target = new StructurePose(new Vec3(0, 2, 0), new Quaterniond().rotateY(Math.PI));
        interpolation.accept(start);
        interpolation.accept(target);
        interpolation.advance();
        interpolation.accept(target);
        assertEquals(start.lerp(target, 0.5), interpolation.sample(0.5));
        assertEquals(target, interpolation.current());
    }

    @Test
    void initialSnapshotIsExactAndInvalidPartialTickIsRejected() {
        var interpolation = new SubLevelPoseInterpolation();
        assertThrows(IllegalStateException.class, () -> interpolation.sample(0));
        interpolation.accept(pose(42));
        assertEquals(pose(42), interpolation.current());
        assertEquals(pose(42), interpolation.sample(0));
        assertEquals(pose(42), interpolation.sample(1));
        assertThrows(IllegalArgumentException.class, () -> interpolation.sample(Double.NaN));
        assertThrows(IllegalArgumentException.class, () -> interpolation.sample(Double.POSITIVE_INFINITY));
    }

    private static StructurePose pose(double x) {
        return StructurePose.identity(new Vec3(x, 0, 0));
    }
}
