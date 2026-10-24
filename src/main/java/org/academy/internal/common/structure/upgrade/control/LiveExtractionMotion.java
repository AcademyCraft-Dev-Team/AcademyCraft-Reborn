package org.academy.internal.common.structure.upgrade.control;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.academy.AcademyCraft;
import org.academy.api.common.structure.StructureDirectControl;
import org.academy.api.common.structure.StructurePose;
import org.academy.api.common.structure.StructureRef;
import org.academy.api.common.structure.upgrade.StructureExtraction;
import org.academy.api.common.structure.upgrade.StructureOperation;
import org.academy.api.common.structure.upgrade.StructureOrientation;
import org.academy.internal.common.structure.StructureBackendRouter;

import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/** Server-owned extraction receipts. Only the solver hook may advance or correct velocity. */
final class LiveExtractionMotion {
    private static final int MAX_PLANS = 64;
    private static final int MAX_RECEIPTS = 256;
    private static final Map<MinecraftServer, Registry> SERVERS = new IdentityHashMap<>();

    private LiveExtractionMotion() {}

    static StructureOperation.Result<StructureExtraction.Plan> save(StructureOperation.Context context,
                                                                     StructureExtraction.Plan plan) {
        var registry = registry(context.host().getServer());
        prune(registry);
        if (registry.plans.size() >= MAX_PLANS) return failure(StructureOperation.Status.CONFLICT, "extraction_plan_capacity");
        registry.plans.put(plan.planId(), new SavedPlan(context, plan));
        return new StructureOperation.Result<>(StructureOperation.Status.READY, "extraction_plan_ready",
                Optional.empty(), Optional.of(plan));
    }

    static Optional<StructureExtraction.Plan> plan(StructureOperation.Context context, UUID control, UUID id) {
        var registry = SERVERS.get(context.host().getServer());
        var saved = registry == null ? null : registry.plans.get(id);
        return saved != null && saved.context.equals(context) && saved.plan.request().controlId().equals(control)
                ? Optional.of(saved.plan) : Optional.empty();
    }

    static StructureOperation.Result<StructureExtraction.Snapshot> repeated(StructureOperation.Context context,
                                                                            StructureExtraction.BeginRequest request) {
        var registry = SERVERS.get(context.host().getServer());
        if (registry == null) return null;
        for (var entry : registry.receipts.values()) {
            if (entry.plan.planId().equals(request.planId())) {
                if (!entry.context.equals(context) || !entry.control().equals(request.controlId())) {
                    return failure(StructureOperation.Status.DENIED, "extraction_plan_foreign_actor");
                }
                if (entry.finishedAt < 0 && authorized(context, request.controlId()).isEmpty()) {
                    finish(registry, entry, StructureOperation.Status.STALE, StructureExtraction.Phase.EXPIRED,
                            "extraction_authority_lost");
                }
                return entry.result;
            }
        }
        return null;
    }

    static StructureOperation.Result<StructureExtraction.Snapshot> begin(StructureOperation.Context context,
                                                                          StructureExtraction.Plan plan) {
        var registry = registry(context.host().getServer());
        prune(registry);
        if (registry.active.containsKey(context.reference()) || LiveStructureOrientation.controls(context.host(), context.reference())) {
            return failure(StructureOperation.Status.CONFLICT, "extraction_control_busy");
        }
        if (registry.receipts.size() >= MAX_RECEIPTS || registry.active.size() >= MAX_PLANS) {
            return failure(StructureOperation.Status.CONFLICT, "extraction_receipt_capacity");
        }
        var access = StructureBackendRouter.orientationAccess(context.host(), context.reference()).orElse(null);
        var body = authorized(context, plan.request().controlId()).orElse(null);
        if (access == null || body == null) return failure(StructureOperation.Status.STALE, "extraction_authority_lost");
        if (!body.body().terrainReady()) return failure(StructureOperation.Status.TERRAIN_PENDING, "extraction_terrain_pending");
        if (!plan.reference().equals(context.reference()) || !plan.runtimeId().equals(context.runtimeId())
                || plan.contentRevision() != context.expectedContentRevision()
                || plan.poseRevision() != body.body().poseRevision() || context.host().getGameTime() > plan.expiresAtGameTime()) {
            return failure(StructureOperation.Status.STALE, "extraction_plan_stale");
        }
        var entry = new Entry(context, plan, access, body.body());
        access.cancelQueuedTranslation(context.host(), context.reference(), plan.request().controlId());
        registry.active.put(context.reference(), entry);
        registry.receipts.put(entry.id, entry);
        return entry.result;
    }

    static StructureOperation.Result<StructureExtraction.Snapshot> status(StructureOperation.Context context, UUID control, UUID id) {
        var registry = SERVERS.get(context.host().getServer());
        var entry = owned(registry, context, control, id);
        if (entry == null) return failure(StructureOperation.Status.STALE, "extraction_receipt_unavailable");
        if (entry.finishedAt < 0 && authorized(context, control).isEmpty()) {
            finish(registry, entry, StructureOperation.Status.STALE, StructureExtraction.Phase.EXPIRED, "extraction_authority_lost");
        }
        return entry.result;
    }

    static StructureOperation.Result<StructureExtraction.Snapshot> cancel(StructureOperation.Context context, UUID control, UUID id) {
        var registry = SERVERS.get(context.host().getServer());
        var entry = owned(registry, context, control, id);
        if (entry == null) return failure(StructureOperation.Status.STALE, "extraction_receipt_unavailable");
        if (entry.finishedAt < 0) finish(registry, entry, StructureOperation.Status.COMPLETED,
                StructureExtraction.Phase.CANCELLED, "extraction_cancelled");
        return entry.result;
    }

    static boolean hasActive(ServerLevel host, boolean sable) {
        var registry = SERVERS.get(host.getServer());
        return registry != null && registry.active.values().stream().anyMatch(entry -> entry.context.host() == host
                && entry.sable == sable);
    }

    static boolean controls(ServerLevel host, StructureRef reference) {
        return active(host, reference) != null;
    }

    static boolean controls(ServerLevel host, StructureRef reference, UUID control) {
        var entry = active(host, reference);
        return entry != null && entry.control().equals(control);
    }

    static AABB terrainBounds(ServerLevel host, StructureRef reference, AABB bounds, double duration) {
        var entry = active(host, reference);
        if (entry == null) return bounds;
        var limits = entry.plan.request().limits();
        return bounds.inflate(limits.maxLinearSpeed() * duration + limits.maxLinearAcceleration() * duration * duration);
    }

    static void cancel(ServerLevel host, StructureRef reference, UUID control) {
        var entry = active(host, reference);
        if (entry != null && entry.control().equals(control)) finish(SERVERS.get(host.getServer()), entry,
                StructureOperation.Status.COMPLETED, StructureExtraction.Phase.CANCELLED, "extraction_cancelled");
    }

    static void cancel(StructureRef reference, UUID control) {
        for (var registry : SERVERS.values()) {
            var entry = registry.active.get(reference);
            if (entry != null && entry.control().equals(control)) finish(registry, entry,
                    StructureOperation.Status.STALE, StructureExtraction.Phase.EXPIRED, "extraction_lease_revoked");
        }
    }

    static void launched(ServerLevel host, StructureRef reference) {
        var entry = active(host, reference);
        if (entry != null) finish(SERVERS.get(host.getServer()), entry, StructureOperation.Status.COMPLETED,
                StructureExtraction.Phase.RELEASED, "extraction_launched");
    }

    static void beforeSubstep(ServerLevel host, boolean sable, double seconds) {
        var registry = SERVERS.get(host.getServer());
        if (registry == null || registry.active.isEmpty()) return;
        long sequence = ++registry.sequence;
        int[] remainingCells = {65_536};
        for (var entry : List.copyOf(registry.active.values())) {
            if (entry.context.host() != host || entry.sable != sable) continue;
            try {
                advance(registry, entry, seconds, sequence, remainingCells);
            } catch (RuntimeException | LinkageError failed) {
                finish(registry, entry, StructureOperation.Status.BLOCKED, StructureExtraction.Phase.BLOCKED,
                        "extraction_backend_failure");
                AcademyCraft.LOGGER.warn("Structure extraction {} failed for {}", entry.id, entry.context.reference(), failed);
            }
        }
    }

    /** Clears only permitted soft material, once per body/tick, before either solver snapshots terrain. */
    static void beforeTerrain(MinecraftServer server) {
        var registry = SERVERS.get(server);
        if (registry == null || registry.active.isEmpty()) return;
        if (!server.isSameThread()) throw new IllegalStateException("Extraction clearance requires server thread");
        int tick = server.getTickCount();
        for (var entry : List.copyOf(registry.active.values())) {
            if (entry.lastSoftClearTick == tick) continue;
            entry.lastSoftClearTick = tick;
            try {
                var linear = authorized(entry.context, entry.control()).orElse(null);
                if (linear == null || !linear.body().terrainReady()) {
                    if (CaptureSoftClearance.traceEnabled()) AcademyCraft.LOGGER.info(
                            "EXTRACTION_PRE_TERRAIN_SKIP tick={} body={} reason={}", tick, entry.context.reference(),
                            linear == null ? "authority_unavailable" : "terrain_unavailable");
                    continue;
                }
                var body = linear.body();
                var center = body.motion().pose().localToWorldPoint(body.motion().localCenterOfMass());
                var displacement = ExtractionController.clearanceDisplacement(center, entry.centerPath.get(entry.waypoint),
                        body.linearVelocity(), entry.plan.request().limits());
                var angular = OrientationController.rotate(body.motion(), entry.targetRotation, entry.angularLimits, .05);
                var rotation = OrientationTargets.integrateRotation(body.motion().pose(), body.motion().localCenterOfMass(),
                        angular.nextAngularVelocity(), .05);
                var nextPose = new StructurePose(rotation.worldOrigin().add(displacement), rotation.rotation());
                boolean loaded = loadedClearanceEnvelope(entry.context.host(), body, displacement);
                if (CaptureSoftClearance.traceEnabled()) AcademyCraft.LOGGER.info(
                        "EXTRACTION_PRE_TERRAIN tick={} gameTick={} operation={} body={} pose={} next={} velocity={} angular={} displacement={} loaded={} waypoint={} target={}",
                        tick, entry.context.host().getGameTime(), entry.id, body.reference(), body.motion().pose(), nextPose,
                        body.linearVelocity(), body.motion().angularVelocity(), displacement, loaded, entry.waypoint,
                        entry.centerPath.get(entry.waypoint));
                if (nextPose.equals(body.motion().pose()) || !loaded) continue;
                int destroyed = CaptureSoftClearance.clearMotion(entry.context.host(), entry.context.actor(), body, nextPose);
                if (CaptureSoftClearance.traceEnabled()) AcademyCraft.LOGGER.info(
                        "EXTRACTION_PRE_TERRAIN_END tick={} operation={} destroyed={}", tick, entry.id, destroyed);
                if (destroyed > 0) {
                    entry.hoverSince = -1; entry.hoverTicks = 0;
                    entry.result = result(entry, StructureOperation.Status.TERRAIN_PENDING,
                            StructureExtraction.Phase.PREPARING, "extraction_soft_obstacles_prepared");
                }
            } catch (RuntimeException | LinkageError unavailable) {
                // Optional block callbacks cannot stop preparation for the remaining bodies.
                // No velocity or pose has been changed; substep admission still fails closed.
                AcademyCraft.LOGGER.debug("Extraction soft-clearance unavailable for {}", entry.context.reference(), unavailable);
            }
        }
    }

    private static boolean loadedClearanceEnvelope(ServerLevel host, OrientationBackendAccess.Body body, Vec3 displacement) {
        var center = body.motion().pose().localToWorldPoint(body.motion().localCenterOfMass());
        double radius = OrientationWorldSnapshot.radius(body.localBoxes(), body.motion().localCenterOfMass());
        var bounds = AABB.ofSize(center, radius * 2, radius * 2, radius * 2).expandTowards(displacement);
        int minX = ((int) Math.floor(bounds.minX)) >> 4, maxX = ((int) Math.floor(bounds.maxX)) >> 4;
        int minZ = ((int) Math.floor(bounds.minZ)) >> 4, maxZ = ((int) Math.floor(bounds.maxZ)) >> 4;
        long chunks = (long) (maxX - minX + 1) * (maxZ - minZ + 1);
        if (chunks <= 0 || chunks > 256) return false;
        for (int x = minX; x <= maxX; x++) for (int z = minZ; z <= maxZ; z++) {
            if (host.getChunkSource().getChunkNow(x, z) == null) return false;
        }
        return true;
    }

    private static void advance(Registry registry, Entry entry, double seconds, long sequence, int[] cells) {
        var observed = authorized(entry.context, entry.control()).orElse(null);
        if (observed == null) {
            finish(registry, entry, StructureOperation.Status.STALE, StructureExtraction.Phase.EXPIRED, "extraction_authority_lost");
            return;
        }
        var body = observed.body();
        if (CaptureSoftClearance.traceEnabled()) AcademyCraft.LOGGER.info(
                "EXTRACTION_SUBSTEP_OBSERVED tick={} gameTick={} sequence={} operation={} body={} pose={} velocity={} angular={} content={} poseRevision={} ready={} mass={} seconds={}",
                entry.context.host().getServer().getTickCount(), entry.context.host().getGameTime(), sequence, entry.id,
                body.reference(), body.motion().pose(), body.linearVelocity(), body.motion().angularVelocity(), body.contentRevision(),
                body.poseRevision(), body.terrainReady(), observed.mass(), seconds);
        if (CaptureSoftClearance.traceEnabled() && !entry.geometryTraced) {
            entry.geometryTraced = true;
            AcademyCraft.LOGGER.info("EXTRACTION_GEOMETRY operation={} body={} localCOM={} localBoxes={}",
                    entry.id, body.reference(), body.motion().localCenterOfMass(), body.localBoxes());
        }
        if (!body.terrainReady()) {
            entry.hoverSince = -1; entry.hoverTicks = 0;
            entry.result = result(entry, StructureOperation.Status.TERRAIN_PENDING, StructureExtraction.Phase.PREPARING,
                    "extraction_terrain_pending");
            return;
        }
        var center = body.motion().pose().localToWorldPoint(body.motion().localCenterOfMass());
        entry.rise = center.y - entry.sourceCenter.y;
        var path = entry.centerPath;
        while (entry.waypoint < path.size() - 1 && center.distanceTo(path.get(entry.waypoint)) <= 0.025
                && body.linearVelocity().length() <= ExtractionController.SPEED_TOLERANCE) entry.waypoint++;
        var target = path.get(entry.waypoint);
        var step = ExtractionController.step(center, body.linearVelocity(), body.freeAcceleration(),
                observed.mass(), target, entry.plan.request().limits(), seconds);
        var angular = OrientationController.rotate(body.motion(), entry.targetRotation, entry.angularLimits, seconds);
        boolean arrived = entry.waypoint == path.size() - 1 && step.stable() && angular.angleError() <= .01
                && body.motion().angularVelocity().length() <= .05;
        long now = entry.context.host().getGameTime();
        if (arrived) {
            if (entry.arrivedAt < 0) entry.arrivedAt = now;
            if (entry.hoverSince < 0) entry.hoverSince = now;
            entry.hoverTicks = (int) Math.min(entry.plan.request().hoverTicks(), now - entry.hoverSince);
        } else { entry.hoverSince = -1; entry.hoverTicks = 0; }
        // The next velocity, not the old standing velocity, defines queried terrain coverage.
        var projected = new OrientationBackendAccess.Body(body.reference(), body.runtimeId(), body.contentRevision(),
                body.poseRevision(), new OrientationController.State(body.motion().pose(), body.motion().localCenterOfMass(),
                angular.nextAngularVelocity(), body.motion().maximumInertia()), step.velocity(), body.freeAcceleration(), body.localBoxes(),
                body.baselines(), body.terrainReady());
        var world = new OrientationWorldSnapshot(entry.context.host(), projected, sequence, seconds, cells);
        var clearance = sweep(projected, step.displacement(), seconds, sequence,
                entry.plan.request().limits().maxShapeTests(), world, entry.access.positionRounding(body));
        if (clearance != OrientationSweep.Status.CLEAR) {
            if (clearance == OrientationSweep.Status.BLOCKED
                    && entry.lastSoftClearTick != entry.context.host().getServer().getTickCount()) {
                // A command begun after the Pre event gets at most one fallback; ongoing
                // operations already consumed this tick's clearance budget before terrain built.
                entry.lastSoftClearTick = entry.context.host().getServer().getTickCount();
                var rotation = OrientationTargets.integrateRotation(body.motion().pose(), body.motion().localCenterOfMass(),
                        angular.nextAngularVelocity(), seconds);
                var nextPose = new StructurePose(rotation.worldOrigin().add(step.displacement()), rotation.rotation());
                if (CaptureSoftClearance.clearMotion(entry.context.host(), entry.context.actor(), body, nextPose) > 0) {
                    // Break callbacks may change both world and authority. Do not apply this
                    // command or reuse the old collision snapshot after any successful write.
                    entry.hoverSince = -1; entry.hoverTicks = 0;
                    entry.result = result(entry, StructureOperation.Status.TERRAIN_PENDING,
                            StructureExtraction.Phase.PREPARING, "extraction_soft_obstacles_changed");
                    return;
                }
            }
            if (clearance == OrientationSweep.Status.TERRAIN_PENDING) {
                entry.hoverSince = -1;
                entry.hoverTicks = 0;
                entry.result = result(entry, StructureOperation.Status.TERRAIN_PENDING, StructureExtraction.Phase.PREPARING,
                        "extraction_terrain_pending");
                return;
            }
            finish(registry, entry, clearance == OrientationSweep.Status.STALE ? StructureOperation.Status.STALE
                    : StructureOperation.Status.BLOCKED, StructureExtraction.Phase.BLOCKED, "extraction_" + clearance.name().toLowerCase(java.util.Locale.ROOT));
            return;
        }
        if (!world.current() || authorized(entry.context, entry.control()).filter(current ->
                observed.mass() == current.mass() && body.sameObservation(current.body())).isEmpty()
                || !entry.access.applyMotion(entry.context.host(), observed, step.velocity(), angular.nextAngularVelocity())) {
            finish(registry, entry, StructureOperation.Status.STALE, StructureExtraction.Phase.EXPIRED, "extraction_observation_changed");
            return;
        }
        if (CaptureSoftClearance.traceEnabled()) AcademyCraft.LOGGER.info(
                "EXTRACTION_SUBSTEP_APPLIED tick={} sequence={} operation={} velocity={} angular={} displacement={} force={} torque={}",
                entry.context.host().getServer().getTickCount(), sequence, entry.id, step.velocity(), angular.nextAngularVelocity(),
                step.displacement(), step.force(), angular.maximumAppliedTorque());
        if (arrived && entry.hoverTicks >= entry.plan.request().hoverTicks()) {
            finish(registry, entry, StructureOperation.Status.COMPLETED, StructureExtraction.Phase.RELEASED, "extraction_hover_completed");
            return;
        }
        entry.result = result(entry, StructureOperation.Status.RUNNING, arrived ? StructureExtraction.Phase.HOVERING
                : StructureExtraction.Phase.EXTRACTING, arrived ? "extraction_hovering" : "extraction_moving");
    }

    /** Exact fixed-angle translation avoids inflating an upward segment sideways into its socket. */
    static OrientationSweep.Status sweep(OrientationBackendAccess.Body body, Vec3 displacement, double seconds,
                                         long sequence, int maximumTests, ExtractionPlanner.ObstacleSnapshot world) {
        return sweep(body, displacement, seconds, sequence, maximumTests, world, Vec3.ZERO);
    }

    static OrientationSweep.Status sweep(OrientationBackendAccess.Body body, Vec3 displacement, double seconds,
                                         long sequence, int maximumTests, ExtractionPlanner.ObstacleSnapshot world,
                                         Vec3 positionRounding) {
        // Reserve half the shared test budget for a tighter proof when the angular enclosure
        // touches the socket. A broad enclosure hit alone is not proof of actual collision.
        int strictBudget = Math.max(1, maximumTests / 2);
        var strict = strictSweep(body, displacement, seconds, sequence, strictBudget, world);
        if (strict != OrientationSweep.Status.BLOCKED) return strict;
        var contact = ExtractionContactSweep.check(body, displacement, seconds, sequence,
                maximumTests - strictBudget, world, positionRounding);
        if (contact.status() == OrientationSweep.Status.BLOCKED) {
            AcademyCraft.LOGGER.debug("Extraction contact blocked body={} pose={} omega={} displacement={} reason={}",
                    body.reference(), body.motion().pose(), body.motion().angularVelocity(), displacement, contact.detail());
        }
        return contact.status();
    }

    private static OrientationSweep.Status strictSweep(OrientationBackendAccess.Body body, Vec3 displacement, double seconds,
                                                       long sequence, int maximumTests, ExtractionPlanner.ObstacleSnapshot world) {
        if (body.motion().angularVelocity().lengthSqr() > 0) {
            return OrientationSweep.check(body.localBoxes(), body.motion().pose(), body.motion().localCenterOfMass(),
                    body.motion().angularVelocity(), displacement, body.freeStepMargin(seconds), seconds,
                    sequence, maximumTests, world).status();
        }
        int used = 0;
        for (var local : body.localBoxes()) {
            // Continuous/subdivided gravity can travel above the velocity-first endpoint
            // segment. A one-sided local enclosure covers that curve without inventing
            // penetration below a structure which is leaving a supporting ground face.
            var gravityEnvelope = body.motion().pose().worldToLocalDirection(
                    body.freeAcceleration().scale(-0.5 * seconds * seconds));
            var box = TranslationSweep.Box.of(local.expandTowards(gravityEnvelope), body.motion().pose());
            if (++used > maximumTests) return OrientationSweep.Status.BUDGET_EXHAUSTED;
            var query = world.query(box.sweptBounds(displacement), maximumTests - used);
            if (query.terrainRevision() != sequence) return OrientationSweep.Status.STALE;
            if (query.coverage() != ExtractionPlanner.Coverage.COMPLETE) return OrientationSweep.Status.TERRAIN_PENDING;
            if (query.truncated() || query.obstacles().size() > maximumTests - used) return OrientationSweep.Status.BUDGET_EXHAUSTED;
            for (var obstacle : query.obstacles()) {
                used++;
                if (TranslationSweep.firstHit(box, displacement, obstacle.box()).isPresent()) return OrientationSweep.Status.BLOCKED;
            }
        }
        return OrientationSweep.Status.CLEAR;
    }

    static void afterTick(MinecraftServer server) {
        var registry = SERVERS.get(server);
        if (registry == null) return;
        for (var entry : List.copyOf(registry.active.values())) {
            if (authorized(entry.context, entry.control()).isEmpty()) {
                finish(registry, entry, StructureOperation.Status.STALE, StructureExtraction.Phase.EXPIRED, "extraction_authority_lost");
                continue;
            }
            long deadline = entry.arrivedAt < 0 ? entry.createdTick + entry.plan.request().extractionTimeoutTicks()
                    : entry.arrivedAt + entry.plan.request().hoverTicks() + entry.plan.request().extractionTimeoutTicks();
            if (entry.context.host().getGameTime() > deadline) finish(registry, entry, StructureOperation.Status.BLOCKED,
                    StructureExtraction.Phase.EXPIRED, "extraction_timeout");
        }
        prune(registry);
    }

    static void stopped(MinecraftServer server) { SERVERS.remove(server); }

    static Optional<OrientationBackendAccess.LinearBody> authorized(StructureOperation.Context context, UUID control) {
        var host = context.host();
        var actor = context.actor();
        if (!actor.isAlive() || actor.isRemoved() || actor.level() != host
                || actor instanceof ServerPlayer player && player.hasDisconnected()
                || host.getServer().getLevel(host.dimension()) != host
                || !context.reference().dimension().equals(host.dimension().identifier())
                || !StructureDirectControl.matches(context.reference(), actor.getUUID(), control, context.runtimeId())) return Optional.empty();
        var body = StructureBackendRouter.orientationAccess(host, context.reference())
                .flatMap(access -> access.observeLinear(host, context.reference()));
        if (body.isEmpty() || !body.get().body().runtimeId().equals(context.runtimeId())
                || body.get().body().contentRevision() != context.expectedContentRevision()) return Optional.empty();
        if (body.get().body().terrainReady() && StructureDirectControl.validate(host, context.reference(), actor, control)
                != StructureDirectControl.Status.ACTIVE) return Optional.empty();
        return body;
    }

    private static Entry active(ServerLevel host, StructureRef reference) {
        var registry = SERVERS.get(host.getServer());
        var entry = registry == null ? null : registry.active.get(reference);
        return entry != null && entry.context.host() == host ? entry : null;
    }

    private static Entry owned(Registry registry, StructureOperation.Context context, UUID control, UUID id) {
        var entry = registry == null ? null : registry.receipts.get(id);
        return entry != null && entry.context.equals(context) && entry.control().equals(control) ? entry : null;
    }

    private static void finish(Registry registry, Entry entry, StructureOperation.Status status,
                                StructureExtraction.Phase phase, String detail) {
        entry.result = result(entry, status, phase, detail);
        registry.active.remove(entry.context.reference(), entry);
        entry.finishedAt = entry.context.host().getServer().getTickCount();
    }

    private static StructureOperation.Result<StructureExtraction.Snapshot> result(Entry entry, StructureOperation.Status status,
                                                                                 StructureExtraction.Phase phase, String detail) {
        return new StructureOperation.Result<>(status, detail, Optional.of(entry.id), Optional.of(
                new StructureExtraction.Snapshot(entry.id, entry.control(), phase, entry.rise, entry.hoverTicks,
                        Math.max(0, entry.context.host().getGameTime() - entry.createdTick), Optional.empty())));
    }

    private static <T> StructureOperation.Result<T> failure(StructureOperation.Status status, String detail) {
        return new StructureOperation.Result<>(status, detail, Optional.empty(), Optional.empty());
    }

    private static Registry registry(MinecraftServer server) { return SERVERS.computeIfAbsent(server, ignored -> new Registry()); }

    private static void prune(Registry registry) {
        registry.plans.values().removeIf(saved -> saved.context.host().getGameTime() > saved.plan.expiresAtGameTime());
        registry.receipts.values().removeIf(entry -> entry.finishedAt >= 0
                && entry.context.host().getServer().getTickCount() - entry.finishedAt > 1_200);
    }

    private record SavedPlan(StructureOperation.Context context, StructureExtraction.Plan plan) {}

    private static final class Registry {
        final Map<UUID, SavedPlan> plans = new LinkedHashMap<>();
        final Map<UUID, Entry> receipts = new LinkedHashMap<>();
        final Map<StructureRef, Entry> active = new LinkedHashMap<>();
        long sequence;
    }

    private static final class Entry {
        final UUID id = UUID.randomUUID();
        final StructureOperation.Context context;
        final StructureExtraction.Plan plan;
        final OrientationBackendAccess access;
        final List<Vec3> centerPath;
        final Vec3 sourceCenter;
        final long createdTick;
        final boolean sable;
        final StructurePose targetRotation;
        final StructureOrientation.Limits angularLimits;
        StructureOperation.Result<StructureExtraction.Snapshot> result;
        int waypoint = 1;
        long hoverSince = -1;
        long arrivedAt = -1;
        int hoverTicks;
        double rise;
        int finishedAt = -1;
        int lastSoftClearTick = -1;
        boolean geometryTraced;

        Entry(StructureOperation.Context context, StructureExtraction.Plan plan, OrientationBackendAccess access,
              OrientationBackendAccess.Body initial) {
            this.context = context; this.plan = plan; this.access = access;
            createdTick = context.host().getGameTime();
            sourceCenter = plan.request().sourcePose().localToWorldPoint(plan.request().localCenterOfMass());
            // A NEW_CAPTURE baseline was authenticated by binding; existing plans required
            // sourcePose to equal the live observation. Neither accepts an arbitrary reset pose.
            targetRotation = new StructurePose(Vec3.ZERO, plan.request().sourcePose().rotation());
            double radius = OrientationWorldSnapshot.radius(initial.localBoxes(), initial.motion().localCenterOfMass());
            angularLimits = new StructureOrientation.Limits(2, 8,
                    Math.min(10_000, plan.request().limits().maxForce() * Math.max(0.01, radius)),
                    plan.request().extractionTimeoutTicks(), 1E-10, 0.05, 0, plan.request().limits().maxShapeTests());
            var centerOffset = initial.motion().pose().localToWorldDirection(initial.motion().localCenterOfMass());
            centerPath = plan.worldPath().stream().map(origin -> origin.add(centerOffset)).toList();
            if (centerPath.size() < 2) throw new IllegalArgumentException("Extraction requires a verified path");
            sable = context.reference().backend().equals(StructureBackendRouter.SABLE_ID);
            result = result(this, StructureOperation.Status.RUNNING, StructureExtraction.Phase.PREPARING, "extraction_queued");
        }

        UUID control() { return plan.request().controlId(); }
    }
}
