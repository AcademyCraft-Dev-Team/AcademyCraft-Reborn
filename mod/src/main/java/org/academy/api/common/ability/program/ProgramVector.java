package org.academy.api.common.ability.program;

/**
 * A finite, magnitude-preserving three-dimensional vector.
 *
 * <p>Unlike {@link ProgramDirection}, this value may be zero and retains its length. It is the
 * stable interchange type for velocity and general vector mathematics.</p>
 */
public record ProgramVector(double x, double y, double z) {
    private static final double MIN_LENGTH_SQUARED = 1.0E-12;

    public ProgramVector {
        requireFinite(x, "x");
        requireFinite(y, "y");
        requireFinite(z, "z");
    }

    public ProgramVector add(ProgramVector other) {
        return new ProgramVector(x + other.x, y + other.y, z + other.z);
    }

    public ProgramVector subtract(ProgramVector other) {
        return new ProgramVector(x - other.x, y - other.y, z - other.z);
    }

    public ProgramVector scale(double scalar) {
        requireFinite(scalar, "scalar");
        return new ProgramVector(x * scalar, y * scalar, z * scalar);
    }

    public double dot(ProgramVector other) {
        return x * other.x + y * other.y + z * other.z;
    }

    public ProgramVector cross(ProgramVector other) {
        return new ProgramVector(
                y * other.z - z * other.y,
                z * other.x - x * other.z,
                x * other.y - y * other.x
        );
    }

    public double length() {
        return Math.sqrt(lengthSquared());
    }

    public double lengthSquared() {
        return x * x + y * y + z * z;
    }

    public ProgramDirection direction() {
        if (lengthSquared() < MIN_LENGTH_SQUARED) {
            throw new IllegalStateException("Zero vector has no direction");
        }
        return new ProgramDirection(x, y, z);
    }

    public static ProgramVector of(ProgramDirection direction) {
        return new ProgramVector(direction.x(), direction.y(), direction.z());
    }

    public static ProgramVector of(ProgramWorldPosition position) {
        return new ProgramVector(position.x(), position.y(), position.z());
    }

    private static void requireFinite(double value, String component) {
        if (!Double.isFinite(value)) {
            throw new IllegalArgumentException("Vector " + component + " must be finite");
        }
    }
}
