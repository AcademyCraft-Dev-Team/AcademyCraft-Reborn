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
import org.academy.api.common.structure.StructureDirectControl;
import org.academy.api.common.structure.StructureDynamics;
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
    /** Shared idle/engagement window of one precision program session in server ticks. */
    public static final int PROGRAM_SESSION_TICKS = 600;
    public static final long MAXIMUM_PROGRAM_MOVE_TICKS = 600L;
    public static final long MAXIMUM_PROGRAM_LAUNCH_TICKS = 1200L;
    public static final int MAXIMUM_PROGRAM_BUDGET = 256;
    public static final double MAXIMUM_PROGRAM_MOVE_RANGE = 32.0;
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
        CAPTURING, AWAITING_PHYSICS, THRUSTING, SABLE_DIRECT
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

    /** Explicit program outcome codes; callers branch on status/detail, never on log text. */
    public enum ProgramStatus {
        COMPLETED, PENDING, REJECTED, UNAVAILABLE
    }

    public record ProgramResult(ProgramStatus status, String detail, @Nullable StructureRef reference) {
        public static ProgramResult completed(String detail, @Nullable StructureRef reference) {
            return new ProgramResult(ProgramStatus.COMPLETED, detail, reference);
        }

        public static ProgramResult pending(String detail) {
            return new ProgramResult(ProgramStatus.PENDING, detail, null);
        }

        public static ProgramResult rejected(String detail) {
            return new ProgramResult(ProgramStatus.REJECTED, detail, null);
        }

        public static ProgramResult unavailable(String detail) {
            return new ProgramResult(ProgramStatus.UNAVAILABLE, detail, null);
        }
    }

    /**
     * Program extraction entry: the same nozzle-hosted capture transaction as the skill path
     * without skill messages or session resource charging. The nozzle support cell must resolve
     * to a captured structure cell; a pending capture publishes no reference and every rejection
     * keeps the source blocks. The committed session waits for an explicit program control.
     */
    public static ProgramResult programExtract(
            ServerPlayer player,
            BlockPos seed,
            double radius,
            int budget
    ) {
        requireThread(player);
        Objects.requireNonNull(seed, "seed");
        if (!Double.isFinite(radius)
                || radius < DEFAULT_STRUCTURE_RADIUS || radius > MAXIMUM_STRUCTURE_RADIUS) {
            return ProgramResult.rejected("invalid_radius");
        }
        if (budget < 0 || budget > MAXIMUM_PROGRAM_BUDGET) {
            return ProgramResult.rejected("invalid_budget");
        }
        if (!(player.level() instanceof ServerLevel level)
                || !Skills.HIGH_SPEED_JET.get().isEnabled(player)) {
            return ProgramResult.rejected("host_unavailable");
        }
        var request = new LaunchRequest(seed, null, null, radius, 1.0f,
                PROGRAM_SESSION_TICKS, false);
        var plan = plan(player, request).orElse(null);
        if (plan == null) return ProgramResult.rejected("plan_unavailable");
        var effectiveBudget = budget == 0
                ? cellBudget(level) : Math.min(budget, cellBudget(level));
        var positions = cropToBudget(plan.positions, seed, effectiveBudget);
        if (positions.isEmpty() || !positions.contains(seed)) {
            return ProgramResult.rejected("budget_excludes_seed");
        }
        var origin = minimumCorner(positions);
        var nozzles = new ArrayList<NozzleMount>();
        for (var nozzle : plan.nozzles) {
            if (positions.contains(nozzle.supportPos())) {
                nozzles.add(new NozzleMount(nozzle,
                        nozzle.supportPos().subtract(origin), nozzle.face()));
            }
        }
        if (nozzles.stream().noneMatch(mount -> mount.nozzle == plan.targetNozzle)) {
            return ProgramResult.rejected("support_unresolved");
        }
        var playerId = player.getUUID();
        var abandoned = CAPTURES.remove(playerId);
        if (abandoned != null) {
            AcademyCraft.LOGGER.info(
                    "High-Speed Jet capture {} was abandoned for a newer program extract",
                    abandoned.transactionId);
        }
        stopSession(playerId, null, "replaced_by_program_extract");
        var result = StructureTransactions.capture(level, new StructureCaptureRequest(
                positions, effectiveBudget, StructureCaptureRequest.MAXIMUM_SPAN,
                capturePermission(player)));
        if (result.pending()) {
            var reference = result.structure().orElse(null);
            if (reference == null) return ProgramResult.rejected("pending_without_reference");
            CAPTURES.put(playerId, new CaptureState(reference, result.transactionId(),
                    level.dimension(), level.getGameTime(), request, List.copyOf(nozzles),
                    plan.targetNozzle, plan.launchDirection, List.copyOf(positions), 1.0f, true));
            return ProgramResult.pending(result.status() + ":" + result.detail());
        }
        if (!result.succeeded()) {
            return ProgramResult.rejected(result.status() + ":" + result.detail());
        }
        var reference = result.structure().orElse(null);
        if (reference == null) return ProgramResult.rejected("committed_without_reference");
        if (!beginProgramSession(player, reference, request, nozzles, plan.targetNozzle,
                plan.launchDirection, positions, level)) {
            return ProgramResult.rejected("session_start_failed");
        }
        return ProgramResult.completed("committed", reference);
    }

    /**
     * Program control entry. An existing program session of this player for the same structure is
     * activated; a committed Sable body without a session receives the lightweight direct-velocity
     * lease instead of a physics region. Another actor's session is rejected, never stolen, and a
     * replaced body epoch or an unavailable region is reported explicitly.
     */
    public static ProgramResult programControl(
            ServerPlayer player,
            StructureRef reference,
            UUID runtimeId
    ) {
        requireThread(player);
        Objects.requireNonNull(reference, "reference");
        Objects.requireNonNull(runtimeId, "runtimeId");
        var playerId = player.getUUID();
        var held = SESSIONS.get(playerId);
        if (held != null && !held.reference.equals(reference)) {
            // One structure action per player; adopting a new body first ends the previous
            // session while retaining that structure, its region and its inertia.
            stopSession(playerId, null, "replaced_by_program_control");
            held = null;
        }
        for (var entry : SESSIONS.entrySet()) {
            if (!entry.getKey().equals(playerId) && entry.getValue().reference.equals(reference)) {
                return ProgramResult.rejected("structure_controlled");
            }
        }
        for (var other : CAPTURES.values()) {
            if (other.reference.equals(reference)) return ProgramResult.rejected("structure_capturing");
        }
        if (held != null) {
            // A skill session thrusts with its own fixed direction and window; a program taking it
            // over could only partially honor move/launch, so it is rejected explicitly instead of
            // silently sharing semantics.
            if (!held.program) return ProgramResult.rejected("skill_session_active");
            if (held.directControl) {
                if (!(player.level() instanceof ServerLevel level)) {
                    return ProgramResult.rejected("host_unavailable");
                }
                var status = StructureDirectControl.validate(
                        level, reference, player, held.controllerId);
                if (status == StructureDirectControl.Status.ACTIVE) {
                    return ProgramResult.completed("control_active", reference);
                }
                stopSession(playerId, held.launchId, "control_lease_lost");
                return ProgramResult.rejected(status == StructureDirectControl.Status.OCCUPIED
                        ? "structure_controlled" : "structure_unavailable");
            }
            if (!(player.level() instanceof ServerLevel level)) {
                return ProgramResult.rejected("host_unavailable");
            }
            var handle = StructureSpaces.resolve(level.getServer(), reference).orElse(null);
            if (handle == null || handle.lifecycle() != StructureLifecycle.ACTIVE) {
                return ProgramResult.rejected("structure_unavailable");
            }
            if (!handle.runtimeId().equals(runtimeId)) {
                return ProgramResult.rejected("stale_structure_epoch");
            }
            if (held.region == null
                    || held.region.status() == StructurePhysicsRegion.Status.FAILED) {
                return ProgramResult.rejected("region_unavailable");
            }
            if (held.region.motion(reference).isEmpty()) {
                return ProgramResult.rejected("motion_unavailable");
            }
            if (held.controlled) return ProgramResult.completed("control_active", reference);
            held.controlled = true;
            return ProgramResult.completed("control_acquired", reference);
        }
        if (!(player.level() instanceof ServerLevel level)) {
            return ProgramResult.rejected("host_unavailable");
        }
        if (!sable(reference)) return ProgramResult.rejected("no_matching_session");
        var handle = StructureSpaces.resolve(level.getServer(), reference).orElse(null);
        if (handle == null || handle.lifecycle() != StructureLifecycle.ACTIVE) {
            return ProgramResult.rejected("structure_unavailable");
        }
        if (!handle.runtimeId().equals(runtimeId)) {
            return ProgramResult.rejected("stale_structure_epoch");
        }
        if (StructureDynamics.motion(level, reference).isEmpty()) {
            return ProgramResult.rejected("motion_unavailable");
        }
        var session = Session.programSable(reference, level.dimension(), null, List.of(), null,
                Vec3.ZERO, 0);
        var status = StructureDirectControl.acquire(
                level, reference, player, session.controllerId);
        if (status == StructureDirectControl.Status.OCCUPIED) {
            return ProgramResult.rejected("structure_controlled");
        }
        if (status != StructureDirectControl.Status.ACTIVE) {
            return ProgramResult.rejected("structure_unavailable");
        }
        session.directControl = true;
        session.controlled = true;
        SESSIONS.put(playerId, session);
        return ProgramResult.completed("control_acquired", reference);
    }

    /**
     * Program move entry: bounded nozzle/pressure force at the captured local anchors. The world
     * thrust direction of every anchor is re-derived from the live full pose each step; a world
     * target turns the group into a bounded follow. The shared whole-body budget is split across
     * the active anchors, so more nozzles or fragments never multiply it. The explicit anchor
     * input must resolve to an actual captured support cell, and every rejection keeps the
     * structure and its inertia. Sable bodies have no per-step force entry and are unavailable.
     */
    public static ProgramResult programMove(
            ServerPlayer player,
            StructureRef reference,
            UUID controlId,
            @Nullable Vec3 target,
            @Nullable BlockPos moveAnchorOffset,
            long durationTicks
    ) {
        requireThread(player);
        var held = controlledSession(player, reference, controlId);
        if (held == null) return ProgramResult.rejected("no_matching_control");
        if (held.directControl || held.sableBody) return ProgramResult.unavailable("sable_move");
        if (held.region == null || held.region.status() == StructurePhysicsRegion.Status.FAILED) {
            return ProgramResult.rejected("region_unavailable");
        }
        if (target != null) {
            if (!finite(target)) return ProgramResult.rejected("invalid_target");
            var motion = held.region.motion(reference).orElse(null);
            if (motion == null) return ProgramResult.rejected("motion_unavailable");
            var center = motion.pose().localToWorldPoint(motion.localCenterOfMass());
            if (center.distanceTo(target) > MAXIMUM_PROGRAM_MOVE_RANGE) {
                return ProgramResult.rejected("target_out_of_range");
            }
        }
        if (moveAnchorOffset != null
                && held.mounts.stream().noneMatch(
                        mount -> mount.supportOffset.equals(moveAnchorOffset))) {
            return ProgramResult.rejected("anchor_unresolved");
        }
        if (!mountsResolve(player, held)) {
            return ProgramResult.rejected("support_unresolved");
        }
        held.programMove = true;
        held.engaged = true;
        held.moveTarget = target;
        held.moveAnchorOffset = moveAnchorOffset;
        held.remainingTicks = (int) Mth.clamp(
                durationTicks, 1L, MAXIMUM_PROGRAM_MOVE_TICKS);
        engageNozzle(held);
        return ProgramResult.completed("move", reference);
    }

    /**
     * Program launch entry: one bounded launch along an explicit direction for the requested
     * window. Native bodies keep the calibrated thrust ceiling and settle damage only at real
     * contacts; a Sable body under the direct lease receives one exact bounded velocity instead,
     * then coasts. No preset-trajectory damage is ever settled.
     */
    public static ProgramResult programLaunch(
            ServerPlayer player,
            StructureRef reference,
            UUID controlId,
            Vec3 direction,
            float power,
            long durationTicks
    ) {
        requireThread(player);
        var held = controlledSession(player, reference, controlId);
        if (held == null) return ProgramResult.rejected("no_matching_control");
        if (!finite(direction) || direction.lengthSqr() <= 1.0e-8) {
            return ProgramResult.rejected("invalid_direction");
        }
        if (!ProgramPowerScale.isAccepted(power)) {
            return ProgramResult.rejected("invalid_power");
        }
        if (held.directControl) {
            return launchSableDirect(player, held, direction.normalize(), power);
        }
        if (held.region == null || held.region.status() == StructurePhysicsRegion.Status.FAILED) {
            return ProgramResult.rejected("region_unavailable");
        }
        if (!mountsResolve(player, held)) {
            return ProgramResult.rejected("support_unresolved");
        }
        held.programMove = false;
        held.engaged = true;
        held.moveTarget = null;
        held.moveAnchorOffset = null;
        held.programPower = power;
        held.launchDirection = direction.normalize();
        held.remainingTicks = (int) Mth.clamp(
                durationTicks, 1L, MAXIMUM_PROGRAM_LAUNCH_TICKS);
        engageNozzle(held);
        return ProgramResult.completed("launched", reference);
    }

    /**
     * Program release entry: idempotent and token-scoped. It stops the sustained jet force and
     * retains the structure with its linear and angular velocity; a stale or foreign token is a
     * no-op and never terminates a successor's control.
     */
    public static ProgramResult programRelease(
            ServerPlayer player,
            StructureRef reference,
            UUID controlId
    ) {
        requireThread(player);
        var held = SESSIONS.get(player.getUUID());
        if (held == null
                || !held.reference.equals(reference)
                || !held.controllerId.equals(controlId)) {
            return ProgramResult.completed("release_noop", null);
        }
        stopSession(player.getUUID(), held.launchId, "program_release");
        return ProgramResult.completed("released", reference);
    }

    /** The current session for this player, if any. It is a view, not a lease. */
    public static Optional<Session> programSession(ServerPlayer player) {
        requireThread(player);
        return Optional.ofNullable(SESSIONS.get(player.getUUID()));
    }

    /**
     * One-shot direct-velocity program launch for a Sable body under the lightweight lease: the
     * calibrated per-power ceiling is applied without a mass gate, the lease and this session's
     * control are withdrawn, and the body is observed in free flight until release or expiry.
     */
    private static ProgramResult launchSableDirect(
            ServerPlayer player,
            Session held,
            Vec3 direction,
            float power
    ) {
        if (!(player.level() instanceof ServerLevel level)) {
            return ProgramResult.rejected("host_unavailable");
        }
        var status = StructureDirectControl.validate(
                level, held.reference, player, held.controllerId);
        if (status != StructureDirectControl.Status.ACTIVE) {
            stopSession(player.getUUID(), held.launchId, "control_lease_lost");
            return ProgramResult.rejected(status == StructureDirectControl.Status.OCCUPIED
                    ? "structure_controlled" : "structure_unavailable");
        }
        var speed = JetStructureControl.maximumSpeed(ProgramPowerScale.require(power));
        held.closeControl();
        if (!StructureBackendRouter.launch(level, held.reference, direction, speed)) {
            stopSession(player.getUUID(), held.launchId, "sable_launch_rejected");
            return ProgramResult.rejected("sable_launch_rejected");
        }
        held.stage = Stage.SABLE_DIRECT;
        held.engaged = true;
        held.remainingTicks = PROGRAM_SESSION_TICKS;
        held.launchSpeed = speed;
        return ProgramResult.completed("launched", held.reference);
    }

    private static @Nullable Session controlledSession(
            ServerPlayer player,
            StructureRef reference,
            UUID controlId
    ) {
        var held = SESSIONS.get(player.getUUID());
        if (held == null
                || !held.reference.equals(reference)
                || !held.controllerId.equals(controlId)
                || !held.controlled) {
            return null;
        }
        return held;
    }

    private static boolean mountsResolve(ServerPlayer player, Session session) {
        if (session.mounts.isEmpty()) return false;
        return session.mounts.stream().allMatch(mount -> supportCellResolves(player, session, mount));
    }

    private static boolean supportCellResolves(ServerPlayer player, Session session, NozzleMount mount) {
        return resolvesSupportCell(player, session.reference, mount.supportOffset);
    }

    /**
     * Whether one local support offset still resolves to a live structure cell. A structure that
     * cannot be resolved (unloaded, split away or replaced) is never guessed back to a host block;
     * callers end the operation while retaining the unique content owner.
     */
    public static boolean resolvesSupportCell(
            ServerPlayer player,
            StructureRef reference,
            BlockPos offset
    ) {
        if (!(player.level() instanceof ServerLevel level)) return false;
        var handle = StructureSpaces.resolve(level.getServer(), reference).orElse(null);
        if (handle == null || handle.lifecycle() != StructureLifecycle.ACTIVE) return false;
        try {
            return !handle.world().read(offset).state().isAir();
        } catch (IllegalArgumentException | IllegalStateException failure) {
            return false;
        }
    }

    /**
     * Pure fallback decision for a nozzle whose support can no longer be resolved: the structure
     * stays the content owner whenever it is still live; a host-block fallback is only allowed
     * after the structure itself is gone and the host block is still structurally solid.
     */
    public enum SupportFallback {
        STRUCTURE, BLOCK, NONE
    }

    public static SupportFallback supportFallback(
            boolean structureActive,
            boolean supportCellPresent,
            boolean blockSupportResolvable
    ) {
        if (structureActive) return supportCellPresent
                ? SupportFallback.STRUCTURE : SupportFallback.NONE;
        return blockSupportResolvable ? SupportFallback.BLOCK : SupportFallback.NONE;
    }

    private static void engageNozzle(Session session) {
        if (session.targetNozzle == null || session.targetNozzle.isRemoved()) return;
        session.targetNozzle.activate((int) Mth.clamp(
                session.remainingTicks, 1L, MAXIMUM_PROGRAM_LAUNCH_TICKS));
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
                    plan.launchDirection, List.copyOf(positions), costMultiplier, false));
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
        if (session.stage == Stage.SABLE_DIRECT) {
            tickSableDirect(player, session);
            return;
        }
        if (session.program && (!session.controlled || !session.engaged)) {
            tickProgramIdle(player, session);
            return;
        }
        switch (session.stage) {
            case AWAITING_PHYSICS -> tickAwait(player, session);
            case THRUSTING -> tickThrust(player, session);
            case SABLE_DIRECT -> tickSableDirect(player, session);
            case CAPTURING -> throw new IllegalStateException("A held session cannot be capturing");
        }
    }

    /**
     * A program session before control and before its first move/launch only watches the region
     * and the direct lease; it never acquires thrust control on its own and expires with the
     * shared window while retaining the structure.
     */
    private static void tickProgramIdle(ServerPlayer player, Session session) {
        if (session.directControl) {
            if (!(player.level() instanceof ServerLevel level)) return;
            var status = StructureDirectControl.validate(
                    level, session.reference, player, session.controllerId);
            if (status != StructureDirectControl.Status.ACTIVE) {
                stopSession(player.getUUID(), session.launchId,
                        status == StructureDirectControl.Status.OCCUPIED
                                ? "control_occupied" : "control_unavailable");
            }
            return;
        }
        var region = session.region;
        if (region == null) return;
        if (region.status() == StructurePhysicsRegion.Status.FAILED) {
            stopSession(player.getUUID(), session.launchId, "region_failed");
            return;
        }
        if (region.status() != StructurePhysicsRegion.Status.READY) {
            if (++session.graceTicks > REGION_UNAVAILABLE_GRACE_TICKS) {
                stopSession(player.getUUID(), session.launchId, "region_unavailable");
            }
            return;
        }
        if (region.motion(session.reference).isEmpty()) {
            if (++session.graceTicks > REGION_UNAVAILABLE_GRACE_TICKS) {
                stopSession(player.getUUID(), session.launchId, "motion_unavailable");
            }
            return;
        }
        session.graceTicks = 0;
    }

    /** Coast observation for a directly launched Sable body; the lease is already withdrawn. */
    private static void tickSableDirect(ServerPlayer player, Session session) {
        if (!session.directControl) return;
        if (!(player.level() instanceof ServerLevel level)) return;
        var status = StructureDirectControl.validate(
                level, session.reference, player, session.controllerId);
        if (status != StructureDirectControl.Status.ACTIVE) {
            stopSession(player.getUUID(), session.launchId,
                    status == StructureDirectControl.Status.OCCUPIED
                            ? "control_occupied" : "control_unavailable");
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
        var reference = current.structure().orElse(capture.reference);
        if (capture.program) {
            if (!beginProgramSession(player, reference, capture.request, capture.nozzles,
                    capture.targetNozzle, capture.launchDirection, capture.positions,
                    (ServerLevel) player.level())) {
                AcademyCraft.LOGGER.warn(
                        "High-Speed Jet program session could not start for {}; structure retained",
                        reference);
            }
            return;
        }
        beginSession(player, capture.transactionId,
                reference, capture.request,
                capture.nozzles, capture.targetNozzle, capture.launchDirection,
                capture.positions, capture.costMultiplier);
    }

    /**
     * Program session start: the captured nozzle-hosted structure opens its shared physics region
     * and waits for explicit program control. No skill message and no session-level resource
     * charge happens here; the program charges per node. Sable bodies skip the region and wait
     * for the direct lease.
     */
    private static boolean beginProgramSession(
            ServerPlayer player,
            StructureRef reference,
            LaunchRequest request,
            List<NozzleMount> nozzles,
            HighSpeedJetNozzle targetNozzle,
            Vec3 launchDirection,
            List<BlockPos> positions,
            ServerLevel level
    ) {
        if (sable(reference)) {
            attachNozzles(player.getUUID(), reference, nozzles);
            SESSIONS.put(player.getUUID(), Session.programSable(reference, level.dimension(),
                    request, List.copyOf(nozzles), targetNozzle, launchDirection, positions.size()));
            AcademyCraft.LOGGER.info(
                    "High-Speed Jet program capture {} waits for program control", reference);
            return true;
        }
        var handle = StructureSpaces.resolve(level.getServer(), reference).orElse(null);
        if (handle == null || handle.lifecycle() != StructureLifecycle.ACTIVE) {
            AcademyCraft.LOGGER.warn(
                    "High-Speed Jet program capture {} lost its handle; structure retained", reference);
            return false;
        }
        var limits = new AABB(level.getWorldBorder().getMinX(), level.getMinY(),
                level.getWorldBorder().getMinZ(), level.getWorldBorder().getMaxX(),
                level.getMaxY(), level.getWorldBorder().getMaxZ());
        var bounds = regionBounds(positions, launchDirection, level.getMinY(), level.getMaxY(), limits);
        if (bounds == null) {
            AcademyCraft.LOGGER.warn(
                    "High-Speed Jet program region cannot fit {}; structure retained", reference);
            return false;
        }
        StructurePhysicsRegion region;
        try {
            region = StructurePhysics.openRegion(level, bounds);
        } catch (RuntimeException | AssertionError failure) {
            AcademyCraft.LOGGER.error(
                    "High-Speed Jet program could not open a physics region for {}", reference, failure);
            return false;
        }
        try {
            region.attach(handle);
        } catch (RuntimeException | AssertionError failure) {
            AcademyCraft.LOGGER.error("High-Speed Jet program could not attach {}", reference, failure);
            try {
                region.close();
            } catch (RuntimeException cleanup) {
                failure.addSuppressed(cleanup);
            }
            return false;
        }
        attachNozzles(player.getUUID(), reference, nozzles);
        var previousTicks = new HashMap<HighSpeedJetNozzle, Integer>();
        for (var mount : nozzles) {
            previousTicks.put(mount.nozzle, mount.nozzle.activeTicks());
        }
        SESSIONS.put(player.getUUID(), Session.programNative(reference, level.dimension(),
                request, previousTicks, List.copyOf(nozzles), targetNozzle, launchDirection,
                positions.size(), region));
        AcademyCraft.LOGGER.info(
                "High-Speed Jet program session started for {} with {} blocks",
                reference, positions.size());
        return true;
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
                previousTicks, List.copyOf(nozzles), targetNozzle, launchDirection,
                blockCount, region, false);
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
        var command = thrustCommand(player, session, motion);
        if (command == null) {
            stopSession(player.getUUID(), session.launchId, "support_unresolved");
            return;
        }
        Optional<StructurePhysicsLease> acquired;
        try {
            acquired = region.acquireEntityControl(session.reference, player, session.controllerId,
                    new StructurePhysicsControl.Force(command.localPoint(), command.force()),
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
            var command = thrustCommand(player, session, motion);
            if (command == null) {
                stopSession(player.getUUID(), session.launchId, "support_unresolved");
                return;
            }
            lease.setControl(new StructurePhysicsControl.Force(
                    command.localPoint(), command.force()));
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

    /**
     * World-space force anchors for one program step. Every anchor's local support cell was
     * verified against the captured structure; its thrust direction is re-derived from the live
     * full pose each step (or toward the explicit follow target), so roll and pitch rotate the
     * force exactly with the body.
     */
    private static List<JetStructureControl.ThrustAnchor> programAnchors(
            Session session,
            StructureMotionState motion
    ) {
        var result = new ArrayList<JetStructureControl.ThrustAnchor>();
        for (var mount : session.mounts) {
            if (session.moveAnchorOffset != null
                    && !mount.supportOffset.equals(session.moveAnchorOffset)) continue;
            if (mount.nozzle.isRemoved()) continue;
            var localCenter = new Vec3(
                    mount.supportOffset.getX() + 0.5,
                    mount.supportOffset.getY() + 0.5,
                    mount.supportOffset.getZ() + 0.5);
            var world = motion.pose().localToWorldPoint(localCenter);
            Vec3 direction;
            if (session.moveTarget != null) {
                direction = session.moveTarget.subtract(world);
                if (direction.lengthSqr() <= 1.0e-8) continue;
                direction = direction.normalize();
            } else {
                direction = motion.pose().localToWorldDirection(localThrustDirection(mount.face));
            }
            result.add(new JetStructureControl.ThrustAnchor(world, direction));
        }
        return List.copyOf(result);
    }

    /** The local application point of one program force group: the centroid of its anchors. */
    private static Vec3 localAnchorCentroid(Session session) {
        double x = 0.0, y = 0.0, z = 0.0;
        var count = 0;
        for (var mount : session.mounts) {
            if (session.moveAnchorOffset != null
                    && !mount.supportOffset.equals(session.moveAnchorOffset)) continue;
            if (mount.nozzle.isRemoved()) continue;
            x += mount.supportOffset.getX() + 0.5;
            y += mount.supportOffset.getY() + 0.5;
            z += mount.supportOffset.getZ() + 0.5;
            count++;
        }
        if (count == 0) return Vec3.ZERO;
        return new Vec3(x / count, y / count, z / count);
    }

    /**
     * One outer-step thrust command: the limited force (single calibrated vector for a skill
     * launch, the shared-budget force group for a program move) and its local application point.
     * A null result means no program anchor still resolves and the caller ends the action safely.
     */
    private static @Nullable ThrustCommand thrustCommand(
            ServerPlayer player,
            Session session,
            StructureMotionState motion
    ) {
        var milestone = milestone(player);
        var power = session.thrustPower();
        if (session.program && session.programMove) {
            var anchors = programAnchors(session, motion);
            if (anchors.isEmpty()) return null;
            var directions = anchors.stream()
                    .map(JetStructureControl.ThrustAnchor::direction)
                    .toList();
            return new ThrustCommand(
                    JetStructureControl.limitedCombinedThrustForce(directions,
                            motion.linearVelocity(), motion.mass(), power, milestone),
                    localAnchorCentroid(session));
        }
        return new ThrustCommand(
                JetStructureControl.limitedThrustForce(session.launchDirection,
                        motion.linearVelocity(), motion.mass(), power, milestone),
                motion.localCenterOfMass());
    }

    /** Captured-frame thrust direction of one nozzle: away from its support face. */
    static Vec3 localThrustDirection(Direction face) {
        return Vec3.atLowerCornerOf(face.getUnitVec3i()).scale(-1.0);
    }

    private record ThrustCommand(Vec3 force, Vec3 localPoint) {
    }

    private static boolean nozzleGate(ServerPlayer player, Session session) {
        var nozzle = session.targetNozzle;
        if (nozzle == null) return !session.program;
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
        var damage = impactDamage(session.blockCount, closingPerTick, session.thrustPower());
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
        var knockback = knockbackStrength(session.blockCount, closingPerTick, session.thrustPower())
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
        private final boolean program;

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
                float costMultiplier,
                boolean program
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
            this.program = program;
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

    public static final class Session {
        private final UUID launchId;
        private final StructureRef reference;
        private final ResourceKey<Level> dimension;
        private final @Nullable LaunchRequest request;
        private final Map<HighSpeedJetNozzle, Integer> previousNozzleTicks;
        private final List<NozzleMount> mounts;
        private final HighSpeedJetNozzle targetNozzle;
        private final int blockCount;
        private final UUID controllerId = UUID.randomUUID();
        private final @Nullable StructurePhysicsRegion region;
        private final Map<UUID, Long> impactTicks = new HashMap<>();
        private final boolean program;
        private Stage stage = Stage.AWAITING_PHYSICS;
        private StructurePhysicsLease lease;
        private int remainingTicks;
        private int graceTicks;
        private int impactCount;
        private double launchSpeed;
        private Vec3 launchDirection;
        private boolean controlled;
        private boolean engaged;
        private boolean programMove;
        private boolean directControl;
        private boolean sableBody;
        private @Nullable Vec3 moveTarget;
        private @Nullable BlockPos moveAnchorOffset;
        private float programPower = 1.0f;

        private Session(
                UUID launchId,
                StructureRef reference,
                ResourceKey<Level> dimension,
                @Nullable LaunchRequest request,
                Map<HighSpeedJetNozzle, Integer> previousNozzleTicks,
                List<NozzleMount> mounts,
                HighSpeedJetNozzle targetNozzle,
                Vec3 launchDirection,
                int blockCount,
                @Nullable StructurePhysicsRegion region,
                boolean program
        ) {
            this.launchId = launchId;
            this.reference = reference;
            this.dimension = dimension;
            this.request = request;
            this.previousNozzleTicks = Map.copyOf(previousNozzleTicks);
            this.mounts = List.copyOf(mounts);
            this.targetNozzle = targetNozzle;
            this.launchDirection = launchDirection;
            this.blockCount = blockCount;
            this.region = region;
            this.program = program;
            remainingTicks = request == null ? PROGRAM_SESSION_TICKS : request.durationTicks();
        }

        private static Session programNative(
                StructureRef reference,
                ResourceKey<Level> dimension,
                LaunchRequest request,
                Map<HighSpeedJetNozzle, Integer> previousNozzleTicks,
                List<NozzleMount> mounts,
                HighSpeedJetNozzle targetNozzle,
                Vec3 launchDirection,
                int blockCount,
                StructurePhysicsRegion region
        ) {
            var session = new Session(UUID.randomUUID(), reference, dimension, request,
                    previousNozzleTicks, mounts, targetNozzle, launchDirection, blockCount,
                    region, true);
            session.remainingTicks = PROGRAM_SESSION_TICKS;
            session.controlled = false;
            return session;
        }

        private static Session programSable(
                StructureRef reference,
                ResourceKey<Level> dimension,
                @Nullable LaunchRequest request,
                List<NozzleMount> mounts,
                @Nullable HighSpeedJetNozzle targetNozzle,
                Vec3 launchDirection,
                int blockCount
        ) {
            var session = new Session(UUID.randomUUID(), reference, dimension, request,
                    Map.of(), mounts, targetNozzle, launchDirection, blockCount, null, true);
            session.remainingTicks = PROGRAM_SESSION_TICKS;
            session.sableBody = true;
            session.stage = Stage.SABLE_DIRECT;
            session.controlled = false;
            return session;
        }

        public StructureRef reference() {
            return reference;
        }

        /** Program launches honor their explicit power; skill sessions keep the request power. */
        private float thrustPower() {
            return program ? programPower : request == null ? 1.0f : request.power();
        }

        public UUID controllerId() {
            return controllerId;
        }

        public Stage stage() {
            return stage;
        }

        public boolean controlActive() {
            return directControl
                    || lease != null && lease.state() == StructurePhysicsLease.State.ACTIVE;
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
            if (directControl) {
                try {
                    StructureDirectControl.release(reference, controllerId);
                } catch (RuntimeException failure) {
                    AcademyCraft.LOGGER.warn(
                            "High-Speed Jet direct lease cleanup failed for {}", reference, failure);
                } finally {
                    directControl = false;
                }
            }
        }
    }
}
