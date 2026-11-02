package org.academy.api.server.ability.vector;

import org.academy.api.server.ability.AbilityExecutionHandle;

import java.util.Objects;
import java.util.Optional;

/** APPLIED does not imply that the projectile hit anything. No result refunds or charges CP. */
public record VectorOperationResult(Status status, String reason, Optional<AbilityExecutionHandle> handle) {
    public VectorOperationResult {
        Objects.requireNonNull(status);
        Objects.requireNonNull(reason);
        Objects.requireNonNull(handle);
    }

    public boolean applied() { return status == Status.APPLIED || status == Status.ALREADY_APPLIED; }

    public enum Status { PREPARED, APPLIED, ALREADY_APPLIED, REJECTED, STALE, FAILED, PARTIAL_FAILURE }
}
