package org.academy.api.common.structure;

import net.minecraft.core.Direction;
import net.minecraft.world.phys.Vec3;

import java.util.Objects;
import java.util.UUID;

/**
 * A detached observation of a structure's selection shape. Faces use local grid axes;
 * points use the explicitly named coordinate system. This value is not an authorization
 * token and must be revalidated against a server-owned view ray before interaction.
 */
public record StructureHit(
        SpaceBlockRef block,
        UUID runtimeId,
        long geometryRevision,
        StructurePose pose,
        Direction localFace,
        Vec3 localPoint,
        Vec3 worldPoint,
        boolean inside
) {
    public StructureHit {
        Objects.requireNonNull(block, "block");
        Objects.requireNonNull(runtimeId, "runtimeId");
        Objects.requireNonNull(pose, "pose");
        Objects.requireNonNull(localFace, "localFace");
        if (geometryRevision < 0) throw new IllegalArgumentException("geometryRevision cannot be negative");
        localPoint = StructurePose.requireFinite(localPoint, "localPoint");
        worldPoint = StructurePose.requireFinite(worldPoint, "worldPoint");
    }
}
