package org.academy.internal.common.structure.physics;

/** Double precision vector. Units at this boundary are blocks, seconds and kilograms. */
public record PhysicsVector(double x, double y, double z) {
    public static final PhysicsVector ZERO = new PhysicsVector(0, 0, 0);

    public PhysicsVector {
        if (!Double.isFinite(x) || !Double.isFinite(y) || !Double.isFinite(z)) {
            throw new IllegalArgumentException("Physics vectors must be finite");
        }
    }

    public PhysicsVector add(PhysicsVector other) {
        return new PhysicsVector(x + other.x, y + other.y, z + other.z);
    }

    public PhysicsVector subtract(PhysicsVector other) {
        return new PhysicsVector(x - other.x, y - other.y, z - other.z);
    }

    double[] array() {
        return new double[]{x, y, z};
    }
}
