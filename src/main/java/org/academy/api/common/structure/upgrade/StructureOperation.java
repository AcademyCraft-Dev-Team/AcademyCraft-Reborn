package org.academy.api.common.structure.upgrade;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import org.academy.api.common.structure.StructureRef;

import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/** Shared contracts for replaceable structure services. A receipt never implies world mutation. */
public final class StructureOperation {
    private StructureOperation() {}

    /** Host-only authority for queries or pre-capture planning before a structure exists.
     * The service still validates the actor, dimension, server thread and permissions.
     */
    public record ActorContext(ServerLevel host, Entity actor) {
        public ActorContext {
            Objects.requireNonNull(host, "host");
            Objects.requireNonNull(actor, "actor");
        }
    }

    /**
     * Runtime-only authority snapshot, not a save format. Consumers must revalidate the server
     * thread, host, actor, exact activation, permission and revision immediately before mutation.
     * Holding this value neither loads chunks nor keeps a structure alive.
     */
    public record Context(ServerLevel host, StructureRef reference, UUID runtimeId,
                          Entity actor, long expectedContentRevision) {
        public Context {
            Objects.requireNonNull(host, "host");
            Objects.requireNonNull(reference, "reference");
            Objects.requireNonNull(runtimeId, "runtimeId");
            Objects.requireNonNull(actor, "actor");
            if (expectedContentRevision < 0) throw new IllegalArgumentException("Negative revision");
        }
    }

    public enum Status {
        READY, RUNNING, COMPLETED, BLOCKED, TERRAIN_PENDING, CONFLICT, UNSUPPORTED, STALE, DENIED
    }

    /** READY is a preview; RUNNING requires a trackable operation; neither means COMPLETED. */
    public record Result<T>(Status status, String detail, Optional<UUID> operationId, Optional<T> value) {
        public Result {
            Objects.requireNonNull(status, "status");
            Objects.requireNonNull(detail, "detail");
            Objects.requireNonNull(operationId, "operationId");
            Objects.requireNonNull(value, "value");
            if (detail.isBlank() || detail.length() > 256) throw new IllegalArgumentException("Invalid detail");
            if (status == Status.RUNNING && operationId.isEmpty()) {
                throw new IllegalArgumentException("Running operation requires an identity");
            }
            if (status == Status.UNSUPPORTED && (operationId.isPresent() || value.isPresent())) {
                throw new IllegalArgumentException("Unsupported operation cannot own work or publish a value");
            }
        }
    }

    public static <T> Result<T> unsupported(String detail) {
        return new Result<>(Status.UNSUPPORTED, detail, Optional.empty(), Optional.empty());
    }
}
