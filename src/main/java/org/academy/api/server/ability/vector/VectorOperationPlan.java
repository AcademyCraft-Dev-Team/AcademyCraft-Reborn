package org.academy.api.server.ability.vector;

import java.util.Objects;
import java.util.UUID;

/** Opaque, short-lived server plan reference. A caller-created UUID never creates a valid plan. */
public record VectorOperationPlan(UUID serverSession, UUID token) {
    public VectorOperationPlan {
        Objects.requireNonNull(serverSession);
        Objects.requireNonNull(token);
    }
}
