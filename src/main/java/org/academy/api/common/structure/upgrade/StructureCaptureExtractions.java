package org.academy.api.common.structure.upgrade;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import org.academy.AcademyCraft;
import org.academy.api.common.structure.StructureCaptureRequest;
import org.academy.api.common.structure.StructureCaptureResult;
import org.academy.api.common.structure.StructureDirectControl;
import org.academy.api.common.structure.StructureRef;
import org.academy.api.common.structure.StructureTransactions;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Shared, bounded capture-to-extraction lifecycle for skills, action nodes and non-player actors.
 * Explicit soft-cell breaking precedes the preview; the preview then precedes every solid
 * source transfer, and its exact prepared request is passed to the transaction journal.
 * Once committed, failed or cancelled motion retains the structure and releases only our token.
 */
@EventBusSubscriber(modid = AcademyCraft.MOD_ID)
public final class StructureCaptureExtractions {
    public static final int EXTRACTION_TIMEOUT_TICKS = 400;
    public static final int NODE_HANDOFF_TICKS = 40;
    private static final int CAPTURE_TIMEOUT_TICKS = 400;
    private static final int MAX_SESSIONS = 128;
    private static final Map<StructureRef, Session> SESSIONS = new HashMap<>();

    private StructureCaptureExtractions() {}

    /** Shared gameplay route; explicit false is retained for diagnostic comparisons with the old controller. */
    public static boolean enabled() {
        return Boolean.parseBoolean(System.getProperty("academy.structureExtractionGameplay", "true"));
    }

    /** Identical policy for all three ability categories; values are in blocks/seconds/Newtons. */
    public static StructureExtraction.Limits limits() {
        return limits(Double.NaN);
    }

    /** Preserve the same acceleration for backend mass units; never alter the body's real mass. */
    public static StructureExtraction.Limits limits(double estimatedMass) {
        // Fresh skill captures lift over their source cells. An automatic lateral detour can
        // push a low cell into an uneven socket wall; callers of the lower-level planner may
        // explicitly authorize retreat, but these default actions must keep their X/Z anchor.
        double force = Double.isFinite(estimatedMass) && estimatedMass > 0
                ? Math.min(StructureExtraction.Limits.MAX_FORCE, Math.max(10_000, estimatedMass * 40)) : 10_000;
        return new StructureExtraction.Limits(8, 40, force, 0, StructureExtraction.MAX_SHAPE_TESTS);
    }

    public static StructureCaptureResult capture(ServerLevel host, Entity actor, StructureCaptureRequest capture,
                                                  double liftHeight, int hoverTicks) {
        requireThread(host);
        if (SESSIONS.size() >= MAX_SESSIONS) return rejected("extraction_session_budget");
        // Validate caller bounds before the explicit clearance action can mutate soft cells.
        var control = UUID.randomUUID();
        var request = new StructureExtraction.CaptureRequest(control, capture, liftHeight,
                EXTRACTION_TIMEOUT_TICKS, hoverTicks, limits());
        var prepared = StructureCaptureClearance.prepareSelection(host, actor, capture);
        if (prepared.value().isEmpty()) return rejected(prepared.detail());
        capture = prepared.value().orElseThrow();
        var backend = org.academy.internal.common.structure.StructureBackendRouter
                .captureTarget(host, capture.physical()).orElse(null);
        if (backend == null) return rejected("extraction_backend_unavailable");
        double mass = backend.estimateMass(host, capture.positions());
        if (Double.isFinite(mass) && mass > StructureExtraction.Limits.MAX_FORCE / 40) {
            return rejected("extraction_force_budget_exceeded");
        }
        request = new StructureExtraction.CaptureRequest(control, capture, liftHeight,
                EXTRACTION_TIMEOUT_TICKS, hoverTicks, limits(mass));
        var preview = StructureExtractions.service().planCapture(new StructureOperation.ActorContext(host, actor), request);
        if (preview.status() == StructureOperation.Status.BLOCKED
                && StructureCaptureClearance.clearSoftObstacles(host, actor, capture, liftHeight) > 0) {
            preview = StructureExtractions.service().planCapture(new StructureOperation.ActorContext(host, actor), request);
        }
        if (preview.status() != StructureOperation.Status.READY || preview.value().isEmpty()) {
            return rejected("extraction_preview:" + preview.status() + ":" + preview.detail());
        }
        var result = StructureTransactions.capture(host, capture);
        if ((result.pending() || result.succeeded()) && result.structure().isPresent()) {
            var reference = result.structure().orElseThrow();
            SESSIONS.put(reference, new Session(host, actor, preview.value().orElseThrow(), result));
        }
        return result;
    }

    public static boolean tracked(StructureRef reference) { return SESSIONS.containsKey(reference); }

    /** The actual immutable source grid after soft clearance, for counts, anchors and attachments. */
    public static Optional<java.util.List<net.minecraft.core.BlockPos>> sourcePositions(Entity actor, StructureRef reference) {
        var session = SESSIONS.get(reference);
        if (session == null) return Optional.empty();
        requireThread(session.host);
        return owned(actor, reference).map(ignored -> session.preview.request().capture().positions());
    }

    /** Opaque cancellation identity for this exact capture, available only to its original actor. */
    public static Optional<Handle> owned(Entity actor, StructureRef reference) {
        var session = SESSIONS.get(reference);
        return session != null && session.actor == actor && actor.level() instanceof ServerLevel current
                && current.getServer() == session.host.getServer() ? Optional.of(session.handle) : Optional.empty();
    }

    /** Runtime identity only; owning the UUID does not acquire control. */
    public static Optional<UUID> controlId(StructureRef reference) {
        var session = SESSIONS.get(reference);
        return session == null ? Optional.empty() : Optional.of(session.preview.request().controlId());
    }

    /** Polling cannot restart capture, replace a token, reset a pose or extend either time budget. */
    public static StructureOperation.Result<StructureExtraction.Snapshot> advance(
            ServerLevel host, Entity actor, StructureRef reference) {
        requireThread(host);
        var session = SESSIONS.get(reference);
        if (session == null || session.host != host || session.actor != actor) return failure("extraction_session_missing");
        if (session.failure != null) return session.failure;
        if (!actor.isAlive() || actor.isRemoved() || actor.level() != host) return fail(session, "extraction_actor_unavailable");
        if (session.context == null) {
            if (host.getGameTime() > session.preview.expiresAtGameTime()) return fail(session, "extraction_preview_expired");
            var capture = StructureTransactions.poll(host.getServer(), session.capture.transactionId()).orElse(null);
            if (capture == null) return fail(session, "capture_transaction_evicted");
            if (capture.pending()) {
                if (host.getGameTime() - session.startedAt > CAPTURE_TIMEOUT_TICKS) return fail(session, "capture_timeout");
                return new StructureOperation.Result<>(StructureOperation.Status.RUNNING, "capture_pending",
                        Optional.of(capture.transactionId()), Optional.empty());
            }
            if (!capture.succeeded()) return fail(session, "capture_failed:" + capture.detail());
            var acquired = StructureDirectControl.acquire(host, reference, actor, session.control());
            if (acquired == StructureDirectControl.Status.OCCUPIED) return fail(session, "extraction_lease_occupied");
            if (acquired != StructureDirectControl.Status.ACTIVE) return waiting(session, "extraction_body_pending");
            var context = StructureExtractions.context(host, reference, actor).orElse(null);
            if (context == null) return waiting(session, "extraction_context_pending");
            var bound = StructureExtractions.service().bindCapture(context,
                    new StructureExtraction.BindCaptureRequest(session.control(), session.preview.planId(), capture.transactionId()));
            if (bound.status() == StructureOperation.Status.TERRAIN_PENDING) return waiting(session, bound.detail());
            if (bound.status() != StructureOperation.Status.READY || bound.value().isEmpty()) {
                return fail(session, "extraction_binding:" + bound.status() + ":" + bound.detail());
            }
            var started = StructureExtractions.service().begin(context,
                    new StructureExtraction.BeginRequest(session.control(), bound.value().orElseThrow().planId()));
            if (started.status() == StructureOperation.Status.TERRAIN_PENDING) return waiting(session, started.detail());
            if (started.operationId().isEmpty() || started.value().isEmpty()) {
                return fail(session, "extraction_start:" + started.status() + ":" + started.detail());
            }
            session.context = context;
            session.operationId = started.operationId().orElseThrow();
            return started;
        }
        return StructureExtractions.service().status(session.context,
                new StructureExtraction.StatusRequest(session.control(), session.operationId));
    }

    /** A node returns its reference only after stable arrival, never merely after capture commits. */
    public static boolean arrived(StructureOperation.Result<StructureExtraction.Snapshot> result) {
        return result.value().map(value -> switch (value.phase()) {
            case HOVERING -> result.status() == StructureOperation.Status.RUNNING
                    || result.status() == StructureOperation.Status.COMPLETED;
            case RELEASED -> result.status() == StructureOperation.Status.COMPLETED
                    && result.detail().equals("extraction_hover_completed");
            default -> false;
        }).orElse(false);
    }

    public static boolean failed(StructureOperation.Result<StructureExtraction.Snapshot> result) {
        return result.status() != StructureOperation.Status.RUNNING && result.status() != StructureOperation.Status.READY
                && result.status() != StructureOperation.Status.TERRAIN_PENDING && !arrived(result);
    }

    /** Actor lifecycle cleanup also works after dimension changes, against the saved owning host. */
    public static void release(Entity actor, StructureRef reference) {
        owned(actor, reference).ifPresent(StructureCaptureExtractions::release);
    }

    /** A foreign or superseded handle cannot remove the current session or release its token. */
    public static void release(Handle handle) {
        var session = SESSIONS.get(handle.reference);
        if (session == null || session.handle != handle || !owns(session.host, session.actor.getUUID(), session.control(),
                handle.host, handle.actor.getUUID(), handle.control)) return;
        requireThread(session.host);
        if (!SESSIONS.remove(handle.reference, session)) return;
        stop(session);
    }

    static boolean owns(Object expectedHost, UUID expectedActor, UUID expectedControl,
                        Object host, UUID actor, UUID control) {
        return expectedHost != null && expectedHost == host && expectedActor.equals(actor) && expectedControl.equals(control);
    }

    public static final class Handle {
        private final ServerLevel host;
        private final Entity actor;
        private final StructureRef reference;
        private final UUID control;

        private Handle(ServerLevel host, Entity actor, StructureRef reference, UUID control) {
            this.host = host; this.actor = actor; this.reference = reference; this.control = control;
        }
    }

    private static void stop(Session session) {
        if (session.context != null && session.operationId != null) {
            StructureExtractions.service().cancel(session.context,
                    new StructureExtraction.CancelRequest(session.control(), session.operationId));
        }
        session.capture.structure().ifPresent(reference -> StructureDirectControl.release(reference, session.control()));
    }

    private static StructureOperation.Result<StructureExtraction.Snapshot> fail(Session session, String detail) {
        stop(session);
        session.failure = failure(detail);
        return session.failure;
    }

    /** Admission waits consume the original preview deadline; they never keep a foreign or stale lease. */
    private static StructureOperation.Result<StructureExtraction.Snapshot> waiting(Session session, String detail) {
        session.capture.structure().ifPresent(reference -> StructureDirectControl.release(reference, session.control()));
        return new StructureOperation.Result<>(StructureOperation.Status.TERRAIN_PENDING,
                detail, Optional.empty(), Optional.empty());
    }

    private static StructureOperation.Result<StructureExtraction.Snapshot> failure(String detail) {
        return new StructureOperation.Result<>(StructureOperation.Status.DENIED, detail, Optional.empty(), Optional.empty());
    }

    private static StructureCaptureResult rejected(String detail) {
        return StructureCaptureResult.failure(UUID.randomUUID(), StructureCaptureResult.Status.REJECTED, detail);
    }

    @SubscribeEvent
    public static void tick(ServerTickEvent.Post event) {
        for (var entry : Map.copyOf(SESSIONS).entrySet()) {
            var session = entry.getValue();
            if (session.host.getServer() != event.getServer()) continue;
            var maximum = CAPTURE_TIMEOUT_TICKS + EXTRACTION_TIMEOUT_TICKS + session.preview.request().hoverTicks() + 40;
            if (!session.actor.isAlive() || session.actor.isRemoved() || session.actor.level() != session.host
                    || session.host.getGameTime() - session.startedAt > maximum) release(session.handle);
        }
    }

    @SubscribeEvent
    public static void stopped(ServerStoppedEvent event) {
        SESSIONS.entrySet().removeIf(entry -> entry.getValue().host.getServer() == event.getServer());
    }

    private static void requireThread(ServerLevel host) {
        if (!host.getServer().isSameThread()) throw new IllegalStateException("Extraction requires owning server thread");
    }

    private static final class Session {
        final ServerLevel host;
        final Entity actor;
        final StructureExtraction.CapturePlan preview;
        final StructureCaptureResult capture;
        final long startedAt;
        final Handle handle;
        StructureOperation.Context context;
        UUID operationId;
        StructureOperation.Result<StructureExtraction.Snapshot> failure;

        Session(ServerLevel host, Entity actor, StructureExtraction.CapturePlan preview, StructureCaptureResult capture) {
            this.host = host;
            this.actor = actor;
            this.preview = preview;
            this.capture = capture;
            this.startedAt = host.getGameTime();
            this.handle = new Handle(host, actor, capture.structure().orElseThrow(), preview.request().controlId());
        }

        UUID control() { return preview.request().controlId(); }
    }
}
