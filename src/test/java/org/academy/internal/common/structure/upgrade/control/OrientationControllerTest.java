package org.academy.internal.common.structure.upgrade.control;

import net.minecraft.world.phys.Vec3;
import org.academy.api.common.structure.StructurePose;
import org.academy.api.common.structure.upgrade.StructureOrientation;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class OrientationControllerTest {
    private static final Vec3 UP = new Vec3(0, 1, 0);

    @Test
    void arbitraryAxisHalfTurnRemainsWithinTheReportedAngularRange() {
        var initial = state(Vec3.ZERO, Vec3.ZERO, Vec3.ZERO, 1);
        for (var axis : new Vec3[] {new Vec3(1, 2, 3), new Vec3(-4, 7, -2), new Vec3(1, 1, 1)}) {
            var result = OrientationController.rotate(initial, OrientationTargets.axisAngle(axis, Math.PI),
                    limits(2, 4, 10), .025);
            assertEquals(Math.PI, result.angleError(), 1.0E-12);
            assertTrue(result.angleError() <= Math.PI);
        }
    }

    @Test
    void torqueLimitIncludesInertiaAndDoesNotMoveWorldMassCenter() {
        var state = state(new Vec3(5, 64, -3), new Vec3(8, 1, -2), Vec3.ZERO, 100);
        var limits = limits(2, 4, 2);
        var step = OrientationController.rotate(state, OrientationTargets.axisAngle(UP, Math.PI / 2), limits, 0.05);
        assertEquals(0.001, step.deltaAngularVelocity().length(), 1.0E-12);
        assertEquals(2.0, step.maximumAppliedTorque(), 1.0E-10);
        assertTrue(step.maximumAppliedTorque() <= limits.maxTorque() + 1.0E-10);
        assertFalse(step.converged());
        assertEquals(0, state.pose().localToWorldPoint(state.localCenterOfMass())
                .distanceTo(step.predictedPose().localToWorldPoint(state.localCenterOfMass())), 1.0E-10);
    }

    @Test
    void splittingATickDoesNotMultiplyAngularImpulseBudget() {
        var initial = state(Vec3.ZERO, new Vec3(3, 2, 1), Vec3.ZERO, 4);
        var limits = limits(2, 1, 4);
        var full = OrientationController.spin(initial, UP, StructureOrientation.AxisFrame.WORLD, 2, limits, 0.05);
        var split = initial;
        double integratedTorqueBound = 0;
        for (int index = 0; index < 4; index++) {
            var step = OrientationController.spin(split, UP, StructureOrientation.AxisFrame.WORLD, 2, limits, 0.0125);
            integratedTorqueBound += step.maximumAppliedTorque() * 0.0125;
            split = next(split, step);
        }
        assertEquals(full.nextAngularVelocity().y, split.angularVelocity().y, 1.0E-12);
        assertEquals(0.05, split.angularVelocity().length(), 1.0E-12);
        assertTrue(integratedTorqueBound <= limits.maxTorque() * 0.05 + 1.0E-10);
    }

    @Test
    void existingOverspeedBrakesWithinBudgetInsteadOfClampingInstantly() {
        var state = state(Vec3.ZERO, Vec3.ZERO, new Vec3(0, 3, 0), 1);
        var limits = limits(1, 2, 100);
        var step = OrientationController.spin(state, UP, StructureOrientation.AxisFrame.WORLD, 1, limits, 0.05);
        assertEquals(2.9, step.nextAngularVelocity().y, 1.0E-12);
        assertEquals(0.1, step.deltaAngularVelocity().length(), 1.0E-12);
        assertFalse(step.converged());
    }

    @Test
    void stopBrakesButReleasePreservesTheObservedInertia() {
        var state = state(Vec3.ZERO, new Vec3(1, 2, 3), new Vec3(0, 3, 0), 2);
        var limits = limits(1, 2, 100);
        var brake = OrientationController.stop(state, StructureOrientation.StopMode.BRAKE, limits, 0.05);
        assertEquals(2.9, brake.nextAngularVelocity().y, 1.0E-12);
        assertFalse(brake.converged());
        var release = OrientationController.stop(state, StructureOrientation.StopMode.RELEASE_INERTIA, limits, 0.05);
        assertEquals(state.angularVelocity(), release.nextAngularVelocity());
        assertEquals(Vec3.ZERO, release.deltaAngularVelocity());
        assertEquals(0, release.maximumAppliedTorque());
        assertTrue(release.converged());
    }

    @Test
    void boundedServoConvergesFromNonAxisAlignedMotionWithoutClaimingPredictedArrival() {
        var current = state(new Vec3(12, 64, -7), new Vec3(3.5, 1.25, -2),
                new Vec3(0.6, -0.2, 0.4), 5);
        var target = OrientationTargets.axisAngle(new Vec3(1, 2, -1), 1.7);
        var limits = limits(1.5, 4, 20);
        var worldCenter = current.pose().localToWorldPoint(current.localCenterOfMass());
        boolean converged = false;
        for (int index = 0; index < 1_000; index++) {
            var step = OrientationController.rotate(current, target, limits, 0.0125);
            assertTrue(step.deltaAngularVelocity().length() <= limits.maxAngularAcceleration() * 0.0125 + 1.0E-10);
            assertTrue(step.maximumAppliedTorque() <= limits.maxTorque() + 1.0E-9);
            assertEquals(0, worldCenter.distanceTo(step.predictedPose().localToWorldPoint(current.localCenterOfMass())),
                    1.0E-9);
            if (step.converged()) {
                assertTrue(OrientationTargets.rotationError(current.pose(), target).length() <= limits.angleTolerance());
                assertTrue(current.angularVelocity().length() <= limits.angularSpeedTolerance());
                converged = true;
                break;
            }
            current = next(current, step);
        }
        assertTrue(converged, "The servo must enter the tolerance region in finite time");
    }

    @Test
    void spinTrackingDoesNotReplaceRequestedSpeedWithFullTickSteps() {
        var current = state(Vec3.ZERO, Vec3.ZERO, Vec3.ZERO, 1);
        var limits = limits(2, 4, 20);
        for (int index = 0; index < 40; index++) {
            var step = OrientationController.spin(current, UP, StructureOrientation.AxisFrame.WORLD, -2, limits, 0.0125);
            assertTrue(step.nextAngularVelocity().length() <= 2 + 1.0E-12);
            current = next(current, step);
        }
        assertEquals(-2, current.angularVelocity().y, 1.0E-10);
        assertTrue(OrientationController.spin(current, UP, StructureOrientation.AxisFrame.WORLD, -2,
                limits, 0.0125).converged());
    }

    @Test
    void invalidInertiaAndSubstepDurationCannotGrantUnboundedCorrection() {
        assertThrows(IllegalArgumentException.class, () -> state(Vec3.ZERO, Vec3.ZERO, Vec3.ZERO, 0));
        var state = state(Vec3.ZERO, Vec3.ZERO, Vec3.ZERO, 1);
        for (double invalid : new double[]{0, -1, Double.NaN, Double.POSITIVE_INFINITY, 0.05001}) {
            assertThrows(IllegalArgumentException.class, () -> OrientationController.stop(state,
                    StructureOrientation.StopMode.BRAKE, limits(2, 4, 20), invalid));
        }
    }

    private static OrientationController.State state(Vec3 origin, Vec3 localCenter, Vec3 omega, double inertia) {
        return new OrientationController.State(StructurePose.identity(origin), localCenter, omega, inertia);
    }

    private static OrientationController.State next(OrientationController.State previous, OrientationController.Step step) {
        return new OrientationController.State(step.predictedPose(), previous.localCenterOfMass(),
                step.nextAngularVelocity(), previous.maximumInertia());
    }

    private static StructureOrientation.Limits limits(double speed, double acceleration, double torque) {
        return new StructureOrientation.Limits(speed, acceleration, torque, 400, 0.003, 0.02, 0, 1_000);
    }
}
