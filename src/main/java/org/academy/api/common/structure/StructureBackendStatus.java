package org.academy.api.common.structure;

import java.util.Objects;

/** Server-local compatibility state. Inspection never migrates, deletes or captures content. */
public record StructureBackendStatus(Mode mode, boolean sableQueriesAvailable,
                                     int storedNativeStructures, String diagnostic) {
    public StructureBackendStatus {
        Objects.requireNonNull(mode, "mode");
        Objects.requireNonNull(diagnostic, "diagnostic");
        if (storedNativeStructures < 0) throw new IllegalArgumentException("Negative structure count");
    }

    public enum Mode {
        /** Independent storage and explicitly opened native physics are available. */
        NATIVE,
        /** Loaded Sable structures support queries/control; capture and materialization are separate capabilities. */
        SABLE,
        /** Saved native structures are retained without activation while Sable is installed. */
        MIGRATION_REQUIRED,
        /** Sable is installed but does not match the tested bridge, so native activation is also disabled. */
        INCOMPATIBLE
    }
}
