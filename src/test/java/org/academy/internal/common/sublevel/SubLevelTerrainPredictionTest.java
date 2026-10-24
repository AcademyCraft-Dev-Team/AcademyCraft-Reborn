package org.academy.internal.common.sublevel;

import net.minecraft.world.phys.Vec3;
import org.academy.api.common.structure.StructurePose;
import org.joml.Quaterniond;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class SubLevelTerrainPredictionTest {
    @Test
    void predictsFirstLiftStepBeforeAnySolverMovement() {
        var start = StructurePose.identity(new Vec3(10, 70, -2));
        var next = SubLevelTerrainBreaker.projectedPose(start, new Vec3(2, 0.5, 2),
                new Vec3(0, 8, 0), Vec3.ZERO, 0.05);
        assertEquals(70.4, next.worldOrigin().y, 1.0E-12);
        assertEquals(start.rotation(), next.rotation());
    }

    @Test
    void rotatesAroundCenterOfMassAndUsesWorldSpaceAngularVelocity() {
        var localCenter = new Vec3(2, 0.5, 1);
        var start = new StructurePose(new Vec3(10, 70, -2), new Quaterniond().rotateY(0.7));
        var velocity = new Vec3(4, 2, -1);
        var next = SubLevelTerrainBreaker.projectedPose(start, localCenter, velocity,
                new Vec3(Math.PI, 0, 0), 0.05);
        var expectedCenter = start.localToWorldPoint(localCenter).add(velocity.scale(0.05));
        assertEquals(0, expectedCenter.distanceTo(next.localToWorldPoint(localCenter)), 1.0E-12);
        var expectedRotation = new Quaterniond().rotateX(Math.PI * 0.05).mul(start.rotation());
        assertEquals(1.0, Math.abs(expectedRotation.dot(next.rotation())), 1.0E-12);
    }

    @Test
    void refusesUnboundedPredictionDurations() {
        var start = StructurePose.identity(Vec3.ZERO);
        for (double duration : new double[]{0, -1, Double.NaN, Double.POSITIVE_INFINITY}) {
            assertThrows(IllegalArgumentException.class, () -> SubLevelTerrainBreaker.projectedPose(
                    start, Vec3.ZERO, Vec3.ZERO, Vec3.ZERO, duration));
        }
    }
}
