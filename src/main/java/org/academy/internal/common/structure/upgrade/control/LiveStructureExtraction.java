package org.academy.internal.common.structure.upgrade.control;

import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import org.academy.AcademyCraft;
import org.academy.api.common.structure.StructureDirectControl;
import org.academy.api.common.structure.StructureCaptureRequest;
import org.academy.api.common.structure.StructurePose;
import org.academy.api.common.structure.StructureRef;
import org.academy.api.common.structure.StructureSpaces;
import org.academy.api.common.structure.StructureWorldAccess;
import org.academy.api.common.structure.upgrade.StructureExtraction;
import org.academy.api.common.structure.upgrade.StructureOperation;
import org.academy.internal.common.structure.StructureBackendRouter;
import org.academy.internal.common.structure.transaction.StructureCaptureCoordinator;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * Capture preview and source binding on top of the live backends. Previews are planned from the
 * real host selection with the pure {@link ExtractionPlanner}; the caller only ever receives the
 * display value, while the source snapshot, actor, request and expiry stay server-side.
 *
 * <p>Binding re-reads the committed structure's content on its local grid and compares the
 * digest with the previewed source and the completed transaction's retained evidence, so a forged preview, a different capture or a silently
 * changed selection cannot be turned into a plan. The plan is re-planned from the body's actual
 * pose and collision geometry. Planning and binding never move a body or acquire a second lease.
 * A begun operation advances only in the owning solver's pre-substep hook.</p>
 *
 * <p>Pre-capture previews carry terrain revision {@code 0} because no structural terrain version
 * exists before a body is created; the content digest is the authoritative source evidence.
 * Bound and existing-body plans also retain this sentinel: begin rechecks the full path, and
 * each solver step uses its own loaded-only snapshot and verifies that snapshot before applying
 * velocity. A zero revision never bypasses the live terrain check.</p>
 */
@EventBusSubscriber(modid = AcademyCraft.MOD_ID)
public final class LiveStructureExtraction implements StructureExtraction {
    public static final LiveStructureExtraction INSTANCE = new LiveStructureExtraction();
    private static final int PLAN_TTL_TICKS = 1_200;
    private static final long UNVERSIONED_TERRAIN = 0L;
    private static final Map<MinecraftServer, CapturePlanRegistry> REGISTRIES = new IdentityHashMap<>();

    private LiveStructureExtraction() {
    }

    @Override
    public StructureOperation.Result<CapturePlan> planCapture(StructureOperation.ActorContext context,
                                                              CaptureRequest request) {
        requireThread(context.host());
        var host = context.host();
        var actor = context.actor();
        if (!actorAvailable(host, actor)) return failure(StructureOperation.Status.DENIED, "capture_actor_unavailable");
        if (host.getServer().getLevel(host.dimension()) != host) {
            return failure(StructureOperation.Status.DENIED, "capture_host_dimension_mismatch");
        }
        var permissionFailure = LiveStructureExtraction.<CapturePlan>permissionFailure(host, actor, request.capture());
        if (permissionFailure != null) return permissionFailure;
        Optional<LevelCaptureSource.Snapshot> read;
        try {
            read = LevelCaptureSource.read(host, request.capture());
        } catch (LevelCaptureSource.Rejected rejected) {
            return failure(rejected.status, rejected.detail);
        }
        if (read.isEmpty()) {
            return failure(StructureOperation.Status.TERRAIN_PENDING, "capture_source_chunk_unloaded");
        }
        var source = read.get();
        if (!source.hasCollisionGeometry()) {
            return failure(StructureOperation.Status.UNSUPPORTED, "capture_source_has_no_collision_geometry");
        }
        var origin = source.source().origin();
        var baseline = StructurePose.identity(Vec3.atLowerCornerOf(origin));
        var geometry = new ExtractionPlanBuilder.Geometry(baseline, baseline, source.localCenterOfMass(),
                source.localBoxes(), 0L, 0L);
        var outcome = plan(host, request.limits(), request.liftHeight(), request.hoverTicks(), geometry,
                source.source().cells().stream().map(CaptureSourceSnapshot.Cell::position).toList(),
                source.localBoxes(), baseline, request.limits().maxRetreatDistance(), null);
        if (outcome.status() != StructureOperation.Status.READY) {
            return new StructureOperation.Result<>(outcome.status(), outcome.detail(),
                    Optional.empty(), Optional.empty());
        }
        long now = host.getGameTime();
        var planId = UUID.randomUUID();
        var preview = new CapturePlan(planId, actor.getUUID(), host.dimension().identifier(),
                source.source().digest(), outcome.terrainRevision(), now + PLAN_TTL_TICKS,
                request, outcome.worldPath(), outcome.blockedCells());
        if (registry(host.getServer()).register(preview, source.source(), now)
                != CapturePlanRegistry.Registration.REGISTERED) {
            return failure(StructureOperation.Status.CONFLICT, "capture_plan_registry_full");
        }
        if (!StructureCaptureCoordinator.watchExtractionPreview(host, request.capture(), preview.expiresAtGameTime())) {
            return failure(StructureOperation.Status.CONFLICT, "capture_preview_observation_capacity");
        }
        return new StructureOperation.Result<>(StructureOperation.Status.READY, "capture_preview_ready",
                Optional.empty(), Optional.of(preview));
    }

    @Override
    public StructureOperation.Result<Plan> bindCapture(StructureOperation.Context context,
                                                       BindCaptureRequest request) {
        requireThread(context.host());
        var host = context.host();
        var actor = context.actor();
        if (!actorAvailable(host, actor)) return failure(StructureOperation.Status.DENIED, "capture_actor_unavailable");
        if (!context.reference().dimension().equals(host.dimension().identifier())) {
            return failure(StructureOperation.Status.DENIED, "capture_reference_dimension_mismatch");
        }
        var registry = registry(host.getServer());
        long now = host.getGameTime();
        var lookup = registry.resolve(request.planId(), actor.getUUID(), now);
        switch (lookup.outcome()) {
            case MISSING -> {
                return failure(StructureOperation.Status.DENIED, "capture_preview_unknown");
            }
            case EXPIRED -> {
                return failure(StructureOperation.Status.STALE, "capture_preview_expired");
            }
            case DENIED -> {
                return failure(StructureOperation.Status.DENIED, "capture_preview_foreign_actor");
            }
            default -> {
            }
        }
        var entry = Objects.requireNonNull(lookup.entry(), "entry");
        if (!entry.preview().hostDimension().equals(host.dimension().identifier())) {
            return failure(StructureOperation.Status.DENIED, "capture_preview_dimension_mismatch");
        }
        if (!entry.preview().request().controlId().equals(request.controlId())) {
            return failure(StructureOperation.Status.DENIED, "capture_control_token_mismatch");
        }
        if (entry.bound()) {
            if (entry.captureOperationId().filter(id -> id.equals(request.captureOperationId())).isEmpty()) {
                return failure(StructureOperation.Status.CONFLICT, "capture_preview_already_bound");
            }
            var bound = entry.boundPlan().orElseThrow();
            if (!bound.reference().equals(context.reference())) {
                return failure(StructureOperation.Status.DENIED, "capture_structure_mismatch");
            }
        }
        var committed = StructureCaptureCoordinator.poll(host.getServer(), request.captureOperationId());
        if (committed.isEmpty()) {
            return failure(StructureOperation.Status.CONFLICT, "capture_result_unknown");
        }
        var result = committed.get();
        if (result.pending()) return failure(StructureOperation.Status.CONFLICT, "capture_pending");
        if (!result.succeeded()) {
            return failure(StructureOperation.Status.DENIED,
                    "capture_" + result.status().name().toLowerCase(Locale.ROOT));
        }
        if (result.structure().filter(structure -> structure.equals(context.reference())).isEmpty()) {
            return failure(StructureOperation.Status.DENIED, "capture_structure_mismatch");
        }
        var evidence = StructureCaptureCoordinator.completedEvidence(host.getServer(), request.captureOperationId())
                .orElse(null);
        if (evidence == null) return failure(StructureOperation.Status.DENIED, "capture_source_evidence_unavailable");
        if (!evidence.matches(host.dimension().identifier(), context.reference(), entry.preview().request().capture())) {
            return failure(StructureOperation.Status.DENIED, "capture_submission_mismatch");
        }
        var committedSource = CaptureSourceSnapshot.of(evidence.cells().stream()
                .map(cell -> new CaptureSourceSnapshot.Cell(cell.position(), cell.state())).toList());
        if (!committedSource.digest().equals(entry.source().digest())) {
            return failure(StructureOperation.Status.DENIED, "capture_source_digest_mismatch");
        }
        var permissionFailure = LiveStructureExtraction.<Plan>permissionFailure(host, actor,
                entry.preview().request().capture());
        if (permissionFailure != null) return permissionFailure;
        if (StructureDirectControl.validate(host, context.reference(), actor, request.controlId())
                != StructureDirectControl.Status.ACTIVE) {
            return failure(StructureOperation.Status.DENIED, "capture_control_lease_inactive");
        }
        var body = StructureBackendRouter.orientationAccess(host, context.reference())
                .flatMap(access -> access.observe(host, context.reference())).orElse(null);
        if (body == null) {
            return failure(StructureOperation.Status.TERRAIN_PENDING, "capture_body_unavailable");
        }
        if (!body.terrainReady()) {
            return failure(StructureOperation.Status.TERRAIN_PENDING, "capture_body_terrain_pending");
        }
        if (!body.runtimeId().equals(context.runtimeId())
                || body.contentRevision() != context.expectedContentRevision()
                || !body.runtimeId().equals(evidence.runtimeId())
                || body.contentRevision() != evidence.contentRevision()) {
            return failure(StructureOperation.Status.STALE, "capture_authority_stale");
        }
        if (entry.bound()) {
            var previous = entry.boundPlan().orElseThrow();
            if (!previous.runtimeId().equals(body.runtimeId()) || previous.contentRevision() != body.contentRevision()
                    || previous.poseRevision() != body.poseRevision()) {
                return failure(StructureOperation.Status.STALE, "capture_binding_observation_stale");
            }
        }
        var content = verifySourceContent(host.getServer(), context, entry);
        if (content != StructureOperation.Status.READY) {
            return content == StructureOperation.Status.DENIED
                    ? failure(StructureOperation.Status.DENIED, "capture_source_digest_mismatch")
                    : failure(StructureOperation.Status.TERRAIN_PENDING, "capture_structure_content_unavailable");
        }
        var origin = entry.source().origin();
        var baseline = StructurePose.identity(Vec3.atLowerCornerOf(origin));
        var geometry = new ExtractionPlanBuilder.Geometry(baseline, body.motion().pose(),
                body.motion().localCenterOfMass(), body.localBoxes(),
                body.contentRevision(), body.poseRevision());
        var outcome = plan(host, entry.preview().request().limits(), entry.preview().request().liftHeight(),
                entry.preview().request().hoverTicks(), geometry, List.of(),
                body.localBoxes(), body.motion().pose(), entry.preview().request().limits().maxRetreatDistance(),
                context.reference());
        if (outcome.status() != StructureOperation.Status.READY) {
            return new StructureOperation.Result<>(outcome.status(), outcome.detail(),
                    Optional.empty(), Optional.empty());
        }
        if (entry.bound()) {
            var previous = entry.boundPlan().orElseThrow();
            if (!outcome.worldPath().equals(previous.worldPath())) {
                return failure(StructureOperation.Status.STALE, "capture_binding_path_changed");
            }
            return new StructureOperation.Result<>(StructureOperation.Status.READY, "capture_binding_repeated",
                    Optional.empty(), Optional.of(previous));
        }
        var planRequest = new PlanRequest(request.controlId(), SourceKind.NEW_CAPTURE, baseline,
                body.motion().localCenterOfMass(), entry.preview().request().liftHeight(),
                entry.preview().request().extractionTimeoutTicks(), entry.preview().request().hoverTicks(),
                entry.preview().request().limits());
        var plan = new Plan(entry.preview().planId(), context.reference(), body.runtimeId(),
                body.contentRevision(), body.poseRevision(), outcome.terrainRevision(),
                Math.max(entry.expiresAt(), now + PLAN_TTL_TICKS), planRequest,
                outcome.worldPath(), outcome.blockedCells());
        var bind = registry.bind(actor.getUUID(), plan, request.captureOperationId(), now);
        return switch (bind.outcome()) {
            case READY, REPEATED -> new StructureOperation.Result<>(StructureOperation.Status.READY,
                    bind.outcome() == CapturePlanRegistry.Outcome.REPEATED ? "capture_binding_repeated"
                            : "capture_bound", Optional.empty(), bind.plan() == null
                    ? Optional.empty() : Optional.of(bind.plan()));
            case CONFLICT -> failure(StructureOperation.Status.CONFLICT, "capture_preview_already_bound");
            case EXPIRED -> failure(StructureOperation.Status.STALE, "capture_preview_expired");
            case MISSING, DENIED -> failure(StructureOperation.Status.DENIED, "capture_preview_foreign_actor");
        };
    }

    @Override
    public StructureOperation.Result<Plan> plan(StructureOperation.Context context, PlanRequest request) {
        requireThread(context.host());
        if (request.sourceKind() != SourceKind.EXISTING_STRUCTURE) {
            return failure(StructureOperation.Status.DENIED, "extraction_capture_binding_required");
        }
        var linear = LiveExtractionMotion.authorized(context, request.controlId()).orElse(null);
        if (linear == null) return failure(StructureOperation.Status.DENIED, "extraction_authority_unavailable");
        var body = linear.body();
        if (!body.terrainReady()) return failure(StructureOperation.Status.TERRAIN_PENDING, "extraction_terrain_pending");
        if (!request.sourcePose().equals(body.motion().pose())
                || !request.localCenterOfMass().equals(body.motion().localCenterOfMass())) {
            return failure(StructureOperation.Status.STALE, "extraction_source_observation_stale");
        }
        var outcome = replan(context.host(), request, body);
        if (outcome.status() != StructureOperation.Status.READY) return failure(outcome.status(), outcome.detail());
        var plan = new Plan(UUID.randomUUID(), context.reference(), body.runtimeId(), body.contentRevision(),
                body.poseRevision(), outcome.terrainRevision(), context.host().getGameTime() + PLAN_TTL_TICKS,
                request, outcome.worldPath(), outcome.blockedCells());
        return LiveExtractionMotion.save(context, plan);
    }

    @Override
    public StructureOperation.Result<Snapshot> begin(StructureOperation.Context context, BeginRequest request) {
        requireThread(context.host());
        var repeated = LiveExtractionMotion.repeated(context, request);
        if (repeated != null) return repeated;
        var plan = LiveExtractionMotion.plan(context, request.controlId(), request.planId()).orElse(null);
        if (plan == null) {
            var lookup = registry(context.host().getServer()).resolve(request.planId(), context.actor().getUUID(),
                    context.host().getGameTime());
            if (lookup.entry() != null) plan = lookup.entry().boundPlan().orElse(null);
        }
        if (plan == null || !plan.request().controlId().equals(request.controlId())) {
            return failure(StructureOperation.Status.DENIED, "extraction_plan_unknown_or_foreign");
        }
        var linear = LiveExtractionMotion.authorized(context, request.controlId()).orElse(null);
        if (linear == null) return failure(StructureOperation.Status.STALE, "extraction_authority_unavailable");
        var body = linear.body();
        if (!plan.reference().equals(context.reference()) || !plan.runtimeId().equals(body.runtimeId())
                || plan.contentRevision() != body.contentRevision() || plan.poseRevision() != body.poseRevision()
                || context.host().getGameTime() > plan.expiresAtGameTime()) {
            return failure(StructureOperation.Status.STALE, "extraction_plan_stale");
        }
        // Terrain has no global version: repeat the bounded path query against current loaded
        // cells and bodies before admitting the saved plan, then recheck every solver step.
        var outcome = replan(context.host(), plan.request(), body);
        if (outcome.status() != StructureOperation.Status.READY) return failure(outcome.status(), outcome.detail());
        if (!outcome.worldPath().equals(plan.worldPath())) return failure(StructureOperation.Status.STALE, "extraction_path_changed");
        return LiveExtractionMotion.begin(context, plan);
    }

    @Override
    public StructureOperation.Result<Snapshot> status(StructureOperation.Context context, StatusRequest request) {
        requireThread(context.host());
        return LiveExtractionMotion.status(context, request.controlId(), request.operationId());
    }

    @Override
    public StructureOperation.Result<Snapshot> cancel(StructureOperation.Context context, CancelRequest request) {
        requireThread(context.host());
        return LiveExtractionMotion.cancel(context, request.controlId(), request.operationId());
    }

    @SubscribeEvent
    public static void stopped(ServerStoppedEvent event) {
        REGISTRIES.remove(event.getServer());
        LiveExtractionMotion.stopped(event.getServer());
    }

    @SubscribeEvent
    public static void afterTick(ServerTickEvent.Post event) { LiveExtractionMotion.afterTick(event.getServer()); }

    /** Both the local Post solver and Sable's level solver see these writes before terrain preparation. */
    @SubscribeEvent(priority = net.neoforged.bus.api.EventPriority.HIGH)
    public static void beforeTick(ServerTickEvent.Pre event) { LiveExtractionMotion.beforeTerrain(event.getServer()); }

    public static boolean hasActive(ServerLevel host, boolean sable) { return LiveExtractionMotion.hasActive(host, sable); }
    public static boolean controls(ServerLevel host, StructureRef reference) { return LiveExtractionMotion.controls(host, reference); }
    public static boolean controls(ServerLevel host, StructureRef reference, UUID control) {
        return LiveExtractionMotion.controls(host, reference, control);
    }
    public static AABB terrainBounds(ServerLevel host, StructureRef reference, AABB bounds, double duration) {
        return LiveExtractionMotion.terrainBounds(host, reference, bounds, duration);
    }
    public static void beforeSubstep(ServerLevel host, boolean sable, double seconds) {
        requireThread(host);
        LiveExtractionMotion.beforeSubstep(host, sable, seconds);
    }
    public static void cancel(ServerLevel host, StructureRef reference, UUID control) {
        requireThread(host);
        LiveExtractionMotion.cancel(host, reference, control);
    }
    public static void cancel(StructureRef reference, UUID control) { LiveExtractionMotion.cancel(reference, control); }
    public static void launched(ServerLevel host, StructureRef reference) {
        requireThread(host);
        LiveExtractionMotion.launched(host, reference);
    }

    private static ExtractionPlanBuilder.Outcome replan(ServerLevel host, PlanRequest request, OrientationBackendAccess.Body body) {
        var geometry = new ExtractionPlanBuilder.Geometry(request.sourcePose(), body.motion().pose(),
                body.motion().localCenterOfMass(), body.localBoxes(), body.contentRevision(), body.poseRevision());
        return plan(host, request.limits(), request.liftHeight(), request.hoverTicks(), geometry, List.of(),
                body.localBoxes(), body.motion().pose(), request.limits().maxRetreatDistance(), body.reference());
    }

    /**
     * Compares the committed structure's local content with the previewed host snapshot. Reading
     * a cell that its backend cannot serve is an unavailable observation, not a mismatch.
     */
    private static StructureOperation.Status verifySourceContent(MinecraftServer server,
                                                                 StructureOperation.Context context,
                                                                 CapturePlanRegistry.Entry entry) {
        var handle = StructureSpaces.resolve(server, context.reference()).orElse(null);
        if (handle == null) return StructureOperation.Status.TERRAIN_PENDING;
        StructureWorldAccess access = handle.world();
        var expected = entry.source().localize(entry.source().origin());
        var actual = new ArrayList<CaptureSourceSnapshot.Cell>(expected.cells().size());
        try {
            for (var cell : expected.cells()) {
                var data = access.read(cell.position());
                if (data.state().hasBlockEntity() || data.blockEntityData() != null) {
                    return StructureOperation.Status.DENIED;
                }
                actual.add(new CaptureSourceSnapshot.Cell(cell.position(), data.state().toString()));
            }
        } catch (IllegalStateException | IllegalArgumentException unavailable) {
            return StructureOperation.Status.TERRAIN_PENDING;
        }
        return CaptureSourceSnapshot.of(actual).digest().equals(expected.digest())
                ? StructureOperation.Status.READY : StructureOperation.Status.DENIED;
    }

    private static ExtractionPlanBuilder.Outcome plan(ServerLevel host, Limits limits, double liftHeight,
                                                      int hoverTicks, ExtractionPlanBuilder.Geometry geometry,
                                                      List<BlockPos> excluded, List<AABB> localBoxes,
                                                      StructurePose pose, double retreat,
                                                      StructureRef controlled) {
        var offset = pose.worldOrigin();
        AABB envelope = null;
        for (var box : localBoxes) {
            var world = TranslationSweep.Box.of(box, pose).bounds();
            envelope = envelope == null ? world : envelope.minmax(world);
        }
        if (envelope == null) envelope = new AABB(offset, offset.add(1.0, 1.0, 1.0));
        envelope = envelope.inflate(retreat + 0.5).expandTowards(0.0, liftHeight + 1.0, 0.0);
        var obstacles = new LevelExtractionObstacles(host, excluded, envelope, UNVERSIONED_TERRAIN, controlled);
        var result = ExtractionPlanBuilder.plan(limits, liftHeight, hoverTicks, UNVERSIONED_TERRAIN, geometry, obstacles);
        return result.status() == StructureOperation.Status.READY && !obstacles.current()
                ? new ExtractionPlanBuilder.Outcome(StructureOperation.Status.STALE, "capture_terrain_changed_during_preview",
                        List.of(), List.of(), UNVERSIONED_TERRAIN) : result;
    }

    /** Built-in actor restrictions are mandatory; the caller predicate can only restrict them. */
    private static <T> StructureOperation.@Nullable Result<T> permissionFailure(ServerLevel host, Entity actor,
                                                                              StructureCaptureRequest request) {
        if (actor instanceof ServerPlayer player && (player.isSpectator() || !player.getAbilities().mayBuild)) {
            return failure(StructureOperation.Status.DENIED, "capture_actor_cannot_build");
        }
        for (var position : request.positions()) {
            if (!host.isInWorldBounds(position) || !host.getWorldBorder().isWithinBounds(position)) {
                return failure(StructureOperation.Status.DENIED, "capture_source_out_of_world");
            }
            if (host.getChunkSource().getChunkNow(position.getX() >> 4, position.getZ() >> 4) == null) {
                return failure(StructureOperation.Status.TERRAIN_PENDING, "capture_source_chunk_unloaded");
            }
            try {
                if (!actor.mayInteract(host, position) || !host.mayInteract(actor, position)
                        || !request.permission().test(host, position)) {
                    return failure(StructureOperation.Status.DENIED, "capture_source_permission_denied");
                }
            } catch (RuntimeException denied) {
                return failure(StructureOperation.Status.DENIED, "capture_source_permission_unavailable");
            }
        }
        return null;
    }

    private static CapturePlanRegistry registry(MinecraftServer server) {
        return REGISTRIES.computeIfAbsent(server, ignored -> new CapturePlanRegistry());
    }

    private static boolean actorAvailable(ServerLevel host, Entity actor) {
        return actor != null && actor.isAlive() && !actor.isRemoved() && actor.level() == host
                && !(actor instanceof net.minecraft.server.level.ServerPlayer player && player.hasDisconnected());
    }

    private static void requireThread(ServerLevel host) {
        Objects.requireNonNull(host, "host");
        if (!host.getServer().isSameThread()) throw new IllegalStateException("Extraction requires the server thread");
    }

    private static <T> StructureOperation.Result<T> failure(StructureOperation.Status status, String detail) {
        return new StructureOperation.Result<>(status, detail, Optional.empty(), Optional.empty());
    }
}
