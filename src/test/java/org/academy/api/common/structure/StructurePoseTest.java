package org.academy.api.common.structure;

import net.minecraft.world.phys.Vec3;
import org.joml.Quaterniond;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class StructurePoseTest {
    @Test
    void legacyYawKeepsItsPivotFixedAndMatchesClockwiseGridRotation() {
        var position = new Vec3(10.5, 64.0, 20.5);
        var pivot = new Vec3(1.0, 0.0, 0.5);
        var pose = StructurePose.fromYawAroundPivot(position, 90.0, pivot);

        assertVector(position.add(pivot), pose.localToWorldPoint(pivot), 1.0e-12);
        assertVector(new Vec3(12.0, 64.0, 20.0),
                pose.localToWorldPoint(Vec3.ZERO), 1.0e-12);
        assertVector(new Vec3(12.0, 64.0, 21.0),
                pose.localToWorldPoint(new Vec3(1.0, 0.0, 0.0)), 1.0e-12);
        assertVector(new Vec3(0.0, 0.0, 2.0),
                pose.localToWorldDirection(new Vec3(2.0, 0.0, 0.0)), 1.0e-12);
    }

    @Test
    void arbitraryRotationRoundTripsAtLargeWorldCoordinates() {
        var pose = new StructurePose(new Vec3(29_000_000.25, -64.0, -29_000_000.75),
                new Quaterniond().rotationXYZ(0.7, -1.2, 2.3));
        var localPoint = new Vec3(-2.25, 8.0, 1.5);
        var direction = new Vec3(0.25, -2.0, 4.5);

        assertVector(localPoint, pose.worldToLocalPoint(pose.localToWorldPoint(localPoint)), 1.0e-8);
        assertVector(direction,
                pose.worldToLocalDirection(pose.localToWorldDirection(direction)), 1.0e-12);
        assertEquals(direction.length(), pose.localToWorldDirection(direction).length(), 1.0e-12);
    }

    @Test
    void quaternionInputAndOutputCannotMutateThePose() {
        var rotation = new Quaterniond(2.0, 0.0, 0.0, 2.0);
        var pose = new StructurePose(Vec3.ZERO, rotation);
        rotation.identity();
        pose.rotation().identity();

        assertVector(new Vec3(0.0, 0.0, 1.0),
                pose.localToWorldDirection(new Vec3(0.0, 1.0, 0.0)), 1.0e-12);
        assertEquals(1.0, pose.rotation().lengthSquared(), 1.0e-12);
    }

    @Test
    void quaternionNormalizationHandlesLargeAndSmallFiniteMagnitudes() {
        var large = new StructurePose(Vec3.ZERO,
                Double.MAX_VALUE, 0.0, 0.0, Double.MAX_VALUE);
        var small = new StructurePose(Vec3.ZERO,
                Double.MIN_VALUE, 0.0, 0.0, Double.MIN_VALUE);

        assertVector(new Vec3(0.0, 0.0, 1.0),
                large.localToWorldDirection(new Vec3(0.0, 1.0, 0.0)), 1.0e-12);
        assertVector(large.localToWorldDirection(new Vec3(1.0, 2.0, 3.0)),
                small.localToWorldDirection(new Vec3(1.0, 2.0, 3.0)), 1.0e-12);
    }

    @Test
    void surfaceVelocityUsesWorldAngularVelocityAndLocalCenterOfMass() {
        var pose = StructurePose.fromYawAroundPivot(
                new Vec3(20.0, 30.0, 40.0), 90.0, Vec3.ZERO);
        var velocity = pose.surfaceVelocity(
                new Vec3(3.0, 0.0, 0.0), new Vec3(2.0, 0.0, 0.0),
                new Vec3(1.0, 0.0, 0.0), new Vec3(0.0, 2.0, 0.0));

        assertVector(new Vec3(3.0, 0.0, 0.0), velocity, 1.0e-12);
    }

    @Test
    void invalidPosesAndVectorsFailBeforeEnteringPhysics() {
        assertThrows(IllegalArgumentException.class,
                () -> new StructurePose(Vec3.ZERO, 0.0, 0.0, 0.0, 0.0));
        assertThrows(IllegalArgumentException.class,
                () -> new StructurePose(Vec3.ZERO, Double.NaN, 0.0, 0.0, 1.0));
        assertThrows(IllegalArgumentException.class,
                () -> StructurePose.identity(new Vec3(0.0, Double.POSITIVE_INFINITY, 0.0)));
        assertThrows(IllegalArgumentException.class,
                () -> StructurePose.fromYawAroundPivot(Vec3.ZERO, Double.NaN, Vec3.ZERO));
        assertThrows(IllegalArgumentException.class,
                () -> StructurePose.identity(Vec3.ZERO)
                        .worldToLocalDirection(new Vec3(Double.NaN, 0.0, 0.0)));
        assertThrows(IllegalArgumentException.class,
                () -> StructurePose.identity(new Vec3(Double.MAX_VALUE, 0.0, 0.0))
                        .localToWorldPoint(new Vec3(Double.MAX_VALUE, 0.0, 0.0)));
    }

    private static void assertVector(Vec3 expected, Vec3 actual, double tolerance) {
        assertEquals(expected.x, actual.x, tolerance);
        assertEquals(expected.y, actual.y, tolerance);
        assertEquals(expected.z, actual.z, tolerance);
    }
}
