package org.academy.internal.common.structure.upgrade.control;

import net.minecraft.world.phys.Vec3;
import org.academy.api.common.structure.StructurePose;
import org.academy.api.common.structure.upgrade.StructureOrientation;

import java.util.Objects;

/** Pure bounded angular servo. It never writes a pose, changes COM velocity, or asserts collision clearance. */
public final class OrientationController {
    private static final double POSITION_GAIN = 6.0;
    public static final double MAX_SUBSTEP_SECONDS = 0.05;

    private OrientationController() {}

    /**
     * maximumInertia must conservatively bound the live world's inertia tensor's largest eigenvalue,
     * including parallel-axis terms for a welded assembly. It is not mass or the inertia of one part.
     */
    public record State(StructurePose pose, Vec3 localCenterOfMass, Vec3 angularVelocity, double maximumInertia) {
        public State {
            Objects.requireNonNull(pose, "pose");
            OrientationTargets.requireFinite(localCenterOfMass, "localCenterOfMass");
            OrientationTargets.requireFinite(angularVelocity, "angularVelocity");
            OrientationTargets.magnitude(angularVelocity);
            if (!Double.isFinite(maximumInertia) || maximumInertia <= 0.0) {
                throw new IllegalArgumentException("maximumInertia must be finite and positive");
            }
        }
    }

    /**
     * Apply only nextAngularVelocity through an authorized adapter after the complete rotation arc
     * passes a trustworthy collision/coverage check. predictedPose is solely for that check, never
     * setPose. converged describes the actual input observation, not predicted arrival. For spin it
     * means speed tracking, not completion of its duration; RELEASE_INERTIA converges immediately.
     */
    public record Step(Vec3 nextAngularVelocity, Vec3 deltaAngularVelocity, StructurePose predictedPose,
                       double angleError, boolean converged, double maximumAppliedTorque) {
        public Step {
            OrientationTargets.requireFinite(nextAngularVelocity, "nextAngularVelocity");
            OrientationTargets.requireFinite(deltaAngularVelocity, "deltaAngularVelocity");
            Objects.requireNonNull(predictedPose, "predictedPose");
            if (!Double.isFinite(angleError) || angleError < 0 || angleError > Math.PI
                    || !Double.isFinite(maximumAppliedTorque) || maximumAppliedTorque < 0) {
                throw new IllegalArgumentException("invalid angular step observation");
            }
        }
    }

    public static Step rotate(State state, StructurePose targetRotation,
                               StructureOrientation.Limits limits, double seconds) {
        validate(state, limits, seconds);
        OrientationTargets.requireRotation(targetRotation);
        var error = OrientationTargets.rotationError(state.pose(), targetRotation);
        // The norm of the scaled error axis can round just above pi for an exact half turn.
        double angle = Math.min(Math.PI, OrientationTargets.magnitude(error));
        double acceleration = maximumAcceleration(state, limits);
        double speed = angle <= limits.angleTolerance() ? 0.0 : Math.min(limits.maxAngularSpeed(),
                Math.min(POSITION_GAIN * angle, Math.sqrt(2.0 * acceleration * angle)));
        var desired = angle == 0.0 ? Vec3.ZERO : error.scale(speed / angle);
        boolean converged = angle <= limits.angleTolerance()
                && OrientationTargets.magnitude(state.angularVelocity()) <= limits.angularSpeedTolerance();
        return approach(state, desired, limits, seconds, angle, converged);
    }

    public static Step spin(State state, Vec3 axis, StructureOrientation.AxisFrame frame,
                             double angularSpeed, StructureOrientation.Limits limits, double seconds) {
        validate(state, limits, seconds);
        if (!Double.isFinite(angularSpeed) || Math.abs(angularSpeed) > limits.maxAngularSpeed()) {
            throw new IllegalArgumentException("spin speed exceeds its bounded command");
        }
        var desired = OrientationTargets.worldSpin(state.pose(), axis, frame, angularSpeed);
        boolean tracking = OrientationTargets.magnitude(desired.subtract(state.angularVelocity()))
                <= limits.angularSpeedTolerance();
        return approach(state, desired, limits, seconds, 0.0, tracking);
    }

    public static Step stop(State state, StructureOrientation.StopMode mode,
                             StructureOrientation.Limits limits, double seconds) {
        validate(state, limits, seconds);
        Objects.requireNonNull(mode, "mode");
        if (mode == StructureOrientation.StopMode.RELEASE_INERTIA) {
            return new Step(state.angularVelocity(), Vec3.ZERO,
                    OrientationTargets.integrateRotation(state.pose(), state.localCenterOfMass(),
                            state.angularVelocity(), seconds), 0.0, true, 0.0);
        }
        boolean stopped = OrientationTargets.magnitude(state.angularVelocity()) <= limits.angularSpeedTolerance();
        return approach(state, Vec3.ZERO, limits, seconds, 0.0, stopped);
    }

    private static Step approach(State state, Vec3 desired, StructureOrientation.Limits limits,
                                   double seconds, double angleError, boolean converged) {
        var delta = desired.subtract(state.angularVelocity());
        double deltaLength = OrientationTargets.magnitude(delta);
        double maximumChange = maximumAcceleration(state, limits) * seconds;
        if (deltaLength > maximumChange) delta = delta.scale(maximumChange / deltaLength);
        var next = state.angularVelocity().add(delta);
        // Measure the representable correction, not the ideal arithmetic delta. Very large
        // observed velocities may have an ULP larger than the allowed correction; do not jump it.
        var actualDelta = next.subtract(state.angularVelocity());
        double actualChange = OrientationTargets.magnitude(actualDelta);
        if (actualChange > maximumChange * (1.0 + 1.0E-12)) {
            next = state.angularVelocity();
            actualDelta = Vec3.ZERO;
            actualChange = 0.0;
        }
        double torqueBound = (actualChange / seconds) * state.maximumInertia();
        var predicted = OrientationTargets.integrateRotation(state.pose(), state.localCenterOfMass(), next, seconds);
        return new Step(next, actualDelta, predicted, angleError, converged, torqueBound);
    }

    private static double maximumAcceleration(State state, StructureOrientation.Limits limits) {
        return Math.min(limits.maxAngularAcceleration(), limits.maxTorque() / state.maximumInertia());
    }

    private static void validate(State state, StructureOrientation.Limits limits, double seconds) {
        Objects.requireNonNull(state, "state");
        Objects.requireNonNull(limits, "limits");
        if (!Double.isFinite(seconds) || seconds <= 0.0 || seconds > MAX_SUBSTEP_SECONDS) {
            throw new IllegalArgumentException("angular control substep must be in (0, 0.05] seconds");
        }
    }
}
