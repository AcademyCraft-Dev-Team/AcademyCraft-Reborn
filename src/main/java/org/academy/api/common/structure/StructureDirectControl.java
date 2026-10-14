package org.academy.api.common.structure;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import org.academy.internal.common.structure.StructureBackendRouter;
import org.jspecify.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * Lightweight, non-force direct-velocity control for backends that own their own solver.
 *
 * <p>A lease binds exactly one actor, one body epoch and one service control token. It never
 * applies force, never gates on mass and is never persisted. Once invalidated a token cannot
 * regain authority: acquisition revalidates the live body and refuses a foreign controller.
 * Release is idempotent and scoped to its own token, so it never terminates a successor.</p>
 */
public final class StructureDirectControl {
    private static final Map<StructureRef, Lease> LEASES = new HashMap<>();

    private StructureDirectControl() {
    }

    public enum Status {
        ACTIVE, OCCUPIED, UNAVAILABLE
    }

    /** Non-mutating backend observation; only READY carries the exact body epoch. */
    public record Probe(State state, @Nullable UUID runtimeId, String detail) {
        public Probe {
            Objects.requireNonNull(state, "state");
            detail = detail == null ? "" : detail;
        }

        public static Probe ready(UUID runtimeId) {
            return new Probe(State.READY, Objects.requireNonNull(runtimeId, "runtimeId"), "ready");
        }

        public static Probe occupied(String detail) {
            return new Probe(State.OCCUPIED, null, detail);
        }

        public static Probe unavailable(String detail) {
            return new Probe(State.UNAVAILABLE, null, detail);
        }

        public enum State {
            READY, OCCUPIED, UNAVAILABLE
        }
    }

    /** One standing token; the reference it was acquired for is the map key. */
    public record Lease(UUID actorId, UUID controlId, UUID runtimeId) {
        public Lease {
            Objects.requireNonNull(actorId, "actorId");
            Objects.requireNonNull(controlId, "controlId");
            Objects.requireNonNull(runtimeId, "runtimeId");
        }
    }

    /**
     * Acquires or renews the caller's token. A different actor's standing token is reported as
     * OCCUPIED instead of being stolen; the same actor may supersede its own token.
     */
    public static Status acquire(ServerLevel host, StructureRef reference, Entity actor, UUID controlId) {
        Objects.requireNonNull(host, "host");
        Objects.requireNonNull(reference, "reference");
        Objects.requireNonNull(actor, "actor");
        Objects.requireNonNull(controlId, "controlId");
        var probe = StructureBackendRouter.probeDirectControl(host, reference);
        synchronized (LEASES) {
            var standing = LEASES.get(reference);
            var status = decideAcquire(probe, standing, actor.getUUID(), controlId);
            if (status == Status.ACTIVE) {
                LEASES.put(reference, new Lease(actor.getUUID(), controlId, probe.runtimeId()));
            }
            return status;
        }
    }

    /** Revalidates the exact token against the live body; missing or changed state is UNAVAILABLE. */
    public static Status validate(ServerLevel host, StructureRef reference, Entity actor, UUID controlId) {
        Objects.requireNonNull(host, "host");
        Objects.requireNonNull(reference, "reference");
        Objects.requireNonNull(actor, "actor");
        Objects.requireNonNull(controlId, "controlId");
        var probe = StructureBackendRouter.probeDirectControl(host, reference);
        synchronized (LEASES) {
            var standing = LEASES.get(reference);
            var status = decideValidate(probe, standing, actor.getUUID(), controlId);
            if (status != Status.ACTIVE && status != Status.OCCUPIED) LEASES.remove(reference);
            return status;
        }
    }

    /** Idempotent and token-scoped; a foreign or stale token is a no-op. */
    public static void release(StructureRef reference, UUID controlId) {
        Objects.requireNonNull(reference, "reference");
        Objects.requireNonNull(controlId, "controlId");
        synchronized (LEASES) {
            var standing = LEASES.get(reference);
            if (standing != null && standing.controlId().equals(controlId)) LEASES.remove(reference);
        }
    }

    /** Drops every lease; called when the owning server stops. */
    public static void clearAll() {
        synchronized (LEASES) {
            LEASES.clear();
        }
    }

    static Status decideAcquire(Probe probe, @Nullable Lease standing, UUID actorId, UUID controlId) {
        if (probe.state() == Probe.State.UNAVAILABLE) return Status.UNAVAILABLE;
        if (standing != null) {
            if (!standing.actorId().equals(actorId)) return Status.OCCUPIED;
            if (probe.state() == Probe.State.OCCUPIED) return Status.OCCUPIED;
            if (standing.controlId().equals(controlId) && !standing.runtimeId().equals(probe.runtimeId())) {
                // The bound body epoch is gone; a stale token must not regain authority.
                return Status.UNAVAILABLE;
            }
        }
        if (probe.state() == Probe.State.OCCUPIED) return Status.OCCUPIED;
        return Status.ACTIVE;
    }

    static Status decideValidate(Probe probe, @Nullable Lease standing, UUID actorId, UUID controlId) {
        if (probe.state() == Probe.State.UNAVAILABLE) return Status.UNAVAILABLE;
        if (standing == null
                || !standing.actorId().equals(actorId)
                || !standing.controlId().equals(controlId)) {
            return Status.UNAVAILABLE;
        }
        if (probe.state() == Probe.State.OCCUPIED) return Status.OCCUPIED;
        if (!standing.runtimeId().equals(probe.runtimeId())) return Status.UNAVAILABLE;
        return Status.ACTIVE;
    }
}
