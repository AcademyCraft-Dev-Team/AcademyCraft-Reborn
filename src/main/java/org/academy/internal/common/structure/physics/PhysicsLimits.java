package org.academy.internal.common.structure.physics;

final class PhysicsLimits {
    private PhysicsLimits() {}

    static void range(double value, double minimum, double maximum, String field) {
        if (!Double.isFinite(value) || value < minimum || value > maximum) {
            throw new IllegalArgumentException("Native physics " + field + " must be in [" + minimum + ", " + maximum + "]");
        }
    }

    static void vector(PhysicsVector vector, double maximum, String field) {
        range(vector.x(), -maximum, maximum, field);
        range(vector.y(), -maximum, maximum, field);
        range(vector.z(), -maximum, maximum, field);
    }
}
