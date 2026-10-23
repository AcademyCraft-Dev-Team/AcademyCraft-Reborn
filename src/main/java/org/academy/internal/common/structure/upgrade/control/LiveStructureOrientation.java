package org.academy.internal.common.structure.upgrade.control;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import org.academy.AcademyCraft;
import org.academy.api.common.structure.StructureDirectControl;
import org.academy.api.common.structure.StructureRef;
import org.academy.api.common.structure.upgrade.StructureOperation;
import org.academy.api.common.structure.upgrade.StructureOrientation;
import org.academy.internal.common.structure.StructureBackendRouter;

import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/** Bounded server-owned operation registry shared by local and optional Sable solver adapters. */
@EventBusSubscriber(modid = AcademyCraft.MOD_ID)
public final class LiveStructureOrientation implements StructureOrientation {
    public static final LiveStructureOrientation INSTANCE = new LiveStructureOrientation();
    private static final int MAX_ACTIVE = 16;
    private static final int MAX_RECEIPTS = 256;
    private static final int RECEIPT_TICKS = 200;
    private static final Map<MinecraftServer, Registry> SERVERS = new IdentityHashMap<>();

    private LiveStructureOrientation() {}

    public static Optional<StructureOperation.Context> context(ServerLevel host, StructureRef reference, Entity actor) {
        requireThread(host);
        if (!actorAvailable(host, actor) || !reference.dimension().equals(host.dimension().identifier())) return Optional.empty();
        var access = StructureBackendRouter.orientationAccess(host, reference);
        return access.flatMap(backend -> backend.observe(host, reference)).map(body ->
                new StructureOperation.Context(host, reference, body.runtimeId(), actor, body.contentRevision()));
    }

    @Override
    public StructureOperation.Result<Snapshot> rotateTo(StructureOperation.Context context, RotateRequest request) {
        Objects.requireNonNull(request, "request");
        return submit(context, request.controlId(), Kind.ROTATE_TO, BaselineSource.NOT_APPLICABLE,
                Optional.of(request.targetRotation()), Optional.empty(), request.limits());
    }

    @Override
    public StructureOperation.Result<Snapshot> reset(StructureOperation.Context context, ResetRequest request) {
        Objects.requireNonNull(request, "request");
        requireThread(context.host());
        var body = authorized(context, request.controlId());
        if (body.isEmpty()) return failure(StructureOperation.Status.STALE, "orientation_authority_unavailable");
        var target = OrientationTargets.reset(body.get().baselines(), body.get().motion().pose(), request.mode(), request.missingBaselinePolicy());
        if (target.isEmpty()) return failure(StructureOperation.Status.UNSUPPORTED, "orientation_baseline_missing");
        return submit(context, request.controlId(), Kind.RESET, target.get().source(),
                Optional.of(target.get().rotation()), Optional.empty(), request.limits());
    }

    @Override
    public StructureOperation.Result<Snapshot> spin(StructureOperation.Context context, SpinRequest request) {
        Objects.requireNonNull(request, "request");
        return submit(context, request.controlId(), Kind.SPIN, BaselineSource.NOT_APPLICABLE,
                Optional.empty(), Optional.of(request), request.limits());
    }

    private StructureOperation.Result<Snapshot> submit(StructureOperation.Context context, UUID control,
            Kind kind, BaselineSource baseline, Optional<org.academy.api.common.structure.StructurePose> target,
            Optional<SpinRequest> spin, Limits limits) {
        requireThread(context.host());
        var access = StructureBackendRouter.orientationAccess(context.host(), context.reference());
        if (access.isEmpty()) return failure(StructureOperation.Status.UNSUPPORTED, "orientation_backend_unavailable");
        var body = authorized(context, control);
        if (body.isEmpty()) return failure(StructureOperation.Status.STALE, "orientation_authority_unavailable");
        var registry = SERVERS.computeIfAbsent(context.host().getServer(), ignored -> new Registry());
        prune(registry, context.host().getServer());
        var previous = registry.active.get(context.reference());
        if (previous == null && registry.active.size() >= MAX_ACTIVE) {
            return failure(StructureOperation.Status.CONFLICT, "orientation_capacity");
        }
        if (previous != null && !previous.binding.controlId().equals(control)) {
            return failure(StructureOperation.Status.CONFLICT, "orientation_control_conflict");
        }
        if (previous != null) finish(registry, previous, StructureOperation.Status.STALE, Phase.STOPPED, "orientation_replaced");
        // Evict oldest completed receipts only; active commands are never silently dropped.
        while (registry.receipts.size() >= MAX_RECEIPTS) {
            var old = registry.receipts.values().stream().filter(entry -> entry.finishedAt >= 0).findFirst();
            if (old.isEmpty()) return failure(StructureOperation.Status.CONFLICT, "orientation_receipt_capacity");
            registry.receipts.remove(old.get().id);
        }
        var entry = new Entry(context, control, access.get(), new Plan(kind, baseline, target, limits,
                body.get().poseRevision(), 0), spin);
        registry.receipts.put(entry.id, entry);
        registry.active.put(context.reference(), entry);
        return entry.result;
    }

    @Override
    public StructureOperation.Result<Snapshot> status(StructureOperation.Context context, StatusRequest request) {
        requireThread(context.host());
        var registry = SERVERS.get(context.host().getServer());
        var entry = owned(registry, context, request.controlId(), request.operationId());
        if (entry == null) return failure(StructureOperation.Status.STALE, "orientation_receipt_unavailable");
        if (entry.finishedAt < 0 && authorized(context, request.controlId()).isEmpty()) {
            finish(registry, entry, StructureOperation.Status.STALE, Phase.EXPIRED, "orientation_authority_lost");
        }
        return entry.result;
    }

    @Override
    public StructureOperation.Result<Snapshot> stop(StructureOperation.Context context, StopRequest request) {
        requireThread(context.host());
        var registry = SERVERS.get(context.host().getServer());
        var entry = owned(registry, context, request.controlId(), request.operationId());
        if (entry == null) return failure(StructureOperation.Status.STALE, "orientation_receipt_unavailable");
        if (entry.finishedAt >= 0) return entry.result;
        if (authorized(context, request.controlId()).isEmpty()) {
            finish(registry, entry, StructureOperation.Status.STALE, Phase.EXPIRED, "orientation_authority_lost");
        } else if (request.mode() == StopMode.RELEASE_INERTIA) {
            finish(registry, entry, StructureOperation.Status.COMPLETED, Phase.STOPPED, "orientation_stopped");
        } else if (entry.session == null) {
            // A replacement may inherit physical inertia from its predecessor. Queue braking
            // against the first actual substep; enqueueing or stopping must never write a pose.
            entry.plan = new Plan(entry.plan.kind(), entry.plan.baselineSource(), entry.plan.targetRotation(),
                    OrientationSession.restrict(entry.plan.limits(), request.limits()),
                    entry.plan.poseRevision(), entry.plan.terrainRevision());
            entry.brakeQueued = true;
            entry.result = new StructureOperation.Result<>(StructureOperation.Status.RUNNING, "orientation_braking_queued",
                    Optional.of(entry.id), Optional.of(new Snapshot(entry.id, entry.binding.controlId(), Phase.BRAKING,
                    entry.plan, 0, 0, context.host().getGameTime() - entry.createdTick)));
        } else {
            entry.result = entry.session.stop(entry.binding, entry.id, request.mode(), request.limits());
            entry.plan = entry.result.value().orElseThrow().plan();
        }
        return entry.result;
    }

    public static boolean hasActive(ServerLevel host, boolean sable) {
        var registry = SERVERS.get(host.getServer());
        return registry != null && registry.active.values().stream().anyMatch(entry -> entry.context.host() == host
                && entry.sable == sable);
    }

    public static boolean controls(ServerLevel host, StructureRef reference) {
        var registry = SERVERS.get(host.getServer());
        var entry = registry == null ? null : registry.active.get(reference);
        return entry != null && entry.context.host() == host;
    }

    public static boolean controls(ServerLevel host, StructureRef reference, UUID controlId) {
        var registry = SERVERS.get(host.getServer());
        var entry = registry == null ? null : registry.active.get(reference);
        return entry != null && entry.context.host() == host && entry.binding.controlId().equals(controlId);
    }

    /** Called only by a real solver's pre-substep hook after translation commands and coverage. */
    public static void beforeSubstep(ServerLevel host, boolean sable, double seconds) {
        requireThread(host);
        var registry = SERVERS.get(host.getServer());
        if (registry == null || registry.active.isEmpty()) return;
        long sequence = ++registry.stepSequence;
        int[] cells = {65_536};
        for (var entry : List.copyOf(registry.active.values())) {
            if (entry.context.host() != host || entry.sable != sable) continue;
            try {
                if (!Double.isFinite(seconds) || seconds <= 0 || seconds > OrientationController.MAX_SUBSTEP_SECONDS) {
                    finish(registry, entry, StructureOperation.Status.BLOCKED, Phase.BLOCKED, "orientation_substep_invalid");
                    continue;
                }
                var body = authorized(entry.context, entry.binding.controlId());
                if (body.isEmpty()) {
                    finish(registry, entry, StructureOperation.Status.STALE, Phase.EXPIRED, "orientation_authority_lost");
                    continue;
                }
                if (!body.get().terrainReady()) continue;
                if (entry.session == null) {
                    var initialLimits = entry.brakeQueued && entry.spin.isPresent()
                            ? entry.spin.get().limits() : entry.plan.limits();
                    var plan = new Plan(entry.plan.kind(), entry.plan.baselineSource(), entry.plan.targetRotation(),
                            initialLimits, body.get().poseRevision(), sequence);
                    entry.session = new OrientationSession(entry.id, entry.binding, plan, entry.spin, entry.createdTick);
                    if (entry.brakeQueued) entry.session.stop(entry.binding, entry.id, StopMode.BRAKE, entry.plan.limits());
                }
                var bridge = new Substep(entry, body.get(), sequence, seconds, cells);
                entry.result = entry.session.advance(entry.binding, host.getGameTime(), seconds, bridge);
                if (terminal(entry.result)) retire(registry, entry);
            } catch (RuntimeException exception) {
                // One malformed/unloaded structure cannot stop an entire dimension's solver.
                finish(registry, entry, StructureOperation.Status.BLOCKED, Phase.BLOCKED, "orientation_backend_failure");
                AcademyCraft.LOGGER.warn("Structure orientation {} failed for {}", entry.id, entry.context.reference(), exception);
            }
        }
    }

    public static void cancel(ServerLevel host, StructureRef reference, UUID controlId) {
        requireThread(host);
        var registry = SERVERS.get(host.getServer());
        var entry = registry == null ? null : registry.active.get(reference);
        if (entry != null && entry.context.host() == host && entry.binding.controlId().equals(controlId)) {
            finish(registry, entry, StructureOperation.Status.COMPLETED, Phase.STOPPED, "orientation_cancelled");
        }
    }

    /** A successful server-authorized launch takes over from the current angular command. */
    public static void launched(ServerLevel host, StructureRef reference) {
        requireThread(host);
        var registry = SERVERS.get(host.getServer());
        var entry = registry == null ? null : registry.active.get(reference);
        if (entry != null && entry.context.host() == host) {
            finish(registry, entry, StructureOperation.Status.COMPLETED, Phase.STOPPED, "orientation_launched");
        }
    }

    /** Called by exact-token lease invalidation, including replacement by another skill. */
    public static void cancel(StructureRef reference, UUID controlId) {
        for (var registry : SERVERS.values()) {
            var entry = registry.active.get(reference);
            if (entry != null && entry.binding.controlId().equals(controlId)) {
                finish(registry, entry, StructureOperation.Status.STALE, Phase.STOPPED, "orientation_lease_revoked");
            }
        }
    }

    @SubscribeEvent
    public static void afterTick(ServerTickEvent.Post event) {
        var registry = SERVERS.get(event.getServer());
        if (registry == null) return;
        for (var entry : List.copyOf(registry.active.values())) {
            var context = entry.context;
            if (!actorAvailable(context.host(), context.actor())
                    || event.getServer().getLevel(context.host().dimension()) != context.host()
                    || !StructureDirectControl.matches(context.reference(), context.actor().getUUID(),
                    entry.binding.controlId(), context.runtimeId())) {
                finish(registry, entry, StructureOperation.Status.STALE, Phase.EXPIRED, "orientation_authority_lost");
            } else if (context.host().getGameTime() - entry.createdTick > entry.plan.limits().timeoutTicks()) {
                finish(registry, entry, StructureOperation.Status.BLOCKED, Phase.EXPIRED, "orientation_timeout");
            }
        }
        prune(registry, event.getServer());
    }

    @SubscribeEvent
    public static void stopped(ServerStoppedEvent event) { SERVERS.remove(event.getServer()); }

    private static Optional<OrientationBackendAccess.Body> authorized(StructureOperation.Context context, UUID control) {
        if (!actorAvailable(context.host(), context.actor())
                || context.host().getServer().getLevel(context.host().dimension()) != context.host()
                || !context.reference().dimension().equals(context.host().dimension().identifier())
                || !StructureDirectControl.matches(context.reference(), context.actor().getUUID(), control, context.runtimeId())) {
            return Optional.empty();
        }
        var body = StructureBackendRouter.orientationAccess(context.host(), context.reference())
                .flatMap(access -> access.observe(context.host(), context.reference()));
        if (body.isEmpty() || !body.get().runtimeId().equals(context.runtimeId())
                || body.get().contentRevision() != context.expectedContentRevision()) return Optional.empty();
        if (body.get().terrainReady() && StructureDirectControl.validate(context.host(), context.reference(), context.actor(), control)
                != StructureDirectControl.Status.ACTIVE) return Optional.empty();
        return body;
    }

    private static Entry owned(Registry registry, StructureOperation.Context context, UUID control, UUID operation) {
        var entry = registry == null ? null : registry.receipts.get(operation);
        return entry != null && entry.context.equals(context) && entry.binding.controlId().equals(control) ? entry : null;
    }

    private static void prune(Registry registry, MinecraftServer server) {
        registry.receipts.values().removeIf(entry -> entry.finishedAt >= 0
                && server.getTickCount() - entry.finishedAt > RECEIPT_TICKS);
    }

    private static void finish(Registry registry, Entry entry, StructureOperation.Status status, Phase phase, String detail) {
        var prior = entry.result.value().orElseThrow();
        entry.result = new StructureOperation.Result<>(status, detail, Optional.of(entry.id), Optional.of(new Snapshot(
                entry.id, entry.binding.controlId(), phase, prior.plan(), prior.angleError(), prior.angularSpeed(),
                Math.max(0, entry.context.host().getGameTime() - entry.createdTick))));
        retire(registry, entry);
    }

    private static void retire(Registry registry, Entry entry) {
        registry.active.remove(entry.context.reference(), entry);
        entry.finishedAt = entry.context.host().getServer().getTickCount();
    }

    private static boolean terminal(StructureOperation.Result<Snapshot> result) {
        return switch (result.value().orElseThrow().phase()) {
            case COMPLETED, BLOCKED, STOPPED, EXPIRED -> true;
            default -> false;
        };
    }

    private static boolean actorAvailable(ServerLevel host, Entity actor) {
        return actor != null && actor.isAlive() && !actor.isRemoved() && actor.level() == host
                && !(actor instanceof net.minecraft.server.level.ServerPlayer player && player.hasDisconnected());
    }

    private static void requireThread(ServerLevel host) {
        Objects.requireNonNull(host, "host");
        if (!host.getServer().isSameThread()) throw new IllegalStateException("Orientation requires the server thread");
    }

    private static StructureOperation.Result<Snapshot> failure(StructureOperation.Status status, String detail) {
        return new StructureOperation.Result<>(status, detail, Optional.empty(), Optional.empty());
    }

    private static final class Registry {
        final Map<UUID, Entry> receipts = new LinkedHashMap<>();
        final Map<StructureRef, Entry> active = new LinkedHashMap<>();
        long stepSequence;
    }

    private static final class Entry {
        final UUID id = UUID.randomUUID();
        final StructureOperation.Context context;
        final OrientationSession.Binding binding;
        final OrientationBackendAccess access;
        Plan plan;
        final Optional<SpinRequest> spin;
        final long createdTick;
        final boolean sable;
        OrientationSession session;
        boolean brakeQueued;
        StructureOperation.Result<Snapshot> result;
        int finishedAt = -1;

        Entry(StructureOperation.Context context, UUID control, OrientationBackendAccess access, Plan plan, Optional<SpinRequest> spin) {
            this.context = context; this.access = access; this.plan = plan; this.spin = spin;
            binding = new OrientationSession.Binding(context.reference(), context.runtimeId(), context.actor().getUUID(),
                    control, context.expectedContentRevision());
            createdTick = context.host().getGameTime();
            sable = context.reference().backend().equals(StructureBackendRouter.SABLE_ID);
            result = new StructureOperation.Result<>(StructureOperation.Status.RUNNING, "orientation_queued", Optional.of(id),
                    Optional.of(new Snapshot(id, control, Phase.PREPARING, plan, 0, 0, 0)));
        }
    }

    private static final class Substep implements OrientationSession.Backend {
        final Entry entry;
        final OrientationBackendAccess.Body body;
        final long sequence;
        final double seconds;
        final int[] cells;
        OrientationWorldSnapshot world;

        Substep(Entry entry, OrientationBackendAccess.Body body, long sequence, double seconds, int[] cells) {
            this.entry = entry; this.body = body; this.sequence = sequence; this.seconds = seconds; this.cells = cells;
        }

        @Override public Optional<OrientationSession.Observation> observe(OrientationSession.Binding authority) {
            return Optional.of(new OrientationSession.Observation(entry.binding, sequence, body.poseRevision(), sequence, body.motion()));
        }

        @Override public OrientationSweep.Status sweep(OrientationSession.Observation observed, OrientationController.Step command,
                                                       double ignoredSeconds, int maximumTests) {
            world = new OrientationWorldSnapshot(entry.context.host(), body, sequence, seconds, cells);
            return OrientationSweep.check(body.localBoxes(), body.motion().pose(), body.motion().localCenterOfMass(),
                    command.nextAngularVelocity(), body.freeStepDisplacement(seconds), body.freeStepMargin(seconds),
                    seconds, sequence, maximumTests, world).status();
        }

        @Override public boolean applyAngularVelocity(OrientationSession.Observation observed, Vec3 next) {
            return world != null && world.current() && authorized(entry.context, entry.binding.controlId())
                    .filter(body::sameObservation).isPresent() && entry.access.apply(entry.context.host(), body, next);
        }
    }
}
