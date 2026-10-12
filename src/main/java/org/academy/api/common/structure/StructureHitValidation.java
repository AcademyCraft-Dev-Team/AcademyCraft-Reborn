package org.academy.api.common.structure;

import java.util.Objects;
import java.util.Optional;

/** Only a valid result contains a freshly recomputed server hit. */
public record StructureHitValidation(Status status, Optional<StructureHit> currentHit) {
    public StructureHitValidation {
        Objects.requireNonNull(status, "status");
        Objects.requireNonNull(currentHit, "currentHit");
        if ((status == Status.VALID) != currentHit.isPresent()) {
            throw new IllegalArgumentException("Only VALID contains a current hit");
        }
    }

    public static StructureHitValidation valid(StructureHit currentHit) {
        return new StructureHitValidation(Status.VALID, Optional.of(currentHit));
    }

    public static StructureHitValidation failure(Status status) {
        return new StructureHitValidation(status, Optional.empty());
    }

    public boolean succeeded() {
        return status == Status.VALID;
    }

    public enum Status {
        VALID,
        STALE,
        NOT_AVAILABLE,
        NOT_VISIBLE
    }
}
