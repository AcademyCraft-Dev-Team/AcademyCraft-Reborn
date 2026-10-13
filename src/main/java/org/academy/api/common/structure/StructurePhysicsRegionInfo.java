package org.academy.api.common.structure;

import net.minecraft.world.phys.AABB;

import java.util.Objects;
import java.util.UUID;

/**
 * Detached description of one saved region in the host supplied to
 * {@link StructurePhysics#savedRegions}. Its identity survives normal server restarts;
 * it is not a live solver handle or permission to modify its structures.
 */
public record StructurePhysicsRegionInfo(UUID id, AABB bounds, int structures, boolean resumeAllowed) {
    public StructurePhysicsRegionInfo {
        Objects.requireNonNull(id, "id");
        StructurePhysics.requireBounds(bounds);
        if (structures < 1) throw new IllegalArgumentException("A saved region must have a structure association");
    }
}
