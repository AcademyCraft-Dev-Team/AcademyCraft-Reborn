package org.academy.api.common.structure;

import net.minecraft.world.phys.Vec3;

import java.util.Objects;

/**
 * A point attached to a local cell. The offset may extend outside the cell for surface-mounted
 * effects. Resolving identity, cell existence and an optional generation remains the caller's job.
 */
public record StructureAnchor(
        SpaceBlockRef block,
        Vec3 offset,
        Vec3 localNormal,
        long blockGeneration
) {
    public static final long UNBOUND_GENERATION = -1L;

    public StructureAnchor {
        Objects.requireNonNull(block, "block");
        offset = StructurePose.requireFinite(offset, "offset");
        StructurePose.requireFinite(localNormal, "localNormal");
        var scale = Math.max(Math.abs(localNormal.x),
                Math.max(Math.abs(localNormal.y), Math.abs(localNormal.z)));
        if (scale == 0.0) throw new IllegalArgumentException("localNormal cannot be zero");
        var scaledNormal = new Vec3(
                localNormal.x / scale, localNormal.y / scale, localNormal.z / scale);
        localNormal = scaledNormal.scale(1.0 / scaledNormal.length());
        if (blockGeneration < UNBOUND_GENERATION) {
            throw new IllegalArgumentException("blockGeneration must be non-negative or unbound");
        }
    }

    public StructureAnchor(SpaceBlockRef block, Vec3 offset, Vec3 localNormal) {
        this(block, offset, localNormal, UNBOUND_GENERATION);
    }

    public Vec3 localPoint() {
        return Vec3.atLowerCornerOf(block.localPosition()).add(offset);
    }

    public Vec3 worldPoint(StructurePose pose) {
        return Objects.requireNonNull(pose, "pose").localToWorldPoint(localPoint());
    }

    public Vec3 worldNormal(StructurePose pose) {
        return Objects.requireNonNull(pose, "pose").localToWorldDirection(localNormal);
    }
}
