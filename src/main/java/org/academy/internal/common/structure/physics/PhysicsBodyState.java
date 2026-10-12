package org.academy.internal.common.structure.physics;

import java.util.Objects;

/** One coherent native state read. COM and inertia are expressed in the body's local frame. */
public record PhysicsBodyState(PhysicsPose pose, PhysicsVector linearVelocity, PhysicsVector angularVelocity,
                               double mass, PhysicsVector localCenterOfMass, PhysicsVector principalInertia,
                               PhysicsQuaternion inertiaLocalFrame, boolean sleeping, BodyType type) {
    public PhysicsBodyState {
        Objects.requireNonNull(pose, "pose");
        Objects.requireNonNull(linearVelocity, "linearVelocity");
        Objects.requireNonNull(angularVelocity, "angularVelocity");
        Objects.requireNonNull(localCenterOfMass, "localCenterOfMass");
        Objects.requireNonNull(principalInertia, "principalInertia");
        Objects.requireNonNull(inertiaLocalFrame, "inertiaLocalFrame");
        Objects.requireNonNull(type, "type");
        if (!Double.isFinite(mass) || mass < 0 || principalInertia.x() < 0
                || principalInertia.y() < 0 || principalInertia.z() < 0) {
            throw new IllegalArgumentException("Native mass and inertia must be finite and nonnegative");
        }
    }
}
