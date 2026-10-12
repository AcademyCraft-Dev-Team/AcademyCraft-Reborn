package org.academy.internal.common.structure.physics;

import java.util.Objects;

/** A cuboid in the body's local frame; empty gaps between cuboids remain empty. */
public record PhysicsBox(PhysicsVector center, PhysicsQuaternion rotation, PhysicsVector halfExtents) {
    public PhysicsBox {
        Objects.requireNonNull(center, "center");
        Objects.requireNonNull(rotation, "rotation");
        Objects.requireNonNull(halfExtents, "halfExtents");
        PhysicsLimits.vector(center, 1.0E6, "box center");
        PhysicsLimits.range(halfExtents.x(), 1.0E-6, 1024, "box half extent");
        PhysicsLimits.range(halfExtents.y(), 1.0E-6, 1024, "box half extent");
        PhysicsLimits.range(halfExtents.z(), 1.0E-6, 1024, "box half extent");
    }
}
