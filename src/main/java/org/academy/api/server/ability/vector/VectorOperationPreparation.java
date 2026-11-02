package org.academy.api.server.ability.vector;

import java.util.Objects;
import java.util.Optional;

public record VectorOperationPreparation(VectorOperationResult.Status status, String reason,
                                         Optional<VectorOperationPlan> plan) {
    public VectorOperationPreparation {
        Objects.requireNonNull(status);
        Objects.requireNonNull(reason);
        Objects.requireNonNull(plan);
    }

    public boolean prepared() { return status == VectorOperationResult.Status.PREPARED; }
}
