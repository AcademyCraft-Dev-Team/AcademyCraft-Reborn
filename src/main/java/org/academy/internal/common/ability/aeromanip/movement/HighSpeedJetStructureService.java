package org.academy.internal.common.ability.aeromanip.movement;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.academy.AcademyCraft;
import org.academy.api.common.ability.aeromanip.JetStructureControl;
import org.academy.api.common.damage.SkillDamageSource;
import org.academy.api.common.structure.BlockStructureApi;
import org.academy.api.common.structure.BlockStructureCaptureOptions;
import org.academy.api.common.structure.BlockStructureKinetics;
import org.academy.api.common.structure.LegacyMotionUnits;
import org.academy.api.common.structure.StructureBackendStatus;
import org.academy.api.common.structure.StructureCaptureRequest;
import org.academy.api.common.structure.StructureMotionState;
import org.academy.api.common.structure.StructureLifecycle;
import org.academy.api.common.structure.StructurePhysics;
import org.academy.api.common.structure.StructurePhysicsControl;
import org.academy.api.common.structure.StructurePhysicsLease;
import org.academy.api.common.structure.StructurePhysicsRegion;
import org.academy.api.common.structure.StructureRef;
import org.academy.api.common.structure.StructureSpaces;
import org.academy.api.common.structure.StructureTransactions;
import org.academy.api.server.ability.AbilitySystemServer;
import org.academy.internal.common.ability.Skills;
import org.academy.internal.common.ability.aeromanip.skills.lv4.HighSpeedJet;
import org.academy.internal.common.ability.aeromanip.targeting.AeromanipTargeting;
import org.academy.internal.common.ability.program.ProgramPowerScale;
import org.academy.internal.common.entitycontrol.EntityMotionGuard;
import org.academy.internal.common.structure.StructureBackendRouter;
import org.academy.internal.common.world.entity.skill.HighSpeedJetNozzle;
import org.academy.internal.common.world.entity.structure.NativeStructureProjectionEntity;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.BiPredicate;

/** Shared server implementation used by High-Speed Jet and Aeromanip programs. */
public final class HighSpeedJetStructureService {
    public static final double DEFAULT_STRUCTURE_RADIUS = 2.0;
    public static final double MAXIMUM_STRUCTURE_RADIUS = 8.0;
    public static final int IMPACT_COOLDOWN_TICKS = 10;
    public static final double MINIMUM_IMPACT_SPEED = 0.35;

    private static final double DIRECTION_DOT_THRESHOLD = Math.cos(Math.toRadians(45.0));
    private static final int NATIVE_CELL_BUDGET = 128;
    private static final int SABLE_CELL_BUDGET = 256;
    private static final long CAPTURE_TIMEOUT_TICKS = 600;
    private static final int TRACTION_LEASE_TICKS = 20;
    private static final int REGION_UNAVAILABLE_GRACE_TICKS = 40;
    private static final double REGION_MARGIN = 6.0;
    private static final double FOLLOW_TRIGGER = 2.0;
    private static final double FOLLOW_LOOKAHEAD_SECONDS = 0.5;
    private static final double FOLLOW_TRAILING_MARGIN = 2.0;
    private static final double ANGULAR_MARGIN_SECONDS = 0.05;
    private static final double SUPPORT_TOLERANCE = 0.5;

    private static final Map<UUID, CaptureState> CAPTURES = new HashMap<>();
    private static final Map<UUID, Session> SESSIONS = new HashMap<>();

    private HighSpeedJetStructureService() {
    }

    public static int countOwnedNozzles(
            ServerPlayer player,
            Vec3 center,
            double radius,
            Vec3 desiredDirection
    ) {
        return candidateNozzles(player, center, radius, desiredDirection).size();
    }

    /** Public stage observation for integrations and validation; it cannot drive the skill. */
    public enum Stage {
        CAPTURING, AWAITING_PHYSICS, THRUSTING
    }

    public record Diagnostic(
            Stage stage,
            StructureRef reference,
            int blockCount,
            boolean controlActive,
            boolean sable,
            double speed,
            int impacts
    ) {
    }

    public static Optional<Diagnostic> diagnostic(ServerPlayer player) {
        requireThread(player);
        var capture = CAPTURES.get(player.getUUID());
        if (capture != null) {
            return Optional.of(new Diagnostic(Stage.CAPTURING, capture.reference,
                    capture.positions.size(), false, sable(capture.reference), 0.0, 0));
        }
        var session = SESSIONS.get(player.getUUID());
        if (session == null) return Optional.empty();
        return Optional.of(new Diagnostic(session.stage, session.reference, session.blockCount,
                session.controlActive(), sable(session.reference), session.launchSpeed, session.impactCount));
    }

    /**
     * Starts a structure launch through the backend-routed capture transaction. The returned
     * handle represents this launch attempt: closing it terminates the tracking/control state
     * while the captured structure, its inertia and its physics region are deliberately retained.
     * Failures log {@code status:detail}; asynchronous failures also report the skill message.
     */
    public static Optional<LaunchHandle> launch(
            ServerPlayer player,
            LaunchRequest request,
            float costMultiplier
    ) {
        requireThread(player);
        if (request == null
                || !(player.level() instanceof ServerLevel level)
                || !Skills.HIGH_SPEED_JET.get().isEnabled(player)
                || !Float.isFinite(costMultiplier) || costMultiplier <= 0.0f) {
            return Optional.empty();
        }
        var playerId = player.getUUID();
        // One structure action per player, matching the shared service contract: a new launch
        // first ends the previous tracking while retaining its already captured structure.
        var abandoned = CAPTURES.remove(playerId);
        if (abandoned != null) {
            AcademyCraft.LOGGER.info("High-Speed Jet capture {} was abandoned for a newer launch",
                    abandoned.transactionId);
        }
        stopSession(playerId, null, "replaced_by_new_launch");
        var plan = plan(player, request).orElse(null);
        if (plan == null) return Optional.empty();
        var budget = cellBudget(level);
        var positions = cropToBudget(plan.positions, request.seed, budget);
        if (positions.isEmpty() || !positions.contains(request.seed)) return Optional.empty();
        var origin = minimumCorner(positions);
        var nozzles = new ArrayList<NozzleMount>();
        for (var nozzle : plan.nozzles) {
            if (positions.contains(nozzle.supportPos())) {
                nozzles.add(new NozzleMount(nozzle, nozzle.supportPos().subtract(origin), nozzle.face()));
            }
        }
        if (nozzles.stream().noneMatch(mount -> mount.nozzle == plan.targetNozzle)) return Optional.empty();
        var result = StructureTransactions.capture(level, new StructureCaptureRequest(
                positions, budget, StructureCaptureRequest.MAXIMUM_SPAN, capturePermission(player)));
        if (result.pending()) {
            var reference = result.structure().orElse(null);
            if (reference == null) return Optional.empty();
            CAPTURES.put(playerId, new CaptureState(reference, result.transactionId(), level.dimension(),
                    level.getGameTime(), request, List.copyOf(nozzles), plan.targetNozzle,
                    plan.launchDirection, List.copyOf(positions), costMultiplier));
            return Optional.of(new LaunchHandle(playerId, result.transactionId()));
        }
        if (!result.succeeded()) {
            AcademyCraft.LOGGER.info("High-Speed Jet capture rejected for {}: {}:{}",
                    playerId, result.status(), result.detail());
            return Optional.empty();
        }
        var reference = result.structure().orElse(null);
        if (reference == null) return Optional.empty();
        var launchId = UUID.randomUUID();
        beginSession(player, launchId, reference, request, List.copyOf(nozzles), plan.targetNozzle,
                plan.launchDirection, List.copyOf(positions), costMultiplier);
        return Optional.of(new LaunchHandle(playerId, launchId));
    }

    static Optional<LaunchPlan> plan(ServerPlayer player, LaunchRequest request) {
        if (player == null || request == null
                || !(player.level() instanceof ServerLevel level)
                || !level.isInWorldBounds(request.seed)
                || !level.isLoaded(request.seed)) {
            return Optional.empty();
        }
        var targetNozzle = targetNozzle(player, request);
        if (targetNozzle == null) return Optional.empty();
        var launchDirection = HighSpeedJet.entityThrustDirection(targetNozzle.direction());
        var movementDirection = HighSpeedJet.nearestFace(launchDirection);
        var selectionCenter = request.seed.relative(movementDirection);
        var selection = BlockStructureApi.selectSphere(
                level,
                selectionCenter,
                request.structureRadius,
                capturePolicy(player)
        );
        if (!selection.succeeded()) return Optional.empty();
        var positions = BlockStructureApi.cropImmediatelyBlocked(
                level, selection.positions(), movementDirection);
        if (!positions.contains(request.seed)) return Optional.empty();
        var positionSet = Set.copyOf(positions);
        var nozzles = HighSpeedJet.Server.ownedNozzles(level, player).stream()
                .filter(nozzle -> !nozzle.isRemoved() && !nozzle.isEntityMounted())
                .filter(nozzle -> positionSet.contains(nozzle.supportPos()))
                .sorted(Comparator.comparingInt(Entity::getId))
                .toList();
        if (nozzles.stream().noneMatch(nozzle -> nozzle == targetNozzle)) {
            return Optional.empty();
        }
        return Optional.of(new LaunchPlan(
                List.copyOf(positions),
                nozzles,
                targetNozzle,
                launchDirection
        ));
    }

    /**
     * Deterministic pre-trim before a capture transaction: the seed cell is kept, remaining
     * cells are ranked by squared distance to the seed with coordinate tie-breaks, and at most
     * {@code budget} cells are returned. The backend enforces its own cell budget after this.
     */
    static List<BlockPos> cropToBudget(List<BlockPos> positions, BlockPos seed, int budget) {
        Objects.requireNonNull(positions, "positions");
        Objects.requireNonNull(seed, "seed");
        if (budget < 1) return List.of();
        var unique = new ArrayList<BlockPos>();
        var seen = new HashSet<BlockPos>();
        for (var position : positions) {
            var immutable = Objects.requireNonNull(position, "position").immutable();
            if (seen.add(immutable)) unique.add(immutable);
        }
        unique.sort(Comparator
                .comparingDouble((BlockPos position) -> position.distSqr(seed))
                .thenComparingInt(BlockPos::getX)
                .thenComparingInt(BlockPos::getY)
                .thenComparingInt(BlockPos::getZ));
        var kept = new ArrayList<BlockPos>(Math.min(budget, unique.size()));
        var immutableSeed = seed.immutable();
        if (seen.contains(immutableSeed)) kept.add(immutableSeed);
        for (var position : unique) {
            if (kept.size() >= budget) break;
            if (position.equals(immutableSeed)) continue;
            kept.add(position);
        }
        return List.copyOf(kept);
    }

    /**
     * Smallest usable physics region around the captured cells, with the launch direction's
     * axis extended inside the host room so one rebase covers a full calibrated step.
     */
    static AABB regionBounds(
            List<BlockPos> positions,
            Vec3 movementDirection,
            double minimumY,
            double maximumY,
            AABB limits
    ) {
        Objects.requireNonNull(positions, "positions");
        if (positions.isEmpty() || movementDirection == null) return null;
        int minX = Integer.MAX_VALUE, minY = Integer.MAX_VALUE, minZ = Integer.MAX_VALUE;
        int maxX = Integer.MIN_VALUE, maxY = Integer.MIN_VALUE, maxZ = Integer.MIN_VALUE;
        for (var position : positions) {
            minX = Math.min(minX, position.getX()); maxX = Math.max(maxX, position.getX());
            minY = Math.min(minY, position.getY()); maxY = Math.max(maxY, position.getY());
            minZ = Math.min(minZ, position.getZ()); maxZ = Math.max(maxZ, position.getZ());
        }
        double[] lower = {minX, minY, minZ};
        double[] upper = {maxX + 1.0, maxY + 1.0, maxZ + 1.0};
        double[] limitLower = {limits.minX, minimumY, limits.minZ};
        double[] limitUpper = {limits.maxX, maximumY, limits.maxZ};
        var axis = dominantAxis(movementDirection);
        var component = axis == 0 ? movementDirection.x
                : axis == 1 ? movementDirection.y : movementDirection.z;
        for (int index = 0; index < 3; index++) {
            double size = upper[index] - lower[index];
            double limitSize = limitUpper[index] - limitLower[index];
            if (!Double.isFinite(size) || size <= 0 || size > limitSize) return null;
            if (index == axis) {
                double maximumExtension = Math.min(
                        StructurePhysics.MAX_REGION_AXIS - size, limitSize - size);
                double forwardRoom = component >= 0
                        ? limitUpper[index] - upper[index]
                        : lower[index] - limitLower[index];
                double backwardRoom = component >= 0
                        ? lower[index] - limitLower[index]
                        : limitUpper[index] - upper[index];
                double front = Math.min(forwardRoom, maximumExtension / 2.0);
                double back = Math.min(backwardRoom, maximumExtension - front);
                front = Math.min(forwardRoom, maximumExtension - back);
                if (component >= 0) {
                    lower[index] -= back;
                    upper[index] += front;
                } else {
                    lower[index] -= front;
                    upper[index] += back;
                }
            } else {
                double sideRoom = Math.min(
                        lower[index] - limitLower[index], limitUpper[index] - upper[index]);
                double margin = Math.min(Math.min(REGION_MARGIN,
                        (StructurePhysics.MAX_REGION_AXIS - size) / 2.0), sideRoom);
                lower[index] -= margin;
                upper[index] += margin;
            }
        }
        var bounds = new AABB(lower[0], lower[1], lower[2], upper[0], upper[1], upper[2]);
        return bounds.getXsize() <= StructurePhysics.MAX_REGION_AXIS
                && bounds.getYsize() <= StructurePhysics.MAX_REGION_AXIS
                && bounds.getZsize() <= StructurePhysics.MAX_REGION_AXIS ? bounds : null;
    }

    static float impactDamage(int blockCount, double closingSpeed, float power) {
        return BlockStructureKinetics.collisionDamage(blockCount, closingSpeed)
                * ProgramPowerScale.damageMultiplier(power);
    }

    static double knockbackStrength(int blockCount, double closingSpeed, float power) {
        return BlockStructureKinetics.collisionKnockback(blockCount, closingSpeed)
                * ProgramPowerScale.effectMultiplier(power);
    }

    /** Per-player driver; must run once on the owning server thread. */
    public static void tick(ServerPlayer player) {
        requireThread(player);
        var playerId = player.getUUID();
        tickCapture(player);
        var session = SESSIONS.get(playerId);
        if (session == null) return;
        if (!session.usable(player)) {
            stopSession(playerId, session.launchId, "owner_or_structure_unavailable");
            return;
        }
        if (--session.remainingTicks <= 0) {
            stopSession(playerId, session.launchId, "duration_elapsed");
            return;
        }
        switch (session.stage) {
            case AWAITING_PHYSICS -> tickAwait(player, session);
            case THRUSTING -> tickThrust(player, session);
            case CAPTURING -> throw new IllegalStateException("A held session cannot be capturing");
        }
    }

    /** Silent lifecycle cleanup; the captured structure, inertia and region are retained. */
    public static void clear(ServerPlayer player) {
        if (player == null) return;
        var playerId = player.getUUID();
        var capture = CAPTURES.remove(playerId);
        if (capture != null) {
            AcademyCraft.LOGGER.info("High-Speed Jet capture {} tracking cleared at a player lifecycle boundary",
                    capture.transactionId);
        }
        stopSession(playerId, null, "player_lifecycle");
    }

    /** Server shutdown cleanup; never throws into the stopping server. */
    public static void clearAll() {
        for (var playerId : List.copyOf(SESSIONS.keySet())) {
            try {
                stopSession(playerId, null, "server_stopped");
            } catch (RuntimeException failure) {
                AcademyCraft.LOGGER.warn("High-Speed Jet shutdown cleanup failed for {}", playerId, failure);
            }
        }
        CAPTURES.clear();
        SESSIONS.clear();
    }

    private static void tickCapture(ServerPlayer player) {
        var playerId = player.getUUID();
        var capture = CAPTURES.get(playerId);
        if (capture == null) return;
        if (!(player.level() instanceof ServerLevel level)
                || !capture.dimension.equals(player.level().dimension())
                || !player.isAlive() || player.hasDisconnected()) {
            CAPTURES.remove(playerId);
            return;
        }
        var result = StructureTransactions.poll(level.getServer(), capture.transactionId);
        if (result.isEmpty()) {
            CAPTURES.remove(playerId);
            reject(player, "transaction_evicted");
            return;
        }
        var current = result.get();
        if (current.pending()) {
            if (level.getGameTime() - capture.startedAt > CAPTURE_TIMEOUT_TICKS) {
                CAPTURES.remove(playerId);
                AcademyCraft.LOGGER.warn(
                        "High-Speed Jet capture {} exceeded its tracking window; structure retained",
                        capture.transactionId);
                reject(player, "transaction_tracking_timeout");
            }
            return;
        }
        CAPTURES.remove(playerId);
        if (!current.succeeded()) {
            reject(player, current.status() + ":" + current.detail());
            return;
        }
        beginSession(player, capture.transactionId,
                current.structure().orElse(capture.reference), capture.request,
                capture.nozzles, capture.targetNozzle, capture.launchDirection,
                capture.positions, capture.costMultiplier);
    }

    private static void beginSession(
            ServerPlayer player,
            UUID launchId,
            StructureRef reference,
            LaunchRequest request,
            List<NozzleMount> nozzles,
            HighSpeedJetNozzle targetNozzle,
            Vec3 launchDirection,
            List<BlockPos> positions,
            float costMultiplier
    ) {
        if (!(player.level() instanceof ServerLevel level)) return;
        if (sable(reference)) {
            // The shared service has no Sable launch/flight entry yet. The captured structure is
            // retained instead of being mutated through an unsupported path or a legacy entity.
            attachNozzles(player.getUUID(), reference, nozzles);
            AcademyCraft.LOGGER.info("High-Speed Jet sable launch is not implemented; retaining {}", reference);
            reject(player, "sable_launch_unavailable");
            return;
        }
        var handle = StructureSpaces.resolve(level.getServer(), reference).orElse(null);
        if (handle == null || handle.lifecycle() != StructureLifecycle.ACTIVE) {
            reject(player, "captured_handle_unavailable");
            return;
        }
        var limits = new AABB(level.getWorldBorder().getMinX(), level.getMinY(), level.getWorldBorder().getMinZ(),
                level.getWorldBorder().getMaxX(), level.getMaxY(), level.getWorldBorder().getMaxZ());
        var bounds = regionBounds(positions, launchDirection, level.getMinY(), level.getMaxY(), limits);
        if (bounds == null) {
            reject(player, "region_cannot_fit_structure");
            return;
        }
        StructurePhysicsRegion region;
        try {
            region = StructurePhysics.openRegion(level, bounds);
        } catch (RuntimeException | AssertionError failure) {
            AcademyCraft.LOGGER.error("High-Speed Jet could not open a physics region for {}", reference, failure);
            reject(player, "region_open_failed");
            return;
        }
        try {
            region.attach(handle);
        } catch (RuntimeException | AssertionError failure) {
            AcademyCraft.LOGGER.error("High-Speed Jet could not attach {}", reference, failure);
            try {
                region.close();
            } catch (RuntimeException cleanup) {
                failure.addSuppressed(cleanup);
            }
            reject(player, "region_attach_failed");
            return;
        }
        var blockCount = positions.size();
        var powerCost = ProgramPowerScale.costMultiplier(request.power);
        var cpCost = (HighSpeedJet.activationCpCost(1)
                + blockCount * 0.25f) * powerCost * costMultiplier;
        var airCost = (HighSpeedJet.activationAirCost(1)
                + blockCount * 0.5f) * powerCost;
        var started = new boolean[1];
        var executed = HighSpeedJet.Server.executeWithResources(
                player,
                cpCost,
                airCost,
                () -> started[0] = true
        );
        if (!executed || !started[0]) {
            try {
                region.close();
            } catch (RuntimeException cleanup) {
                AcademyCraft.LOGGER.warn("High-Speed Jet region cleanup failed for {}", reference, cleanup);
            }
            reject(player, "activation_rejected");
            return;
        }
        attachNozzles(player.getUUID(), reference, nozzles);
        var previousTicks = new HashMap<HighSpeedJetNozzle, Integer>();
        for (var mount : nozzles) {
            previousTicks.put(mount.nozzle, mount.nozzle.activeTicks());
        }
        targetNozzle.activate(request.durationTicks);
        var session = new Session(launchId, reference, level.dimension(), request,
                previousTicks, targetNozzle, launchDirection, blockCount, region);
        SESSIONS.put(player.getUUID(), session);
        AcademyCraft.LOGGER.info("High-Speed Jet launch started for {} with {} blocks", reference, blockCount);
    }

    private static void tickAwait(ServerPlayer player, Session session) {
        var region = session.region;
        if (region.status() == StructurePhysicsRegion.Status.FAILED) {
            AcademyCraft.LOGGER.error("High-Speed Jet region failed before thrust: {}", region.diagnostic());
            stopSession(player.getUUID(), session.launchId, "region_failed");
            return;
        }
        if (region.status() != StructurePhysicsRegion.Status.READY) {
            if (++session.graceTicks > REGION_UNAVAILABLE_GRACE_TICKS) {
                stopSession(player.getUUID(), session.launchId, "region_unavailable");
            }
            return;
        }
        var motion = region.motion(session.reference).orElse(null);
        if (motion == null) {
            if (++session.graceTicks > REGION_UNAVAILABLE_GRACE_TICKS) {
                stopSession(player.getUUID(), session.launchId, "motion_unavailable");
            }
            return;
        }
        session.graceTicks = 0;
        var force = JetStructureControl.limitedThrustForce(session.launchDirection,
                motion.linearVelocity(), motion.mass(), session.request.power(), milestone(player));
        Optional<StructurePhysicsLease> acquired;
        try {
            acquired = region.acquireEntityControl(session.reference, player, session.controllerId,
                    new StructurePhysicsControl.Force(motion.localCenterOfMass(), force),
                    TRACTION_LEASE_TICKS);
        } catch (IllegalStateException | IllegalArgumentException rejection) {
            AcademyCraft.LOGGER.warn("High-Speed Jet thrust control was rejected for {}",
                    session.reference, rejection);
            stopSession(player.getUUID(), session.launchId, "control_rejected");
            return;
        }
        if (acquired.isEmpty()) {
            AcademyCraft.LOGGER.warn("High-Speed Jet control slot is occupied for {}", session.reference);
            stopSession(player.getUUID(), session.launchId, "control_occupied");
            return;
        }
        session.lease = acquired.orElseThrow();
        session.stage = Stage.THRUSTING;
    }

    private static void tickThrust(ServerPlayer player, Session session) {
        var region = session.region;
        if (region.status() == StructurePhysicsRegion.Status.FAILED) {
            AcademyCraft.LOGGER.error("High-Speed Jet region failed during thrust: {}", region.diagnostic());
            stopSession(player.getUUID(), session.launchId, "region_failed");
            return;
        }
        if (region.status() != StructurePhysicsRegion.Status.READY) {
            if (++session.graceTicks > REGION_UNAVAILABLE_GRACE_TICKS) {
                stopSession(player.getUUID(), session.launchId, "region_unavailable");
            }
            return;
        }
        var motion = region.motion(session.reference).orElse(null);
        if (motion == null) {
            if (++session.graceTicks > REGION_UNAVAILABLE_GRACE_TICKS) {
                stopSession(player.getUUID(), session.launchId, "motion_unavailable");
            }
            return;
        }
        session.graceTicks = 0;
        if (!nozzleGate(player, session)) {
            stopSession(player.getUUID(), session.launchId, "nozzle_ended");
            return;
        }
        var lease = session.lease;
        if (lease == null || lease.state() != StructurePhysicsLease.State.ACTIVE) {
            stopSession(player.getUUID(), session.launchId, "lease_ended");
            return;
        }
        try {
            var force = JetStructureControl.limitedThrustForce(session.launchDirection,
                    motion.linearVelocity(), motion.mass(), session.request.power(), milestone(player));
            lease.setControl(new StructurePhysicsControl.Force(motion.localCenterOfMass(), force));
            lease.renew(TRACTION_LEASE_TICKS);
        } catch (IllegalStateException | IllegalArgumentException rejection) {
            AcademyCraft.LOGGER.warn("High-Speed Jet thrust lease ended for {}", session.reference, rejection);
            stopSession(player.getUUID(), session.launchId, "lease_rejected");
            return;
        }
        session.launchSpeed = Math.max(session.launchSpeed, motion.linearVelocity().length());
        followRegion(player, session, motion);
        applyImpacts(player, session, motion);
    }

    private static boolean nozzleGate(ServerPlayer player, Session session) {
        var nozzle = session.targetNozzle;
        return !nozzle.isRemoved()
                && nozzle.activeTicks() > 0
                && nozzle.isOwnedBy(player)
                && nozzle.isAttachedTo(session.reference);
    }

    private static void followRegion(ServerPlayer player, Session session, StructureMotionState motion) {
        if (!(player.level() instanceof ServerLevel level)) return;
        var region = session.region;
        var projection = projection(level, session.reference, region.bounds().inflate(16.0));
        if (projection == null) return;
        var occupied = union(projection.worldCollisionBoxes());
        if (occupied == null) return;
        var halfDiagonal = Math.sqrt(occupied.getXsize() * occupied.getXsize()
                + occupied.getYsize() * occupied.getYsize()
                + occupied.getZsize() * occupied.getZsize()) / 2.0;
        var predicted = occupied.move(motion.linearVelocity().scale(0.05))
                .inflate(motion.angularVelocity().length() * halfDiagonal * ANGULAR_MARGIN_SECONDS);
        var bounds = region.bounds();
        if (edgeDistance(bounds, predicted) >= FOLLOW_TRIGGER) return;
        var limits = new AABB(level.getWorldBorder().getMinX(), level.getMinY(), level.getWorldBorder().getMinZ(),
                level.getWorldBorder().getMaxX(), level.getMaxY(), level.getWorldBorder().getMaxZ());
        var candidate = followBounds(bounds, predicted, motion.linearVelocity(), FOLLOW_LOOKAHEAD_SECONDS,
                FOLLOW_TRAILING_MARGIN, level.getMinY(), level.getMaxY(), limits);
        if (candidate == null || candidate.equals(bounds) || !tryRebind(region, candidate)) {
            // A body that can no longer be kept inside a rebaseable region would fail the region
            // terminally on the next step. Ending the thrust keeps the retained structure.
            var conservative = followBounds(bounds, occupied, motion.linearVelocity(), FOLLOW_LOOKAHEAD_SECONDS,
                    FOLLOW_TRAILING_MARGIN, level.getMinY(), level.getMaxY(), limits);
            if (conservative == null || conservative.equals(bounds) || !tryRebind(region, conservative)) {
                AcademyCraft.LOGGER.warn("High-Speed Jet region follow could not rebase {}; ending thrust safely",
                        session.reference);
                stopSession(player.getUUID(), session.launchId, "follow_rebase_failed");
            }
        }
    }

    private static boolean tryRebind(StructurePhysicsRegion region, AABB candidate) {
        try {
            return region.rebind(candidate);
        } catch (RuntimeException | AssertionError failure) {
            AcademyCraft.LOGGER.error("High-Speed Jet region rebase failed", failure);
            return false;
        }
    }

    private static void applyImpacts(ServerPlayer player, Session session, StructureMotionState motion) {
        if (!(player.level() instanceof ServerLevel level)) return;
        var projection = projection(level, session.reference, session.region.bounds().inflate(16.0));
        if (projection == null) return;
        var boxes = projection.worldCollisionBoxes();
        if (boxes.isEmpty()) return;
        var union = union(boxes);
        if (union == null) return;
        var now = level.getGameTime();
        var targets = new ArrayList<Entity>();
        for (var target : level.getEntities(player, union.inflate(1.5))) {
            if (target == projection || target.noPhysics
                    || !AeromanipTargeting.canAffectNegatively(player, target)) continue;
            var last = session.impactTicks.get(target.getUUID());
            if (last != null && now - last < IMPACT_COOLDOWN_TICKS) continue;
            var targetBox = target.getBoundingBox();
            if (projection.supports(targetBox, SUPPORT_TOLERANCE)) continue;
            if (boxes.stream().noneMatch(box -> box.intersects(targetBox))) continue;
            targets.add(target);
        }
        for (var target : targets) settleImpact(player, session, projection, target, motion);
        session.impactTicks.entrySet().removeIf(entry -> now - entry.getValue() > IMPACT_COOLDOWN_TICKS * 4L);
    }

    private static void settleImpact(
            ServerPlayer player,
            Session session,
            NativeStructureProjectionEntity direct,
            Entity target,
            StructureMotionState motion
    ) {
        var level = (ServerLevel) player.level();
        var center = motion.pose().localToWorldPoint(motion.localCenterOfMass());
        var offset = target.getBoundingBox().getCenter().subtract(center);
        var closingPerTick = 0.0;
        if (offset.lengthSqr() > 1.0e-8) {
            var relative = motion.linearVelocity()
                    .subtract(LegacyMotionUnits.velocityPerSecond(target.getDeltaMovement()));
            closingPerTick = LegacyMotionUnits.velocityPerTick(
                    Math.max(0.0, relative.dot(offset.normalize())));
        }
        var damage = impactDamage(session.blockCount, closingPerTick, session.request.power());
        var system = AbilitySystemServer.getSystem(player);
        damage *= system.getPlayerAbilityPowerMultiplier(player.getUUID())
                * system.getPlayerDamageMultiplier(player.getUUID());
        if (target instanceof LivingEntity living && damage > 0.0f) {
            var damaged = living.hurtServer(
                    level,
                    SkillDamageSource.ofDirect(player, Skills.HIGH_SPEED_JET.get(), direct),
                    damage);
            if (!damaged) return;
            Skills.HIGH_SPEED_JET.get().onHurt(player, living, damage);
        }
        var forceMultiplier = AeromanipTargeting.forceMultiplier(player, target);
        var knockback = knockbackStrength(session.blockCount, closingPerTick, session.request.power())
                * forceMultiplier;
        var movement = motion.linearVelocity();
        if (knockback > 0.0 && movement.lengthSqr() > 1.0e-8) {
            var impulse = movement.normalize().scale(knockback).add(0.0, 0.08, 0.0);
            EntityMotionGuard.runWithMotionSource(player, () ->
                    AeromanipTargeting.addClampedVelocity(target, impulse));
        }
        session.impactTicks.put(target.getUUID(), level.getGameTime());
        session.impactCount = Math.incrementExact(session.impactCount);
    }

    private static NativeStructureProjectionEntity projection(
            ServerLevel level,
            StructureRef reference,
            AABB area
    ) {
        for (var entity : level.getEntitiesOfClass(NativeStructureProjectionEntity.class, area)) {
            if (!entity.isRemoved() && entity.viewState().reference().equals(reference)) return entity;
        }
        return null;
    }

    private static AABB union(List<AABB> boxes) {
        AABB result = null;
        for (var box : boxes) result = result == null ? box : result.minmax(box);
        return result;
    }

    private static double edgeDistance(AABB bounds, AABB box) {
        return Math.min(Math.min(box.minX - bounds.minX, bounds.maxX - box.maxX),
                Math.min(Math.min(box.minY - bounds.minY, bounds.maxY - box.maxY),
                        Math.min(box.minZ - bounds.minZ, bounds.maxZ - box.maxZ)));
    }

    private static AABB followBounds(
            AABB region,
            AABB occupied,
            Vec3 velocity,
            double lookaheadSeconds,
            double trailingMargin,
            double minimumY,
            double maximumY,
            AABB limits
    ) {
        double sizeX = region.getXsize(), sizeY = region.getYsize(), sizeZ = region.getZsize();
        if (limits.getXsize() < sizeX || limits.getZsize() < sizeZ || maximumY - minimumY < sizeY) return null;
        var center = occupied.getCenter().add(
                limitedShift(velocity.x * lookaheadSeconds, sizeX, occupied.getXsize(), trailingMargin),
                limitedShift(velocity.y * lookaheadSeconds, sizeY, occupied.getYsize(), trailingMargin),
                limitedShift(velocity.z * lookaheadSeconds, sizeZ, occupied.getZsize(), trailingMargin));
        double x = Mth.clamp(center.x, limits.minX + sizeX / 2.0, limits.maxX - sizeX / 2.0);
        double y = Mth.clamp(center.y, minimumY + sizeY / 2.0, maximumY - sizeY / 2.0);
        double z = Mth.clamp(center.z, limits.minZ + sizeZ / 2.0, limits.maxZ - sizeZ / 2.0);
        var candidate = new AABB(x - sizeX / 2.0, y - sizeY / 2.0, z - sizeZ / 2.0,
                x + sizeX / 2.0, y + sizeY / 2.0, z + sizeZ / 2.0);
        return contains(candidate, occupied) ? candidate : null;
    }

    private static double limitedShift(double desired, double regionSize, double boxSize, double trailing) {
        double maximum = Math.max(0, (regionSize - boxSize) / 2.0 - trailing);
        return Mth.clamp(desired, -maximum, maximum);
    }

    private static boolean contains(AABB outer, AABB inner) {
        return outer.minX <= inner.minX && outer.minY <= inner.minY && outer.minZ <= inner.minZ
                && outer.maxX >= inner.maxX && outer.maxY >= inner.maxY && outer.maxZ >= inner.maxZ;
    }

    private static int dominantAxis(Vec3 direction) {
        var x = Math.abs(direction.x);
        var y = Math.abs(direction.y);
        var z = Math.abs(direction.z);
        if (y >= x && y >= z) return 1;
        return x >= z ? 0 : 2;
    }

    private static void attachNozzles(UUID ownerId, StructureRef reference, List<NozzleMount> mounts) {
        for (var mount : mounts) {
            if (mount.nozzle.isRemoved()) continue;
            try {
                mount.nozzle.attachToStructure(ownerId, reference, mount.supportOffset, mount.face);
            } catch (IllegalArgumentException rejection) {
                AcademyCraft.LOGGER.warn("High-Speed Jet could not attach a nozzle to {}",
                        reference, rejection);
            }
        }
    }

    private static void stopSession(UUID playerId, @Nullable UUID launchId, String detail) {
        var session = SESSIONS.get(playerId);
        if (session == null || launchId != null && !session.launchId.equals(launchId)) return;
        SESSIONS.remove(playerId);
        session.closeControl();
        session.previousNozzleTicks.forEach((nozzle, ticks) -> {
            if (!nozzle.isRemoved()) nozzle.restoreActiveTicks(ticks);
        });
        AcademyCraft.LOGGER.info("High-Speed Jet session ended for {}: {}", session.reference, detail);
    }

    private static void reject(ServerPlayer player, String detail) {
        AcademyCraft.LOGGER.info("High-Speed Jet launch rejected for {}: {}", player.getUUID(), detail);
        player.sendOverlayMessage(Component.translatable(
                "message.academy.high_speed_jet.structure_launch_rejected"));
    }

    private static int milestone(ServerPlayer player) {
        return Skills.HIGH_SPEED_JET.get().getEffectiveProficiencyMilestone(player);
    }

    private static boolean sable(StructureRef reference) {
        return StructureBackendRouter.SABLE_ID.equals(reference.backend());
    }

    private static HighSpeedJetNozzle targetNozzle(
            ServerPlayer player,
            LaunchRequest request
    ) {
        if (!(player.level() instanceof ServerLevel level)) return null;
        var desiredDirection = normalizeNullable(request.desiredDirection);
        return HighSpeedJet.Server.ownedNozzles(level, player).stream()
                .filter(nozzle -> !nozzle.isRemoved() && !nozzle.isEntityMounted())
                .filter(nozzle -> nozzle.supportPos().equals(request.seed))
                .filter(nozzle -> desiredDirection == null
                        || HighSpeedJet.entityThrustDirection(nozzle.direction())
                        .dot(desiredDirection) >= DIRECTION_DOT_THRESHOLD)
                .sorted(Comparator
                        .comparing((HighSpeedJetNozzle nozzle) ->
                                request.nozzleFace != null
                                        && nozzle.face() == request.nozzleFace)
                        .reversed()
                        .thenComparingInt(Entity::getId))
                .findFirst()
                .orElse(null);
    }

    private static List<HighSpeedJetNozzle> candidateNozzles(
            ServerPlayer player,
            Vec3 center,
            double radius,
            Vec3 desiredDirection
    ) {
        if (player == null || center == null || !finite(center)
                || !Double.isFinite(radius) || radius < 1.0 || radius > 32.0
                || !(player.level() instanceof ServerLevel level)) {
            return List.of();
        }
        var normalizedDirection = normalizeNullable(desiredDirection);
        var radiusSquared = radius * radius;
        var result = new ArrayList<HighSpeedJetNozzle>();
        for (var nozzle : HighSpeedJet.Server.ownedNozzles(level, player)) {
            if (nozzle.isRemoved() || nozzle.isEntityMounted()
                    || nozzle.position().distanceToSqr(center) > radiusSquared) continue;
            if (normalizedDirection != null
                    && nozzle.direction().dot(normalizedDirection) < DIRECTION_DOT_THRESHOLD) {
                continue;
            }
            result.add(nozzle);
        }
        result.sort(Comparator.comparingInt(Entity::getId));
        return List.copyOf(result);
    }

    private static BlockStructureCaptureOptions.CapturePolicy capturePolicy(ServerPlayer player) {
        return (level, position, state, blockEntity) ->
                level.mayInteract(player, position)
                        && blockEntity == null
                        && BlockStructureCaptureOptions.CapturePolicy.MOVABLE.canCapture(
                        level, position, state, null);
    }

    private static BiPredicate<ServerLevel, BlockPos> capturePermission(ServerPlayer player) {
        return (level, position) -> level.mayInteract(player, position);
    }

    private static int cellBudget(ServerLevel level) {
        return sable(level) ? SABLE_CELL_BUDGET : NATIVE_CELL_BUDGET;
    }

    private static boolean sable(ServerLevel level) {
        return StructureBackendRouter.status(level.getServer()).mode() == StructureBackendStatus.Mode.SABLE;
    }

    private static BlockPos minimumCorner(List<BlockPos> positions) {
        var minimumX = Integer.MAX_VALUE;
        var minimumY = Integer.MAX_VALUE;
        var minimumZ = Integer.MAX_VALUE;
        for (var position : positions) {
            minimumX = Math.min(minimumX, position.getX());
            minimumY = Math.min(minimumY, position.getY());
            minimumZ = Math.min(minimumZ, position.getZ());
        }
        return new BlockPos(minimumX, minimumY, minimumZ);
    }

    private static Vec3 normalizeNullable(Vec3 direction) {
        if (!finite(direction) || direction.lengthSqr() <= 1.0e-8) return null;
        return direction.normalize();
    }

    private static boolean finite(Vec3 value) {
        return value != null
                && Double.isFinite(value.x)
                && Double.isFinite(value.y)
                && Double.isFinite(value.z);
    }

    private static void requireThread(ServerPlayer player) {
        Objects.requireNonNull(player, "player");
        if (!player.level().getServer().isSameThread()) {
            throw new IllegalStateException("High-Speed Jet requires the owning server thread");
        }
    }

    public record LaunchRequest(
            BlockPos seed,
            Direction nozzleFace,
            Vec3 desiredDirection,
            double structureRadius,
            float power,
            int durationTicks,
            boolean restoreWhenSettled
    ) {
        public LaunchRequest {
            if (seed == null) throw new IllegalArgumentException("seed cannot be null");
            seed = seed.immutable();
            if (!Double.isFinite(structureRadius)
                    || structureRadius < DEFAULT_STRUCTURE_RADIUS
                    || structureRadius > MAXIMUM_STRUCTURE_RADIUS) {
                throw new IllegalArgumentException("structureRadius must be between "
                        + DEFAULT_STRUCTURE_RADIUS + " and " + MAXIMUM_STRUCTURE_RADIUS);
            }
            ProgramPowerScale.require(power);
            if (durationTicks < 1 || durationTicks > 60 * 20) {
                throw new IllegalArgumentException("durationTicks must be between 1 and 1200");
            }
            if (desiredDirection != null && normalizeNullable(desiredDirection) == null) {
                throw new IllegalArgumentException("desiredDirection is invalid");
            }
        }
    }

    static record LaunchPlan(
            List<BlockPos> positions,
            List<HighSpeedJetNozzle> nozzles,
            HighSpeedJetNozzle targetNozzle,
            Vec3 launchDirection
    ) {
    }

    private static final class CaptureState {
        private final StructureRef reference;
        private final UUID transactionId;
        private final ResourceKey<Level> dimension;
        private final long startedAt;
        private final LaunchRequest request;
        private final List<NozzleMount> nozzles;
        private final HighSpeedJetNozzle targetNozzle;
        private final Vec3 launchDirection;
        private final List<BlockPos> positions;
        private final float costMultiplier;

        private CaptureState(
                StructureRef reference,
                UUID transactionId,
                ResourceKey<Level> dimension,
                long startedAt,
                LaunchRequest request,
                List<NozzleMount> nozzles,
                HighSpeedJetNozzle targetNozzle,
                Vec3 launchDirection,
                List<BlockPos> positions,
                float costMultiplier
        ) {
            this.reference = reference;
            this.transactionId = transactionId;
            this.dimension = dimension;
            this.startedAt = startedAt;
            this.request = request;
            this.nozzles = nozzles;
            this.targetNozzle = targetNozzle;
            this.launchDirection = launchDirection;
            this.positions = positions;
            this.costMultiplier = costMultiplier;
        }
    }

    private record NozzleMount(
            HighSpeedJetNozzle nozzle,
            BlockPos supportOffset,
            Direction face
    ) {
    }

    /** Launch attempt capability; closing it terminates tracking/control and retains the structure. */
    public static final class LaunchHandle implements AutoCloseable {
        private final UUID playerId;
        private final UUID launchId;
        private boolean closed;

        private LaunchHandle(UUID playerId, UUID launchId) {
            this.playerId = playerId;
            this.launchId = launchId;
        }

        public UUID launchId() {
            return launchId;
        }

        @Override
        public void close() {
            if (closed) return;
            closed = true;
            var capture = CAPTURES.get(playerId);
            if (capture != null && capture.transactionId.equals(launchId)) {
                CAPTURES.remove(playerId);
            }
            stopSession(playerId, launchId, "handle_closed");
        }
    }

    private static final class Session {
        private final UUID launchId;
        private final StructureRef reference;
        private final ResourceKey<Level> dimension;
        private final LaunchRequest request;
        private final Map<HighSpeedJetNozzle, Integer> previousNozzleTicks;
        private final HighSpeedJetNozzle targetNozzle;
        private final Vec3 launchDirection;
        private final int blockCount;
        private final UUID controllerId = UUID.randomUUID();
        private final StructurePhysicsRegion region;
        private final Map<UUID, Long> impactTicks = new HashMap<>();
        private Stage stage = Stage.AWAITING_PHYSICS;
        private StructurePhysicsLease lease;
        private int remainingTicks;
        private int graceTicks;
        private int impactCount;
        private double launchSpeed;

        private Session(
                UUID launchId,
                StructureRef reference,
                ResourceKey<Level> dimension,
                LaunchRequest request,
                Map<HighSpeedJetNozzle, Integer> previousNozzleTicks,
                HighSpeedJetNozzle targetNozzle,
                Vec3 launchDirection,
                int blockCount,
                StructurePhysicsRegion region
        ) {
            this.launchId = launchId;
            this.reference = reference;
            this.dimension = dimension;
            this.request = request;
            this.previousNozzleTicks = Map.copyOf(previousNozzleTicks);
            this.targetNozzle = targetNozzle;
            this.launchDirection = launchDirection;
            this.blockCount = blockCount;
            this.region = region;
            remainingTicks = request.durationTicks();
        }

        private boolean controlActive() {
            return lease != null && lease.state() == StructurePhysicsLease.State.ACTIVE;
        }

        private boolean usable(ServerPlayer player) {
            if (!dimension.equals(player.level().dimension())
                    || !player.isAlive() || player.hasDisconnected()
                    || !Skills.HIGH_SPEED_JET.get().isEnabled(player)) {
                return false;
            }
            return StructureSpaces.resolve(player.level().getServer(), reference)
                    .map(handle -> handle.lifecycle() == StructureLifecycle.ACTIVE)
                    .orElse(false);
        }

        private void closeControl() {
            try {
                if (lease != null) lease.close();
            } catch (RuntimeException failure) {
                AcademyCraft.LOGGER.warn("High-Speed Jet lease cleanup failed for {}", reference, failure);
            } finally {
                lease = null;
            }
        }
    }
}
