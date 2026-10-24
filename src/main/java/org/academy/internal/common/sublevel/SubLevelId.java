package org.academy.internal.common.sublevel;

import java.util.Objects;
import java.util.UUID;

/** Stable identity of one plot-backed moving structure. */
public record SubLevelId(UUID value) {
    public SubLevelId {
        Objects.requireNonNull(value, "value");
    }

    public static SubLevelId random() {
        return new SubLevelId(UUID.randomUUID());
    }
}
