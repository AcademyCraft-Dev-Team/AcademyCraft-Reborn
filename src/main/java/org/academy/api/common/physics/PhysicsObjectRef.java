package org.academy.api.common.physics;

import java.util.Objects;
import java.util.UUID;

/** Logical identity only. Native addresses and engine handles never cross this boundary. */
public record PhysicsObjectRef(String providerId, UUID worldId, long worldEpoch,
                               UUID objectId, long generation, Kind kind) {
    public PhysicsObjectRef {
        Objects.requireNonNull(providerId, "providerId");
        Objects.requireNonNull(worldId, "worldId");
        Objects.requireNonNull(objectId, "objectId");
        Objects.requireNonNull(kind, "kind");
        if (providerId.isBlank() || worldEpoch < 1 || generation < 1) {
            throw new IllegalArgumentException("Physics identities require a provider and positive epochs");
        }
    }

    public enum Kind { RIGID, SOFT, FLUID }
}
