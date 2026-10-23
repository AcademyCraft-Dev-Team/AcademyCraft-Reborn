package org.academy.api.common.structure;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.entity.Entity;
import org.academy.internal.common.structure.StructureBackendRouter;
import org.academy.internal.common.sublevel.SubLevelDirectControl;
import org.academy.internal.common.sublevel.SubLevelStructureRefs;
import org.jspecify.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * Exclusive control for backends that own their own solver, supporting launch and bounded force.
 *
 * <p>A lease binds exactly one actor, one body epoch and one service control token. It never
 * gates on mass and is never persisted. Force submission is explicit. Once invalidated a token cannot
 * regain authority: acquisition revalidates the live body and refuses a foreign controller.
 * Release is idempotent and scoped to its own token, so it never terminates a successor.</p>
 */
public final class StructureDirectControl {
    private static final Map<StructureRef, Lease> LEASES = new HashMap<>();
    private static final Map<StructureRef, Entity> ACTORS = new HashMap<>();

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
        if (!actor.isAlive() || actor.isRemoved() || actor.level() != host) return Status.UNAVAILABLE;
        var probe = StructureBackendRouter.probeDirectControl(host, reference);
        synchronized (LEASES) {
            var standing = LEASES.get(reference);
            var status = decideAcquire(probe, standing, actor.getUUID(), controlId);
            if (status == Status.ACTIVE) {
                if (standing != null && !standing.controlId().equals(controlId)) cancel(reference, standing.controlId());
                LEASES.put(reference, new Lease(actor.getUUID(), controlId, probe.runtimeId()));
                ACTORS.put(reference, actor);
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
        var probe = leaseProbe(host, reference);
        synchronized (LEASES) {
            var standing = LEASES.get(reference);
            var status = validateLease(LEASES, reference, probe, actor.getUUID(), controlId);
            if (!LEASES.containsKey(reference) && owns(standing, actor.getUUID(), controlId)) {
                ACTORS.remove(reference);
                cancel(reference, controlId);
            }
            return status;
        }
    }

    static Status validateLease(Map<StructureRef, Lease> leases, StructureRef reference,
                                Probe probe, UUID actorId, UUID controlId) {
        var standing = leases.get(reference);
        var status = decideValidate(probe, standing, actorId, controlId);
        if (status != Status.ACTIVE && status != Status.OCCUPIED
                && owns(standing, actorId, controlId)) leases.remove(reference, standing);
        return status;
    }

    /** Exact activation occupancy for cooperative external tool/traction arbitration. */
    public static boolean occupied(StructureRef reference, UUID runtimeId) {
        synchronized (LEASES) {
            var lease = LEASES.get(reference);
            return lease != null && lease.runtimeId().equals(runtimeId);
        }
    }

    /** Identity only, for retaining a command while its terrain is paused; not mutation permission. */
    public static boolean matches(StructureRef reference, UUID actorId, UUID controlId, UUID runtimeId) {
        synchronized (LEASES) {
            var lease = LEASES.get(reference);
            return owns(lease, actorId, controlId) && lease.runtimeId().equals(runtimeId);
        }
    }

    /** Idempotent and token-scoped; a foreign or stale token is a no-op. */
    public static void release(StructureRef reference, UUID controlId) {
        Objects.requireNonNull(reference, "reference");
        Objects.requireNonNull(controlId, "controlId");
        synchronized (LEASES) {
            var standing = LEASES.get(reference);
            if (standing != null && standing.controlId().equals(controlId)) {
                LEASES.remove(reference);
                ACTORS.remove(reference);
                cancel(reference, controlId);
            }
        }
    }

    /** Revokes dead/moved actors for every backend before local motion is applied. */
    public static void tick(MinecraftServer server) {
        synchronized (LEASES) {
            for (var entry : Map.copyOf(ACTORS).entrySet()) {
                var reference = entry.getKey();
                var actor = entry.getValue();
                if (!(actor.level() instanceof ServerLevel host) || host.getServer() != server) continue;
                var lease = LEASES.get(reference);
                if (lease == null) { ACTORS.remove(reference); continue; }
                boolean disconnected = actor instanceof net.minecraft.server.level.ServerPlayer player && player.hasDisconnected();
                if (actorLeaseEnded(actor.isAlive(), actor.isRemoved(), disconnected,
                        reference.dimension().equals(host.dimension().identifier()))) {
                    release(reference, lease.controlId());
                    continue;
                }
                boolean local = SubLevelStructureRefs.isSubLevel(reference);
                if (local || !org.academy.internal.common.structure.upgrade.control.LiveStructureOrientation.controls(host, reference)) {
                    var probe = local ? leaseProbe(host, reference) : StructureBackendRouter.probeDirectControl(host, reference);
                    if (probeRevokesLease(probe, lease.runtimeId(), local)) release(reference, lease.controlId());
                }
            }
        }
    }

    static boolean actorLeaseEnded(boolean alive, boolean removed, boolean disconnected, boolean sameDimension) {
        return !alive || removed || disconnected || !sameDimension;
    }

    /** Optional backend pause/staff ownership is not evidence of a changed runtime identity. */
    static boolean probeRevokesLease(Probe probe, UUID runtimeId, boolean local) {
        return probe.state() == Probe.State.READY ? !runtimeId.equals(probe.runtimeId()) : local;
    }

    private static void cancel(StructureRef reference, UUID controlId) {
        org.academy.internal.common.structure.upgrade.control.LiveStructureOrientation.cancel(reference, controlId);
        if (SubLevelStructureRefs.isSubLevel(reference)) SubLevelDirectControl.cancel(reference, controlId);
    }

    private static Probe leaseProbe(ServerLevel host, StructureRef reference) {
        var probe = StructureBackendRouter.probeDirectControl(host, reference);
        if (probe.state() == Probe.State.UNAVAILABLE && SubLevelStructureRefs.isSubLevel(reference)
                && org.academy.internal.common.structure.upgrade.control.LiveStructureOrientation.controls(host, reference)) {
            // Keep the exact existing lease while this body alone waits for terrain. No impulse
            // can pass the paused native body, and the angular registry still enforces timeout.
            var body = org.academy.internal.common.sublevel.SubLevelOrientationAccess.INSTANCE.observe(host, reference);
            if (body.isPresent() && !body.get().terrainReady()) return Probe.ready(body.get().runtimeId());
        }
        return probe;
    }

    /** Submits one outer tick of bounded force; the backend consumes it at physics substeps. */
    public static boolean submit(ServerLevel host, StructureRef reference, Entity actor, UUID controlId,
                                 StructurePhysicsControl control) {
        return submit(host, reference, actor, controlId, control, false);
    }

    /**
     * Submits one controlled tick. When holdOrientation is true the backend holds the pose rotation
     * observed at the first submission for the lifetime of the standing control token (at least
     * the skill's 200-tick hover window), instead of allowing contact torque to spin the structure.
     * Backends without angular authority ignore the flag rather than rejecting the command.
     */
    public static boolean submit(ServerLevel host, StructureRef reference, Entity actor, UUID controlId,
                                 StructurePhysicsControl control, boolean holdOrientation) {
        Objects.requireNonNull(control, "control");
        if (!actor.isAlive() || actor.isRemoved() || actor.level() != host
                || validate(host, reference, actor, controlId) != Status.ACTIVE) return false;
        return StructureBackendRouter.submitDirectControl(host, reference, actor, controlId, control,
                holdOrientation);
    }

    /**
     * Submits one controlled tick with an explicit source pose. The backend holds that rotation
     * for the standing control token and, when supported, normalizes the body to the source pose
     * at the first tick so a capture-adjacent disturbance cannot wedge the structure.
     */
    public static boolean submit(ServerLevel host, StructureRef reference, Entity actor, UUID controlId,
                                 StructurePhysicsControl control, StructurePose holdPose) {
        Objects.requireNonNull(control, "control");
        Objects.requireNonNull(holdPose, "holdPose");
        if (!actor.isAlive() || actor.isRemoved() || actor.level() != host
                || validate(host, reference, actor, controlId) != Status.ACTIVE) return false;
        return StructureBackendRouter.submitDirectControl(host, reference, actor, controlId, control, holdPose);
    }

    /** Drops every lease; called when the owning server stops. */
    public static void clearAll() {
        synchronized (LEASES) {
            for (var entry : LEASES.entrySet()) cancel(entry.getKey(), entry.getValue().controlId());
            LEASES.clear();
            ACTORS.clear();
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

    static boolean owns(@Nullable Lease standing, UUID actorId, UUID controlId) {
        return standing != null && standing.actorId().equals(actorId)
                && standing.controlId().equals(controlId);
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
