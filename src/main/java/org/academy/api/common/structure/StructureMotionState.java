package org.academy.api.common.structure;

import net.minecraft.world.phys.Vec3;

import java.util.Objects;

/**
 * Immutable physics observation. Pose maps the local grid origin to the host world; it is
 * not the center of mass. Linear velocity is world-space center-of-mass blocks/second,
 * angular velocity is world-space radians/second, and the center of mass is structure-local.
 * Content revision and geometry epoch identify the collision data used by this observation;
 * they are nonnegative runtime counters, not persistent identity or permission tokens.
 */
public record StructureMotionState(StructurePose pose, Vec3 linearVelocity, Vec3 angularVelocity,
                                   Vec3 localCenterOfMass, double mass, boolean sleeping,
                                   long contentRevision, long geometryEpoch) {
    public StructureMotionState {
        Objects.requireNonNull(pose, "pose");
        requireFinite(linearVelocity, "linearVelocity");
        requireFinite(angularVelocity, "angularVelocity");
        requireFinite(localCenterOfMass, "localCenterOfMass");
        if (!Double.isFinite(mass) || mass <= 0) {
            throw new IllegalArgumentException("Structure motion mass must be finite and positive");
        }
        if (contentRevision < 0 || geometryEpoch < 0) {
            throw new IllegalArgumentException("Structure motion revisions must be nonnegative");
        }
    }

    private static void requireFinite(Vec3 value, String name) {
        Objects.requireNonNull(value, name);
        if (!Double.isFinite(value.x) || !Double.isFinite(value.y) || !Double.isFinite(value.z)) {
            throw new IllegalArgumentException(name + " must be finite");
        }
    }
}
