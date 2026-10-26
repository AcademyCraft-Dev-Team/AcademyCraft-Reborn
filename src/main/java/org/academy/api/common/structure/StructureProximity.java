package org.academy.api.common.structure;

import net.minecraft.world.phys.Vec3;

import java.util.Objects;
import java.util.UUID;

/** Read-only observation of a whole loaded structure, measured in host-world coordinates. */
public record StructureProximity(StructureRef reference, UUID runtimeId, Vec3 closestPoint, double distance) {
    public StructureProximity {
        Objects.requireNonNull(reference, "reference");
        Objects.requireNonNull(runtimeId, "runtimeId");
        Objects.requireNonNull(closestPoint, "closestPoint");
        if (!Double.isFinite(distance) || distance < 0
                || !Double.isFinite(closestPoint.x) || !Double.isFinite(closestPoint.y)
                || !Double.isFinite(closestPoint.z)) throw new IllegalArgumentException("Invalid proximity");
    }
}
