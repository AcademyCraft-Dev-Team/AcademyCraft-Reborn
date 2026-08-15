package org.academy.api.common.ability.program;

import net.minecraft.resources.Identifier;

import java.util.Objects;

/** An integral block position whose dimension cannot be lost through an implicit conversion. */
public record ProgramBlockPosition(Identifier dimension, int x, int y, int z) {
    public ProgramBlockPosition {
        Objects.requireNonNull(dimension, "dimension");
    }

    public ProgramWorldPosition center() {
        return new ProgramWorldPosition(dimension, x + 0.5, y + 0.5, z + 0.5);
    }

    public static ProgramBlockPosition containing(ProgramWorldPosition position) {
        return new ProgramBlockPosition(
                position.dimension(),
                floorToInt(position.x()),
                floorToInt(position.y()),
                floorToInt(position.z())
        );
    }

    private static int floorToInt(double value) {
        var floored = Math.floor(value);
        if (floored < Integer.MIN_VALUE || floored > Integer.MAX_VALUE) {
            throw new IllegalArgumentException("Block coordinate is outside the supported range");
        }
        return (int) floored;
    }
}
