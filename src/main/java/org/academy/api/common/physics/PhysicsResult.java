package org.academy.api.common.physics;

import java.util.Objects;
import java.util.Optional;

/** A port result; merely queuing work must never be represented as completed simulation. */
public record PhysicsResult<T>(Status status, Optional<T> value, String reason) {
    public PhysicsResult {
        Objects.requireNonNull(status, "status");
        value = Objects.requireNonNull(value, "value");
        Objects.requireNonNull(reason, "reason");
        if ((status == Status.COMPLETED) != value.isPresent()) {
            throw new IllegalArgumentException("Only completed results carry a value");
        }
        if (status != Status.COMPLETED && reason.isBlank()) {
            throw new IllegalArgumentException("Rejected operations require an explicit reason");
        }
    }

    public static <T> PhysicsResult<T> completed(T value) {
        return new PhysicsResult<>(Status.COMPLETED, Optional.of(value), "");
    }

    public static <T> PhysicsResult<T> rejected(Status status, String reason) {
        if (status == Status.COMPLETED) throw new IllegalArgumentException("Use completed for success");
        return new PhysicsResult<>(status, Optional.empty(), reason);
    }

    public enum Status {
        COMPLETED, UNSUPPORTED, NOT_READY, CLOSED, STALE, INVALID, CAPACITY,
        TERRAIN_PENDING, CONFLICT, FAILED
    }

    /** Successful operations without a payload still have an unambiguous result value. */
    public enum Done { INSTANCE }
}
