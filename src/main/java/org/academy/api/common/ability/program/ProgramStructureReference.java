package org.academy.api.common.ability.program;

import org.academy.api.common.structure.StructureHandle;
import org.academy.api.common.structure.StructureRef;

import java.util.Objects;
import java.util.UUID;

/** A whole physical structure in one activation. This value is neither an entity nor a control lease. */
public record ProgramStructureReference(StructureRef reference, UUID runtimeId) {
    public ProgramStructureReference {
        Objects.requireNonNull(reference, "reference");
        Objects.requireNonNull(runtimeId, "runtimeId");
    }

    /** Reloading the same saved structure cannot revive a program's old target. */
    public boolean matches(StructureHandle handle) {
        return handle != null && reference.equals(handle.reference()) && runtimeId.equals(handle.runtimeId());
    }
}
