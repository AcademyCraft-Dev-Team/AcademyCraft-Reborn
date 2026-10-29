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
import org.academy.internal.common.structure.upgrade.control.CapturePreparationPolicy;
import org.academy.internal.common.structure.upgrade.control.CaptureSoftClearance;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Shared, bounded capture-to-extraction lifecycle for skills, action nodes and non-player actors.
 * A read-only admission/path check precedes explicit soft-cell breaking. Transient source ticks
 * and unavailable terrain wait against one bounded deadline; no synthetic structure is exposed.
 * The final preview precedes every solid source transfer, and its exact prepared request is passed to the transaction journal.
 * Once committed, failed or cancelled motion retains the structure and releases only our token.
 */
@EventBusSubscriber(modid = AcademyCraft.MOD_ID)
public final class StructureCaptureExtractions {
    public static final int EXTRACTION_TIMEOUT_TICKS = 400;
    public static final int NODE_HANDOFF_TICKS = 40;
    private static final int CAPTURE_TIMEOUT_TICKS = 400;
    private static final int MAX_SESSIONS = 128;
    private static final Map<StructureRef, Session> SESSIONS = new HashMap<>();
    private static final Map<UUID, Preparation> PREPARATIONS = new HashMap<>();

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
        if (SESSIONS.size() + PREPARATIONS.values().stream().filter(value -> value.result == null).count() >= MAX_SESSIONS
                || PREPARATIONS.size() >= MAX_SESSIONS * 2) return rejected("extraction_session_budget");
        if (actor != null && PREPARATIONS.values().stream().anyMatch(value -> value.host == host && value.actor.getUUID().equals(actor.getUUID())
                && (value.result == null || value.result.pending()))) return rejected("extraction_preparation_already_pending");
        // Validate caller bounds before the explicit clearance action can mutate soft cells.
        var control = UUID.randomUUID();
        var request = new StructureExtraction.CaptureRequest(control, capture, liftHeight,
                EXTRACTION_TIMEOUT_TICKS, hoverTicks, limits());
        var preparation = new Preparation(host, actor, request);
        PREPARATIONS.put(preparation.id, preparation);
        boolean completed = false;
        boolean waiting = false;
        try {
            var result = prepare(preparation);
            waiting = result.pending() && result.structure().isEmpty() && !preparation.cancelled;
            if (waiting) preparation.facade = true;
            completed = true;
            return result;
        } finally {
            if (!completed) preparation.cancelled = true;
            if (!waiting) PREPARATIONS.remove(preparation.id, preparation);
        }
    }

    /** Preparation publishes no synthetic reference. Its operation id remains stable through commit. */
    public static Optional<StructureCaptureResult> poll(ServerLevel host, Entity actor, UUID operationId) {
        requireThread(host);
        var preparation = PREPARATIONS.get(operationId);
        if (preparation == null) return StructureTransactions.poll(host.getServer(), operationId);
        if (!preparation.owned(host, actor)) return Optional.empty();
        if (preparation.result == null) {
            try { return Optional.of(prepare(preparation)); }
            catch (RuntimeException | LinkageError failure) {
                preparation.cancelled = true;
                PREPARATIONS.remove(operationId, preparation);
                throw failure;
            }
        }
        if (preparation.result.pending()) {
            preparation.finish(StructureTransactions.poll(host.getServer(), preparation.result.transactionId())
                    .orElseGet(() -> rejected("capture_transaction_evicted")));
        }
        return Optional.of(preparation.display(preparation.result));
    }

    /** Cancels only this actor's preparation/control; a durable content transaction keeps its recovery owner. */
    public static boolean cancel(ServerLevel host, Entity actor, UUID operationId) {
        requireThread(host);
        var preparation = PREPARATIONS.get(operationId);
        if (preparation == null || !preparation.owned(host, actor)) return false;
        preparation.cancelled = true;
        if (preparation.result != null) preparation.result.structure().ifPresent(ref -> release(preparation.actor, ref));
        if (preparation.result == null || !preparation.result.pending()) PREPARATIONS.remove(operationId, preparation);
        return true;
    }

    private static StructureCaptureResult prepare(Preparation preparation) {
        var host = preparation.host;
        var actor = preparation.actor;
        if (!preparation.active()) return preparation.finish(rejected("extraction_preparation_cancelled"));
        if (actor == null || !actor.isAlive() || actor.isRemoved() || actor.level() != host
                || actor instanceof net.minecraft.server.level.ServerPlayer player && player.hasDisconnected()) {
            return preparation.finish(rejected("capture_actor_unavailable"));
        }
        long now = host.getGameTime();
        if (CapturePreparationPolicy.expired(preparation.startedAt, now)) {
            return preparation.finish(StructureCaptureResult.failure(preparation.id, StructureCaptureResult.Status.TIMEOUT,
                    "extraction_preparation_timeout:" + preparation.detail));
        }
        if (!CapturePreparationPolicy.mayAttempt(preparation.lastAttempt, now)) return preparation.waiting(preparation.detail);
        preparation.lastAttempt = now;
        var capture = preparation.request.capture();
        CaptureSoftClearance.Selection selection = preparation.selection;
        if (selection == null) {
            var inspected = CaptureSoftClearance.inspectSelection(host, actor, capture);
            if (!preparation.active()) return preparation.finish(rejected("extraction_preparation_cancelled"));
            if (inspected.value().isEmpty()) return preparation.observation(inspected.status(), inspected.detail());
            selection = inspected.value().orElseThrow();
            capture = selection.request();
        } else {
            if (!selection.current(host)) return preparation.finish(rejected("capture_source_changed_during_preparation"));
            capture = selection.request();
        }
        var backend = org.academy.internal.common.structure.StructureBackendRouter
                .captureTarget(host, capture.physical()).orElse(null);
        if (backend == null) return preparation.finish(rejected("extraction_backend_unavailable"));
        double mass = backend.estimateMass(host, capture.positions());
        if (!preparation.active()) return preparation.finish(rejected("extraction_preparation_cancelled"));
        if (Double.isFinite(mass) && mass > StructureExtraction.Limits.MAX_FORCE / 40) {
            return preparation.finish(rejected("extraction_force_budget_exceeded"));
        }
        var permission = capture.permission();
        var guarded = new StructureCaptureRequest(capture.positions(), capture.maximumBlocks(), capture.maximumSpan(),
                (level, pos) -> !preparation.cancelled && preparation.actorValid()
                        && permission.test(level, pos) && !preparation.cancelled && preparation.actorValid(),
                capture.physical(), capture.skillCreation());
        var request = new StructureExtraction.CaptureRequest(preparation.request.controlId(), guarded,
                preparation.request.liftHeight(), EXTRACTION_TIMEOUT_TICKS, preparation.request.hoverTicks(), limits(mass));
        if (preparation.selection == null) {
            // Preflight excludes selected vegetation without spending either break budget.
            // A large source must not consume the separate 48-cell route-clearance budget.
            var path = CaptureSoftClearance.inspectPath(host, selection, request);
            if (!preparation.active()) return preparation.finish(rejected("extraction_preparation_cancelled"));
            if (path.status() == StructureOperation.Status.BLOCKED && !preparation.sweepCleared
                    && CaptureSoftClearance.clearSelectionObstacles(host, actor, selection, request.liftHeight(), preparation::active) > 0) {
                preparation.sweepCleared = true;
                path = CaptureSoftClearance.inspectPath(host, selection, request);
            }
            if (!preparation.active()) return preparation.finish(rejected("extraction_preparation_cancelled"));
            if (path.status() != StructureOperation.Status.READY) return preparation.observation(path.status(), path.detail());
            var cleared = CaptureSoftClearance.applySelection(host, actor, selection, preparation::active);
            if (!preparation.active()) return preparation.finish(rejected("extraction_preparation_cancelled"));
            if (cleared.value().isEmpty()) return preparation.observation(cleared.status(), cleared.detail());
            preparation.selection = selection;
        }
        var preview = StructureExtractions.service().planCapture(new StructureOperation.ActorContext(host, actor), request);
        if (!preparation.active()) return preparation.finish(rejected("extraction_preparation_cancelled"));
        if (preview.status() != StructureOperation.Status.READY || preview.value().isEmpty()) {
            return preparation.observation(preview.status(), preview.detail());
        }
        // The coordinator watches request identity, so preview and commit must share this exact object.
        var result = StructureTransactions.capture(host, request.capture());
        if (preparation.cancelled) {
            return preparation.finish(new StructureCaptureResult(result.transactionId(), StructureCaptureResult.Status.REJECTED,
                    result.structure(), result.problemPosition(), 0, "extraction_preparation_cancelled"));
        }
        if (!result.pending() && !result.succeeded() && "scheduled_tick_pending".equals(result.detail())) {
            return preparation.waiting("capture_source_scheduled_tick_pending");
        }
        if ((result.pending() || result.succeeded()) && result.structure().isPresent()) {
            var reference = result.structure().orElseThrow();
            SESSIONS.put(reference, new Session(host, actor, preview.value().orElseThrow(), result));
            // Bind a completed capture before the next solver tick can settle a partial-height
            // block into its support. The source-grid path was just verified in this call.
            if (result.succeeded()) advance(host, actor, reference);
        }
        return preparation.finish(result);
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
        for (var entry : Map.copyOf(PREPARATIONS).entrySet()) {
            var preparation = entry.getValue();
            if (preparation.host.getServer() != event.getServer()) continue;
            long now = preparation.host.getGameTime();
            if (!preparation.actor.isAlive() || preparation.actor.isRemoved() || preparation.actor.level() != preparation.host
                    || preparation.actor instanceof net.minecraft.server.level.ServerPlayer player && player.hasDisconnected()) {
                preparation.cancelled = true;
            }
            if (preparation.result != null && preparation.result.pending()) {
                var current = StructureTransactions.poll(event.getServer(), preparation.result.transactionId()).orElse(null);
                if (current != null && !current.pending()) preparation.finish(current);
            }
            if (preparation.cancelled) {
                if (preparation.result != null && preparation.result.pending()) {
                    preparation.result = StructureTransactions.poll(event.getServer(), preparation.result.transactionId()).orElse(null);
                }
                if (preparation.result != null) preparation.result.structure().ifPresent(ref -> release(preparation.actor, ref));
                if (preparation.result == null || !preparation.result.pending()) PREPARATIONS.remove(entry.getKey(), preparation);
            } else if (preparation.result == null && (CapturePreparationPolicy.expired(preparation.startedAt, now)
                    || !preparation.actor.isAlive() || preparation.actor.isRemoved() || preparation.actor.level() != preparation.host)) {
                preparation.finish(StructureCaptureResult.failure(preparation.id, StructureCaptureResult.Status.TIMEOUT,
                        "extraction_preparation_timeout:" + preparation.detail));
            } else if (preparation.result != null && !preparation.result.pending()
                    && now - preparation.finishedAt >= CAPTURE_TIMEOUT_TICKS) {
                PREPARATIONS.remove(entry.getKey(), preparation);
            }
        }
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
        PREPARATIONS.entrySet().removeIf(entry -> entry.getValue().host.getServer() == event.getServer());
    }

    private static void requireThread(ServerLevel host) {
        if (!host.getServer().isSameThread()) throw new IllegalStateException("Extraction requires owning server thread");
    }

    private static final class Preparation {
        final UUID id = UUID.randomUUID();
        final ServerLevel host;
        final Entity actor;
        final StructureExtraction.CaptureRequest request;
        final long startedAt;
        long lastAttempt = Long.MIN_VALUE;
        long finishedAt;
        String detail = "capture_selection_pending";
        CaptureSoftClearance.Selection selection;
        StructureCaptureResult result;
        boolean sweepCleared;
        boolean cancelled;
        boolean facade;

        Preparation(ServerLevel host, Entity actor, StructureExtraction.CaptureRequest request) {
            this.host = host; this.actor = actor; this.request = request; this.startedAt = host.getGameTime();
        }

        boolean owned(ServerLevel host, Entity actor) {
            return actor != null && this.host == host && this.actor.getUUID().equals(actor.getUUID())
                    && actor.level().getServer() == host.getServer();
        }

        boolean actorValid() {
            return actor != null && actor.isAlive() && !actor.isRemoved() && actor.level() == host
                    && !(actor instanceof net.minecraft.server.level.ServerPlayer player && player.hasDisconnected());
        }

        boolean active() { return !cancelled && actorValid() && PREPARATIONS.get(id) == this; }

        StructureCaptureResult waiting(String detail) {
            this.detail = detail;
            return StructureCaptureResult.preparing(id, "extraction_preparing:" + detail);
        }

        StructureCaptureResult observation(StructureOperation.Status status, String detail) {
            return CapturePreparationPolicy.retryable(status, detail) ? waiting(detail)
                    : finish(rejected("extraction_preview:" + status + ":" + detail));
        }

        StructureCaptureResult finish(StructureCaptureResult result) {
            this.result = result;
            if (!result.pending()) this.finishedAt = host.getGameTime();
            return facade ? display(result) : result;
        }

        StructureCaptureResult display(StructureCaptureResult result) {
            return new StructureCaptureResult(id, result.status(), result.structure(), result.problemPosition(),
                    result.affectedBlocks(), result.detail());
        }
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
