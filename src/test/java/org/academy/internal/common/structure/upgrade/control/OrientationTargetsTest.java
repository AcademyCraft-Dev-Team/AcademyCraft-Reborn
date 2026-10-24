package org.academy.internal.common.structure.upgrade.control;

import net.minecraft.world.phys.Vec3;
import org.academy.api.common.structure.StructurePose;
import org.academy.api.common.structure.upgrade.StructureOrientation;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

class OrientationTargetsTest {
    private static final Vec3 UP = new Vec3(0, 1, 0);

    @Test
    void signedAxisAngleAndQuaternionSignChooseTheSameShortestError() {
        var negative = OrientationTargets.axisAngle(UP, -Math.PI / 2);
        vectorEquals(new Vec3(-1, 0, 0), negative.localToWorldDirection(new Vec3(0, 0, 1)));
        var current = OrientationTargets.axisAngle(UP, Math.toRadians(179));
        var target = OrientationTargets.axisAngle(UP, Math.toRadians(-179));
        var equivalent = new StructurePose(Vec3.ZERO, -target.rotationX(), -target.rotationY(),
                -target.rotationZ(), -target.rotationW());
        var error = OrientationTargets.rotationError(current, target);
        vectorEquals(new Vec3(0, Math.toRadians(2), 0), error);
        vectorEquals(error, OrientationTargets.rotationError(current, equivalent));
    }

    @Test
    void rotatedOffCenterGridKeepsItsWorldMassCenter() {
        var current = new StructurePose(new Vec3(100, 64, -70),
                OrientationTargets.axisAngle(new Vec3(1, 2, -1), 0.7).rotation());
        var localCenter = new Vec3(7.5, 2.25, -3.5);
        var target = OrientationTargets.axisAngle(UP, Math.PI / 2);
        var rotated = OrientationTargets.aroundCenterOfMass(current, localCenter, target);
        vectorEquals(current.localToWorldPoint(localCenter), rotated.localToWorldPoint(localCenter));
        assertNotEquals(current.worldOrigin(), rotated.worldOrigin(), "Grid origin must follow the off-center rotation");
        assertEquals(0, OrientationTargets.rotationError(rotated, target).length(), 1.0E-10);
    }

    @Test
    void worldAndLocalSpinUseDifferentAxesAndIntegrationPremultipliesWorldRotation() {
        var current = OrientationTargets.axisAngle(UP, Math.PI / 2);
        var axis = new Vec3(1, 0, 0);
        vectorEquals(new Vec3(2, 0, 0), OrientationTargets.worldSpin(current, axis,
                StructureOrientation.AxisFrame.WORLD, 2));
        vectorEquals(new Vec3(0, 0, -2), OrientationTargets.worldSpin(current, axis,
                StructureOrientation.AxisFrame.LOCAL, 2));
        vectorEquals(new Vec3(0, 0, 2), OrientationTargets.worldSpin(current, axis,
                StructureOrientation.AxisFrame.LOCAL, -2));
        var predicted = OrientationTargets.integrateRotation(current, new Vec3(3, 2, 1),
                new Vec3(0, 0, Math.PI / 2), 1);
        var expected = new StructurePose(Vec3.ZERO,
                OrientationTargets.axisAngle(new Vec3(0, 0, 1), Math.PI / 2).rotation().mul(current.rotation()));
        assertEquals(0, OrientationTargets.rotationError(predicted, expected).length(), 1.0E-10);
        vectorEquals(current.localToWorldPoint(new Vec3(3, 2, 1)), predicted.localToWorldPoint(new Vec3(3, 2, 1)));
    }

    @Test
    void resetNeverTreatsCurrentTiltAsAnInventedCaptureBaseline() {
        var current = OrientationTargets.axisAngle(new Vec3(0, 0, 1), 0.6);
        var absent = new OrientationTargets.Baselines(Optional.empty(), Optional.empty());
        assertTrue(OrientationTargets.reset(absent, current, StructureOrientation.ResetMode.CAPTURE_BASELINE,
                StructureOrientation.MissingBaselinePolicy.REJECT).isEmpty());
        var fallback = OrientationTargets.reset(absent, current, StructureOrientation.ResetMode.CAPTURE_BASELINE,
                StructureOrientation.MissingBaselinePolicy.USE_WORLD_GRID).orElseThrow();
        assertEquals(StructurePose.identity(Vec3.ZERO), fallback.rotation());
        assertEquals(StructureOrientation.BaselineSource.WORLD_GRID, fallback.source());
        var captured = OrientationTargets.axisAngle(UP, 0.4);
        var backend = OrientationTargets.axisAngle(UP, 0.8);
        var resolved = OrientationTargets.reset(new OrientationTargets.Baselines(Optional.of(captured),
                        Optional.of(backend)), current, StructureOrientation.ResetMode.CAPTURE_BASELINE,
                StructureOrientation.MissingBaselinePolicy.REJECT).orElseThrow();
        assertEquals(captured, resolved.rotation());
        assertEquals(StructureOrientation.BaselineSource.CAPTURED_BLOCK_FRAME, resolved.source());
    }

    @Test
    void uprightResetPreservesHeadingAtVerticalPitchAndGridTiesAreDeterministic() {
        var heading = OrientationTargets.axisAngle(UP, 0.7);
        var vertical = new StructurePose(Vec3.ZERO, heading.rotation()
                .mul(OrientationTargets.axisAngle(new Vec3(1, 0, 0), Math.PI / 2).rotation()));
        var absent = new OrientationTargets.Baselines(Optional.empty(), Optional.empty());
        var upright = OrientationTargets.reset(absent, vertical, StructureOrientation.ResetMode.WORLD_UP_KEEP_YAW,
                StructureOrientation.MissingBaselinePolicy.REJECT).orElseThrow().rotation();
        vectorEquals(UP, upright.localToWorldDirection(UP));
        assertEquals(0, OrientationTargets.rotationError(upright, heading).length(), 1.0E-10);
        var tie = OrientationTargets.reset(absent, OrientationTargets.axisAngle(UP, Math.PI / 4),
                StructureOrientation.ResetMode.NEAREST_GRID_YAW,
                StructureOrientation.MissingBaselinePolicy.REJECT).orElseThrow();
        assertEquals(StructurePose.identity(Vec3.ZERO), tie.rotation());
        var nearest = OrientationTargets.reset(absent, OrientationTargets.axisAngle(UP, Math.toRadians(80)),
                StructureOrientation.ResetMode.NEAREST_GRID_YAW,
                StructureOrientation.MissingBaselinePolicy.REJECT).orElseThrow();
        assertEquals(0, OrientationTargets.rotationError(nearest.rotation(),
                OrientationTargets.axisAngle(UP, Math.PI / 2)).length(), 1.0E-10);
    }

    @Test
    void finiteHugeAxesNormalizeWithoutOverflowAndInvalidTargetsFailBeforeIntegration() {
        var huge = OrientationTargets.axisAngle(new Vec3(Double.MAX_VALUE, 0, 0), 0.5);
        var unit = OrientationTargets.axisAngle(new Vec3(1, 0, 0), 0.5);
        assertEquals(huge, unit);
        assertThrows(IllegalArgumentException.class, () -> OrientationTargets.axisAngle(Vec3.ZERO, 1));
        assertThrows(IllegalArgumentException.class, () -> OrientationTargets.axisAngle(UP, Double.NaN));
        assertThrows(IllegalArgumentException.class, () -> OrientationTargets.aroundCenterOfMass(
                unit, Vec3.ZERO, StructurePose.identity(new Vec3(1, 0, 0))));
    }

    private static void vectorEquals(Vec3 expected, Vec3 actual) {
        assertEquals(expected.x, actual.x, 1.0E-10);
        assertEquals(expected.y, actual.y, 1.0E-10);
        assertEquals(expected.z, actual.z, 1.0E-10);
    }
}
