package org.academy.api.common.entitycontrol;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;

import java.util.Objects;

/**
 * Immutable, dimension-bound work area used by autonomous entity-control tasks.
 */
public record BlockWorkRegion(
        Identifier dimension,
        BlockPos minimum,
        BlockPos maximum
) {
    public static final int MAX_AXIS_LENGTH = 32;
    public static final int MAX_VOLUME = 4096;

    public BlockWorkRegion {
        Objects.requireNonNull(dimension, "dimension");
        Objects.requireNonNull(minimum, "minimum");
        Objects.requireNonNull(maximum, "maximum");
        var min = new BlockPos(
                Math.min(minimum.getX(), maximum.getX()),
                Math.min(minimum.getY(), maximum.getY()),
                Math.min(minimum.getZ(), maximum.getZ())
        );
        var max = new BlockPos(
                Math.max(minimum.getX(), maximum.getX()),
                Math.max(minimum.getY(), maximum.getY()),
                Math.max(minimum.getZ(), maximum.getZ())
        );
        minimum = min;
        maximum = max;
        var sizeX = max.getX() - min.getX() + 1;
        var sizeY = max.getY() - min.getY() + 1;
        var sizeZ = max.getZ() - min.getZ() + 1;
        if (sizeX > MAX_AXIS_LENGTH || sizeY > MAX_AXIS_LENGTH
                || sizeZ > MAX_AXIS_LENGTH || (long) sizeX * sizeY * sizeZ > MAX_VOLUME) {
            throw new IllegalArgumentException("Block work region exceeds its safety limit");
        }
    }

    public static BlockWorkRegion centered(
            Identifier dimension,
            BlockPos center,
            int sizeX,
            int sizeY,
            int sizeZ
    ) {
        Objects.requireNonNull(center, "center");
        validateSize(sizeX, sizeY, sizeZ);
        var minimum = center.offset(
                -Math.floorDiv(sizeX - 1, 2),
                -Math.floorDiv(sizeY - 1, 2),
                -Math.floorDiv(sizeZ - 1, 2)
        );
        return new BlockWorkRegion(
                dimension,
                minimum,
                minimum.offset(sizeX - 1, sizeY - 1, sizeZ - 1)
        );
    }

    private static void validateSize(int sizeX, int sizeY, int sizeZ) {
        if (sizeX < 1 || sizeY < 1 || sizeZ < 1
                || sizeX > MAX_AXIS_LENGTH || sizeY > MAX_AXIS_LENGTH
                || sizeZ > MAX_AXIS_LENGTH
                || (long) sizeX * sizeY * sizeZ > MAX_VOLUME) {
            throw new IllegalArgumentException("Block work dimensions exceed their safety limit");
        }
    }

    public int sizeX() {
        return maximum.getX() - minimum.getX() + 1;
    }

    public int sizeY() {
        return maximum.getY() - minimum.getY() + 1;
    }

    public int sizeZ() {
        return maximum.getZ() - minimum.getZ() + 1;
    }

    public int volume() {
        return sizeX() * sizeY() * sizeZ();
    }

    public BlockPos center() {
        return new BlockPos(
                (minimum.getX() + maximum.getX()) >> 1,
                (minimum.getY() + maximum.getY()) >> 1,
                (minimum.getZ() + maximum.getZ()) >> 1
        );
    }
}
