package org.academy.api.common.ability.program;

import org.academy.api.common.structure.StructureRef;

import java.util.Objects;
import java.util.UUID;

/**
 * Runtime-only exclusive control token for one structure.
 *
 * <p>A token binds the actor, the issuing runtime and the physical structure epoch. It is never
 * persisted and never recovered from a save; a token from another runtime, invocation or epoch
 * is rejected instead of silently regaining authority.</p>
 */
public record ProgramStructureControl(
        StructureRef reference,
        UUID structureRuntimeId,
        UUID controlId,
        UUID actorId,
        UUID runtimeToken
) {
    public ProgramStructureControl {
        Objects.requireNonNull(reference, "reference");
        Objects.requireNonNull(structureRuntimeId, "structureRuntimeId");
        Objects.requireNonNull(controlId, "controlId");
        Objects.requireNonNull(actorId, "actorId");
        Objects.requireNonNull(runtimeToken, "runtimeToken");
    }
}
