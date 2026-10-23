package org.academy.internal.common.structure.upgrade.control;

import net.minecraft.world.phys.Vec3;
import org.academy.api.common.structure.StructurePose;
import org.academy.api.common.structure.upgrade.StructureOrientation;

import java.util.Objects;
import java.util.Optional;

/** Pure rotation geometry. Returned poses are observations/plans, never permission to teleport. */
public final class OrientationTargets {
    private static final double TWO_PI = 2.0 * Math.PI;
    private static final double HEADING_EPSILON = 1.0E-9;

    private OrientationTargets() {}

    /** Signed right-handed axis-angle, matching StructurePose/JOML local-to-world rotations. */
    public static StructurePose axisAngle(Vec3 axis, double radians) {
        var unit = unitAxis(axis);
        if (!Double.isFinite(radians)) throw new IllegalArgumentException("angle must be finite");
        double half = 0.5 * Math.IEEEremainder(radians, TWO_PI);
        double sin = Math.sin(half);
        return new StructurePose(Vec3.ZERO, unit.x * sin, unit.y * sin, unit.z * sin, Math.cos(half));
    }

    public static StructurePose rotationOnly(StructurePose pose) {
        Objects.requireNonNull(pose, "pose");
        return new StructurePose(Vec3.ZERO, pose.rotationX(), pose.rotationY(), pose.rotationZ(), pose.rotationW());
    }

    /** World-space shortest-arc axis-angle error from current to target, invariant under q -> -q. */
    public static Vec3 rotationError(StructurePose current, StructurePose target) {
        Objects.requireNonNull(current, "current");
        Objects.requireNonNull(target, "target");
        var error = target.rotation().mul(current.rotation().conjugate()).normalize();
        // At exactly pi either direction is equivalent; choose one deterministically.
        boolean reverse = error.w < 0.0 || error.w == 0.0 && firstNonzeroNegative(error.x, error.y, error.z);
        double sign = reverse ? -1.0 : 1.0;
        var vector = new Vec3(error.x * sign, error.y * sign, error.z * sign);
        double sin = magnitude(vector);
        if (sin == 0.0) return Vec3.ZERO;
        double angle = 2.0 * Math.atan2(sin, Math.max(0.0, Math.min(1.0, error.w * sign)));
        return vector.scale(angle / sin);
    }

    /** Changes the grid origin to keep the exact same world COM for the supplied target rotation. */
    public static StructurePose aroundCenterOfMass(StructurePose current, Vec3 localCenterOfMass,
                                                   StructurePose targetRotation) {
        Objects.requireNonNull(current, "current");
        requireFinite(localCenterOfMass, "localCenterOfMass");
        requireRotation(targetRotation);
        var worldCenter = current.localToWorldPoint(localCenterOfMass);
        var origin = worldCenter.subtract(targetRotation.localToWorldDirection(localCenterOfMass));
        return new StructurePose(origin, targetRotation.rotation());
    }

    /**
     * Endpoint prediction only, with world angular velocity and a fixed world COM. A sweep must
     * retain omega * seconds: when its angle exceeds pi, shortest endpoint interpolation would
     * examine a different path. No collision clearance is inferred here.
     */
    public static StructurePose integrateRotation(StructurePose current, Vec3 localCenterOfMass,
                                                  Vec3 worldOmega, double seconds) {
        Objects.requireNonNull(current, "current");
        requireFinite(localCenterOfMass, "localCenterOfMass");
        requireFinite(worldOmega, "worldOmega");
        if (!Double.isFinite(seconds) || seconds < 0) throw new IllegalArgumentException("invalid duration");
        double speed = magnitude(worldOmega);
        double angle = speed * seconds;
        if (!Double.isFinite(angle)) throw new IllegalArgumentException("rotation step is not representable");
        if (angle == 0.0) return current;
        var delta = axisAngle(worldOmega, angle);
        var next = new StructurePose(Vec3.ZERO, delta.rotation().mul(current.rotation()));
        return aroundCenterOfMass(current, localCenterOfMass, next);
    }

    public static Vec3 worldSpin(StructurePose current, Vec3 axis, StructureOrientation.AxisFrame frame,
                                 double angularSpeed) {
        Objects.requireNonNull(current, "current");
        Objects.requireNonNull(frame, "frame");
        if (!Double.isFinite(angularSpeed)) throw new IllegalArgumentException("speed must be finite");
        var unit = unitAxis(axis);
        if (frame == StructureOrientation.AxisFrame.LOCAL) unit = current.localToWorldDirection(unit);
        return requireFinite(unit.scale(angularSpeed), "world spin");
    }

    /** Server adapters supply trusted baselines; current tilt is never used as a capture baseline. */
    public static Optional<Resolved> reset(Baselines baselines, StructurePose current,
                                           StructureOrientation.ResetMode mode,
                                           StructureOrientation.MissingBaselinePolicy missingPolicy) {
        Objects.requireNonNull(baselines, "baselines");
        Objects.requireNonNull(current, "current");
        Objects.requireNonNull(mode, "mode");
        Objects.requireNonNull(missingPolicy, "missingPolicy");
        return switch (mode) {
            case CAPTURE_BASELINE -> {
                if (baselines.captured().isPresent()) {
                    yield Optional.of(new Resolved(baselines.captured().orElseThrow(),
                            StructureOrientation.BaselineSource.CAPTURED_BLOCK_FRAME));
                }
                if (baselines.backendLocalGrid().isPresent()) {
                    yield Optional.of(new Resolved(baselines.backendLocalGrid().orElseThrow(),
                            StructureOrientation.BaselineSource.BACKEND_LOCAL_GRID));
                }
                yield missingPolicy == StructureOrientation.MissingBaselinePolicy.USE_WORLD_GRID
                        ? Optional.of(new Resolved(StructurePose.identity(Vec3.ZERO),
                        StructureOrientation.BaselineSource.WORLD_GRID)) : Optional.empty();
            }
            case WORLD_UP_KEEP_YAW -> Optional.of(new Resolved(uprightHeading(current),
                    StructureOrientation.BaselineSource.WORLD_UP));
            case NEAREST_GRID_YAW -> Optional.of(new Resolved(nearestGridYaw(current),
                    StructureOrientation.BaselineSource.WORLD_GRID));
        };
    }

    public record Baselines(Optional<StructurePose> captured, Optional<StructurePose> backendLocalGrid) {
        public Baselines {
            Objects.requireNonNull(captured, "captured").ifPresent(OrientationTargets::requireRotation);
            Objects.requireNonNull(backendLocalGrid, "backendLocalGrid").ifPresent(OrientationTargets::requireRotation);
        }
    }

    public record Resolved(StructurePose rotation, StructureOrientation.BaselineSource source) {
        public Resolved {
            requireRotation(rotation);
            Objects.requireNonNull(source, "source");
            if (source == StructureOrientation.BaselineSource.NOT_APPLICABLE) {
                throw new IllegalArgumentException("resolved reset requires a baseline source");
            }
        }
    }

    private static StructurePose uprightHeading(StructurePose current) {
        var forward = current.localToWorldDirection(new Vec3(0, 0, 1));
        double yaw;
        if (Math.hypot(forward.x, forward.z) > HEADING_EPSILON) {
            yaw = Math.atan2(forward.x, forward.z);
        } else {
            // Looking exactly up/down has no projected forward heading; the local right axis
            // supplies the same yaw convention without an arbitrary discontinuous reset to zero.
            var right = current.localToWorldDirection(new Vec3(1, 0, 0));
            yaw = Math.atan2(-right.z, right.x);
        }
        return axisAngle(new Vec3(0, 1, 0), yaw);
    }

    private static StructurePose nearestGridYaw(StructurePose current) {
        var best = StructurePose.identity(Vec3.ZERO);
        double bestError = magnitude(rotationError(current, best));
        for (int quarter = 1; quarter < 4; quarter++) {
            var candidate = axisAngle(new Vec3(0, 1, 0), quarter * Math.PI / 2.0);
            double error = magnitude(rotationError(current, candidate));
            if (error < bestError - 1.0E-12) {
                best = candidate;
                bestError = error;
            }
        }
        return best;
    }

    static void requireRotation(StructurePose pose) {
        Objects.requireNonNull(pose, "rotation");
        if (pose.worldOrigin().x != 0.0 || pose.worldOrigin().y != 0.0 || pose.worldOrigin().z != 0.0) {
            throw new IllegalArgumentException("rotation must not contain a translation");
        }
    }

    static Vec3 requireFinite(Vec3 vector, String name) {
        Objects.requireNonNull(vector, name);
        if (!Double.isFinite(vector.x) || !Double.isFinite(vector.y) || !Double.isFinite(vector.z)) {
            throw new IllegalArgumentException(name + " must be finite");
        }
        return vector;
    }

    static double magnitude(Vec3 vector) {
        double result = Math.hypot(Math.hypot(vector.x, vector.y), vector.z);
        if (!Double.isFinite(result)) throw new IllegalArgumentException("vector magnitude is not representable");
        return result;
    }

    private static Vec3 unitAxis(Vec3 axis) {
        requireFinite(axis, "axis");
        double scale = Math.max(Math.max(Math.abs(axis.x), Math.abs(axis.y)), Math.abs(axis.z));
        if (scale == 0.0) throw new IllegalArgumentException("axis must be nonzero");
        var scaled = new Vec3(axis.x / scale, axis.y / scale, axis.z / scale);
        return scaled.scale(1.0 / magnitude(scaled));
    }

    private static boolean firstNonzeroNegative(double x, double y, double z) {
        return x != 0.0 ? x < 0.0 : y != 0.0 ? y < 0.0 : z < 0.0;
    }
}
