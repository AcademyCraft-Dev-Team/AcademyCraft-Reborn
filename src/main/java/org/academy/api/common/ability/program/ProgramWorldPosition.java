package org.academy.api.common.ability.program;

import net.minecraft.resources.Identifier;

import java.util.Objects;

/** A precise position whose dimension is part of its type-safe runtime value. */
public record ProgramWorldPosition(Identifier dimension, double x, double y, double z) {
    public ProgramWorldPosition {
        Objects.requireNonNull(dimension, "dimension");
        requireFinite(x, "x");
        requireFinite(y, "y");
        requireFinite(z, "z");
    }

    public ProgramWorldPosition offset(ProgramDirection direction, double distance) {
        requireFinite(distance, "distance");
        return new ProgramWorldPosition(
                dimension,
                x + direction.x() * distance,
                y + direction.y() * distance,
                z + direction.z() * distance
        );
    }

    public double distanceTo(ProgramWorldPosition other) {
        if (!dimension.equals(other.dimension)) {
            throw new IllegalArgumentException("Cannot measure distance across dimensions");
        }
        var deltaX = other.x - x;
        var deltaY = other.y - y;
        var deltaZ = other.z - z;
        return Math.sqrt(deltaX * deltaX + deltaY * deltaY + deltaZ * deltaZ);
    }

    private static void requireFinite(double value, String component) {
        if (!Double.isFinite(value)) {
            throw new IllegalArgumentException("World-position " + component + " must be finite");
        }
    }
}
