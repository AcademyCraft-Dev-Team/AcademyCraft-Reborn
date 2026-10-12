package org.academy.internal.common.structure.physics;

import java.util.Objects;

/** Public positions are in world coordinates; the native ABI only receives origin-relative values. */
public record PhysicsPose(PhysicsVector position, PhysicsQuaternion rotation) {
    public static final PhysicsPose IDENTITY = new PhysicsPose(PhysicsVector.ZERO, PhysicsQuaternion.IDENTITY);

    public PhysicsPose {
        Objects.requireNonNull(position, "position");
        Objects.requireNonNull(rotation, "rotation");
    }

    double[] relativeArray(PhysicsVector origin) {
        var relative = position.subtract(origin);
        return new double[]{relative.x(), relative.y(), relative.z(), rotation.x(), rotation.y(), rotation.z(), rotation.w()};
    }
}
