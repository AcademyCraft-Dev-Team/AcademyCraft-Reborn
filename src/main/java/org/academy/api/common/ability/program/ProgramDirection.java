package org.academy.api.common.ability.program;

/**
 * A normalized non-zero direction. Distance is always supplied separately.
 */
public record ProgramDirection(double x, double y, double z) {
    private static final double MIN_LENGTH_SQUARED = 1.0E-12;

    public ProgramDirection {
        if (!Double.isFinite(x) || !Double.isFinite(y) || !Double.isFinite(z)) {
            throw new IllegalArgumentException("Direction components must be finite");
        }
        var lengthSquared = x * x + y * y + z * z;
        if (lengthSquared < MIN_LENGTH_SQUARED) {
            throw new IllegalArgumentException("Direction cannot be a zero vector");
        }
        var inverseLength = 1.0 / Math.sqrt(lengthSquared);
        x *= inverseLength;
        y *= inverseLength;
        z *= inverseLength;
    }

    public ProgramDirection opposite() {
        return new ProgramDirection(-x, -y, -z);
    }

    public double dot(ProgramDirection other) {
        return x * other.x + y * other.y + z * other.z;
    }

    public static ProgramDirection between(
            ProgramWorldPosition from,
            ProgramWorldPosition to
    ) {
        if (!from.dimension().equals(to.dimension())) {
            throw new IllegalArgumentException("Cannot derive a direction across dimensions");
        }
        return new ProgramDirection(to.x() - from.x(), to.y() - from.y(), to.z() - from.z());
    }
}
