package org.academy.api.common.structure;

import java.util.Objects;
import java.util.UUID;

/**
 * Coordinates and content access captured on entering one scoped operation. This is neither
 * a raw storage world nor a permission grant; pose changes do not rewrite this snapshot.
 */
public record SpaceContext(StructureRef reference, UUID runtimeId, StructurePose pose,
                           StructureWorldAccess world) {
    public SpaceContext {
        Objects.requireNonNull(reference, "reference");
        Objects.requireNonNull(runtimeId, "runtimeId");
        Objects.requireNonNull(pose, "pose");
        Objects.requireNonNull(world, "world");
    }
}
