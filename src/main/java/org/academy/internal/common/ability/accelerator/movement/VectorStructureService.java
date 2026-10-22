package org.academy.internal.common.ability.accelerator.movement;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.academy.AcademyCraft;
import org.academy.api.common.ability.accelerator.KineticLiftProfile;
import org.academy.api.common.ability.accelerator.KineticStructureControl;
import org.academy.api.common.damage.SkillDamageSource;
import org.academy.api.common.structure.BlockStructureApi;
import org.academy.api.common.structure.BlockStructureCaptureOptions;
import org.academy.api.common.structure.StructureBackendStatus;
import org.academy.api.common.structure.StructureCaptureRequest;
import org.academy.api.common.structure.StructureCaptureResult;
import org.academy.api.common.structure.StructureDirectControl;
import org.academy.api.common.structure.StructureDynamics;
import org.academy.api.common.structure.StructureHit;
import org.academy.api.common.structure.StructureImpactSweep;
import org.academy.api.common.structure.StructureLifecycle;
import org.academy.api.common.structure.StructureMotionState;
import org.academy.api.common.structure.StructureHandle;
import org.academy.api.common.structure.StructurePhysics;
import org.academy.api.common.structure.StructurePhysicsControl;
import org.academy.api.common.structure.StructurePhysicsLease;
import org.academy.api.common.structure.StructurePhysicsRegion;
import org.academy.api.common.structure.StructurePose;
import org.academy.api.common.structure.StructureQueries;
import org.academy.api.common.structure.StructureRef;
import org.academy.api.common.structure.StructureSpaces;
import org.academy.api.common.structure.StructureTraction;
import org.academy.api.common.structure.StructureTractionSession;
import org.academy.api.common.structure.StructureTransactions;
import org.academy.api.server.team.TeamRelations;
import org.academy.internal.common.ability.Skills;
import org.academy.internal.common.ability.accelerator.skills.lv3.KineticThrow;
import org.academy.internal.common.entitycontrol.EntityMotionGuard;
import org.academy.internal.common.structure.NativeStructureRuntime;
import org.academy.internal.common.structure.NativeStructureCollisionBoxes;
import org.academy.internal.common.structure.StructureBackendRouter;
import org.academy.internal.common.sublevel.SubLevelServer;
import org.academy.internal.common.sublevel.SubLevelStructureRefs;
import org.academy.internal.common.world.damagesource.PvpSetting;
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
import java.util.UUID;
import java.util.function.BiPredicate;

/**
 * Shared server implementation of the vector ability's structure lifecycle: backend-routed
 * capture, physics-region anchor lift, impulsive launch, in-flight region following, contact
 * settlement and transient control release. Kinetic Throw and later precision-program callers
 * drive the same sessions through the public entries below; a terminated session always keeps
 * the structure, its physics region and its inertia instead of restoring blocks.
 */
public final class VectorStructureService {
    private static final int NATIVE_CELL_BUDGET = 128;
    private static final int SABLE_CELL_BUDGET = 256;
    private static final int TRACTION_LEASE_TICKS = 20;
    private static final long CAPTURE_TIMEOUT_TICKS = 600;
    private static final int REGION_UNAVAILABLE_GRACE_TICKS = 40;
    private static final int IMPACT_COOLDOWN_TICKS = 10;
    private static final double REGION_MARGIN = 6.0;
    private static final double FOLLOW_TRIGGER = 2.0;
    private static final double FOLLOW_LOOKAHEAD_SECONDS = 0.5;
    private static final double FOLLOW_TRAILING_MARGIN = 2.0;
    private static final double ANGULAR_MARGIN_SECONDS = 0.05;
    // Impact settling must not skip a real side hit merely because the target's feet are
    // within half a block of the moving top face; 0.18 matches the carrying surface tolerance.
    private static final double SUPPORT_TOLERANCE = 0.18;
    private static final double PROGRAM_CONTROL_RANGE = 32.0;
    private static final int PROGRAM_CONTROL_TIER = 1;
    /**
     * Initial-generation vertical clearance. The first physics step runs before the hover
     * anchor exists; this bounded gap keeps the fresh body out of its own terrain socket so
     * that step can never settle it below the source selection bottom.
     */
    private static final double INITIAL_GROUND_CLEARANCE = 0.25;
    /** Program capture budget ceiling; zero keeps the backend default. */
    public static final int MAXIMUM_PROGRAM_BUDGET = 256;
    /** Bounded distance between the body center and a program move target. */
    public static final double MAXIMUM_PROGRAM_MOVE_RANGE = 32.0;
    /** Program move requests never extend control beyond this many ticks. */
    public static final long MAXIMUM_PROGRAM_MOVE_TICKS = 600L;

    private static final Map<UUID, CaptureState> CAPTURES = new HashMap<>();
    private static final Map<UUID, Session> SESSIONS = new HashMap<>();

    private VectorStructureService() {
    }

    public enum Stage {
        CAPTURING, AWAITING_PHYSICS, LIFTING, FLYING
    }

    /** Public stage observation for integrations and validation; it cannot drive the service. */
    public record Diagnostic(Stage stage, StructureRef reference, int blockCount, boolean lifted,
                             boolean controlActive, boolean flying, boolean sable, int impacts) {
    }

    /** A validated capture selection. The caller pays its own cost before {@link #beginCapture}. */
    public record CapturePlan(BlockPos seed, int tier, List<BlockPos> positions) {
        public CapturePlan {
            Objects.requireNonNull(seed, "seed");
            positions = List.copyOf(positions);
        }
    }

    public static Optional<Diagnostic> diagnostic(ServerPlayer player) {
        requireThread(player);
        var capture = CAPTURES.get(player.getUUID());
        if (capture != null) {
            return Optional.of(new Diagnostic(Stage.CAPTURING, capture.reference, capture.blockCount(),
                    false, false, false, capture.sable, 0));
        }
        var held = SESSIONS.get(player.getUUID());
        if (held == null) return Optional.empty();
        return Optional.of(new Diagnostic(held.stage, held.reference, held.blockCount, held.lifted,
                held.controlActive(), held.flying(), held.sable(), held.impactCount));
    }

    /** Capture identity for program retries; independent of the backend's structure identity. */
    public static Optional<UUID> pendingProgramCapture(ServerPlayer player) {
        requireThread(player);
        var capture = CAPTURES.get(player.getUUID());
        return capture == null || !capture.program ? Optional.empty() : Optional.of(capture.transactionId);
    }

    /** The current structure session for this player, if any. It is a view, not a lease. */
    public static Optional<Session> session(ServerPlayer player) {
        requireThread(player);
        return Optional.ofNullable(SESSIONS.get(player.getUUID()));
    }

    /**
     * Selects and budget-trims the capture area without writing anything. Rejections log the
     * detail and report the skill message; the source blocks are untouched.
     */
    public static Optional<CapturePlan> planCapture(ServerPlayer player, BlockPos seed, int tier) {
        requireThread(player);
        Objects.requireNonNull(seed, "seed");
        var outcome = planCaptureInternal(player, seed, tier, 0, true);
        if (outcome.plan() == null) {
            rejectCapture(player, outcome.rejection());
            return Optional.empty();
        }
        return Optional.of(outcome.plan());
    }

    /**
     * Starts the backend-routed capture transaction for a plan. A pending result is tracked
     * until {@link #tick} observes its outcome; failures report {@code status:detail}.
     */
    public static boolean beginCapture(ServerPlayer player, CapturePlan plan) {
        traceLiftProbeCommand("begin_capture", player, null);
        requireThread(player);
        Objects.requireNonNull(plan, "plan");
        if (sableLiftMassRejected(player, plan)) return false;
        var result = startCapture(player, plan, true, false);
        if (result.pending()) return true;
        if (!result.succeeded()) {
            rejectCapture(player, result.status() + ":" + result.detail());
            return false;
        }
        return true;
    }

    /**
     * Sable skill lifts pull through the traction mass gate, which previously ran only after
     * the capture had already built a ship. The source cells' mass is exact and
     * backend-predictable, so an over-budget lift is refused before any structure exists.
     */
    private static boolean sableLiftMassRejected(ServerPlayer player, CapturePlan plan) {
        if (!(player.level() instanceof ServerLevel level) || !sableMode(level)) return false;
        var backend = StructureBackendRouter.captureTarget(level.getServer()).orElse(null);
        if (backend == null) return false;
        var mass = backend.estimateMass(level, plan.positions());
        if (!Double.isFinite(mass) || mass <= KineticStructureControl.massBudget(plan.tier())) return false;
        rejectCapture(player, "sable_traction_too_heavy");
        return true;
    }

    /** Explicit program outcome codes; the caller never parses log text. */
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
     * Program capture entry: the same staged content transaction as the skill path without the
     * skill messages. A pending capture publishes no structure reference; the structure output
     * exists only after the content transaction committed.
     */
    public static ProgramResult programExtract(ServerPlayer player, BlockPos seed, int tier, int budget) {
        requireThread(player);
        Objects.requireNonNull(seed, "seed");
        if (tier < 0 || tier > 2) return ProgramResult.rejected("invalid_tier");
        if (budget < 0 || budget > MAXIMUM_PROGRAM_BUDGET) {
            return ProgramResult.rejected("invalid_budget");
        }
        var outcome = planCaptureInternal(player, seed, tier, budget, false);
        if (outcome.plan() == null) return ProgramResult.rejected(outcome.rejection());
        var plan = outcome.plan();
        var result = startCapture(player, plan, false, true);
        if (result.pending()) {
            return ProgramResult.pending(result.status() + ":" + result.detail());
        }
        if (!result.succeeded()) {
            return ProgramResult.rejected(result.status() + ":" + result.detail());
        }
        var reference = result.structure().orElse(null);
        if (reference == null) return ProgramResult.rejected("committed_without_reference");
        var held = SESSIONS.get(player.getUUID());
        if (held == null || !held.reference().equals(reference)) {
            return ProgramResult.rejected("lift_failed");
        }
        return ProgramResult.completed("committed", reference);
    }

    /**
     * Program control entry. An existing session of the same structure is reused; otherwise the
     * structure's active physics region is adopted under a new exclusive session. A structure
     * controlled or captured by another actor is rejected instead of being stolen. Sable bodies
     * receive a lightweight direct-velocity lease: no force, no mass gate, and no physics region.
     */
    public static ProgramResult programControl(ServerPlayer player, StructureRef reference, UUID runtimeId) {
        requireThread(player);
        Objects.requireNonNull(reference, "reference");
        Objects.requireNonNull(runtimeId, "runtimeId");
        var playerId = player.getUUID();
        var held = SESSIONS.get(playerId);
        if (held != null && held.reference.equals(reference)) {
            if (isIndependent(reference)) return controlSableExisting(player, held, runtimeId);
            if (!programControlUsable(player, held, runtimeId)) {
                return ProgramResult.rejected("structure_unavailable");
            }
            held.manualHover = false;
            return ProgramResult.completed("control_active", reference);
        }
        var capture = CAPTURES.get(playerId);
        if (capture != null) {
            return ProgramResult.rejected("capture_pending");
        }
        for (var entry : SESSIONS.entrySet()) {
            if (!entry.getKey().equals(playerId) && entry.getValue().reference.equals(reference)) {
                return ProgramResult.rejected("structure_controlled");
            }
        }
        for (var other : CAPTURES.values()) {
            if (other.reference.equals(reference)) return ProgramResult.rejected("structure_capturing");
        }
        if (!(player.level() instanceof ServerLevel level)) {
            return ProgramResult.rejected("host_unavailable");
        }
        if (isIndependent(reference)) {
            return controlSableFresh(player, level, reference, runtimeId);
        }
        var handle = StructureSpaces.resolve(level.getServer(), reference).orElse(null);
        if (handle == null || handle.lifecycle() != StructureLifecycle.ACTIVE) {
            return ProgramResult.rejected("structure_unavailable");
        }
        if (!handle.runtimeId().equals(runtimeId)) {
            return ProgramResult.rejected("stale_structure_epoch");
        }
        var region = StructurePhysics.activeRegion(level, reference).orElse(null);
        if (region == null) return ProgramResult.rejected("no_active_region");
        if (region.status() == StructurePhysicsRegion.Status.FAILED) {
            return ProgramResult.rejected("region_failed");
        }
        var motion = region.motion(reference).orElse(null);
        if (motion == null) return ProgramResult.rejected("motion_unavailable");
        var settings = KineticStructureControl.settings(
                motion.mass(), PROGRAM_CONTROL_TIER, 0, PROGRAM_CONTROL_RANGE);
        var local = motion.localCenterOfMass();
        var point = motion.pose().localToWorldPoint(local);
        var session = new Session(reference, level.dimension(), PROGRAM_CONTROL_TIER, 0, region, null);
        try {
            var acquired = region.acquireEntityControl(reference, player, session.controllerId,
                    new StructurePhysicsControl.Anchor(local, point, Vec3.ZERO,
                            settings.stiffness(), settings.damping(), settings.maxForce()),
                    TRACTION_LEASE_TICKS);
            if (acquired.isEmpty()) return ProgramResult.rejected("control_slot_occupied");
            session.lease = acquired.orElseThrow();
            session.stiffness = settings.stiffness();
            session.damping = settings.damping();
            session.maxForce = settings.maxForce();
            session.liftStartY = point.y;
            session.liftTarget = point;
            session.stage = Stage.LIFTING;
            session.expiresAt = level.getGameTime() + KineticThrow.HOVER_TICKS;
            // The service keeps one session per player; adopting a second structure first ends
            // the previous session while retaining that structure, its region and its inertia.
            terminate(playerId);
            SESSIONS.put(playerId, session);
        } catch (IllegalStateException | IllegalArgumentException rejection) {
            AcademyCraft.LOGGER.info("Program control was rejected for {}: {}", reference, rejection.getMessage());
            return ProgramResult.rejected("control_rejected");
        }
        return ProgramResult.completed("control_acquired", reference);
    }

    private static ProgramResult controlSableExisting(ServerPlayer player, Session held, UUID runtimeId) {
        if (!(player.level() instanceof ServerLevel level)) {
            return ProgramResult.rejected("host_unavailable");
        }
        var handle = StructureSpaces.resolve(level.getServer(), held.reference).orElse(null);
        if (handle == null || handle.lifecycle() != StructureLifecycle.ACTIVE) {
            terminate(player.getUUID());
            return ProgramResult.rejected("structure_unavailable");
        }
        if (!handle.runtimeId().equals(runtimeId)) {
            // The bound body epoch is gone; the stale token must not regain authority.
            terminate(player.getUUID());
            return ProgramResult.rejected("stale_structure_epoch");
        }
        if (held.traction != null) return ProgramResult.rejected("structure_controlled");
        if (held.directControl) {
            var status = StructureDirectControl.validate(level, held.reference, player, held.controllerId);
            if (status == StructureDirectControl.Status.ACTIVE) {
                held.manualHover = false;
                return ProgramResult.completed("control_active", held.reference);
            }
            terminate(player.getUUID());
            return ProgramResult.rejected(status == StructureDirectControl.Status.OCCUPIED
                    ? "structure_controlled" : "structure_unavailable");
        }
        var status = StructureDirectControl.acquire(level, held.reference, player, held.controllerId);
        if (status == StructureDirectControl.Status.OCCUPIED) {
            return ProgramResult.rejected("structure_controlled");
        }
        if (status != StructureDirectControl.Status.ACTIVE) {
            terminate(player.getUUID());
            return ProgramResult.rejected("structure_unavailable");
        }
        held.directControl = true;
        held.sableBody = true;
        held.manualHover = false;
        if (SubLevelStructureRefs.isSubLevel(held.reference)) {
            var motion = StructureDynamics.motion(level, held.reference).orElseThrow();
            held.liftTarget = motion.pose().localToWorldPoint(motion.localCenterOfMass());
            held.programMove = true;
        }
        held.stage = Stage.LIFTING;
        held.expiresAt = level.getGameTime() + KineticThrow.HOVER_TICKS;
        return ProgramResult.completed("control_acquired", held.reference);
    }

    private static ProgramResult controlSableFresh(ServerPlayer player, ServerLevel level,
                                                   StructureRef reference, UUID runtimeId) {
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
        var session = new Session(reference, level.dimension(), PROGRAM_CONTROL_TIER, 0, null, null);
        var status = StructureDirectControl.acquire(level, reference, player, session.controllerId);
        if (status == StructureDirectControl.Status.OCCUPIED) {
            return ProgramResult.rejected("structure_controlled");
        }
        if (status != StructureDirectControl.Status.ACTIVE) {
            return ProgramResult.rejected("structure_unavailable");
        }
        session.directControl = true;
        session.sableBody = true;
        if (SubLevelStructureRefs.isSubLevel(reference)) {
            var motion = StructureDynamics.motion(level, reference).orElseThrow();
            session.liftTarget = motion.pose().localToWorldPoint(motion.localCenterOfMass());
            session.programMove = true;
        }
        session.stage = Stage.LIFTING;
        session.expiresAt = level.getGameTime() + KineticThrow.HOVER_TICKS;
        // The service keeps one session per player; adopting a second structure first ends
        // the previous session while retaining that structure, its region and its inertia.
        terminate(player.getUUID());
        SESSIONS.put(player.getUUID(), session);
        return ProgramResult.completed("control_acquired", reference);
    }

    /**
     * Program move entry: bounded anchor correction toward a world target. The body keeps its
     * inertia; range violations and lost regions end the action while retaining the structure.
     */
    public static ProgramResult programMove(ServerPlayer player, StructureRef reference, UUID controlId,
                                            Vec3 target, long durationTicks) {
        requireThread(player);
        var held = controlledSession(player, reference, controlId);
        if (held == null) return ProgramResult.rejected("no_matching_control");
        if (held.stage != Stage.LIFTING) {
            return ProgramResult.rejected("not_movable:" + held.stage.name().toLowerCase(java.util.Locale.ROOT));
        }
        if (!isFiniteVector(target)) return ProgramResult.rejected("invalid_target");
        if (!(player.level() instanceof ServerLevel level)) {
            return ProgramResult.rejected("host_unavailable");
        }
        var block = BlockPos.containing(target);
        if (!level.hasChunkAt(block) || !level.getWorldBorder().isWithinBounds(block)) {
            return ProgramResult.rejected("target_unavailable");
        }
        var motion = StructureDynamics.motion(level, held.reference).orElse(null);
        if (motion == null) return ProgramResult.rejected("motion_unavailable");
        var com = motion.pose().localToWorldPoint(motion.localCenterOfMass());
        if (com.distanceTo(target) > MAXIMUM_PROGRAM_MOVE_RANGE) {
            return ProgramResult.rejected("target_out_of_range");
        }
        held.programMove = true;
        held.liftTarget = target;
        held.expiresAt = Math.max(held.expiresAt, level.getGameTime()
                + Mth.clamp(durationTicks, 1L, MAXIMUM_PROGRAM_MOVE_TICKS));
        return ProgramResult.completed("move", reference);
    }

    /**
     * Program launch entry: one bounded impulse along an explicit direction. No trajectory damage
     * is pre-settled; contact damage stays tied to real contacts during flight.
     */
    public static ProgramResult programLaunch(ServerPlayer player, StructureRef reference, UUID controlId,
                                              Vec3 direction, double speedScale) {
        requireThread(player);
        var held = controlledSession(player, reference, controlId);
        if (held == null) return ProgramResult.rejected("no_matching_control");
        if (held.stage != Stage.LIFTING) {
            return ProgramResult.rejected("not_launchable:" + held.stage.name().toLowerCase(java.util.Locale.ROOT));
        }
        if (!isFiniteVector(direction) || direction.lengthSqr() <= 1.0e-8) {
            return ProgramResult.rejected("invalid_direction");
        }
        if (!Double.isFinite(speedScale) || speedScale < 0.1 || speedScale > 2.0) {
            return ProgramResult.rejected("invalid_strength");
        }
        if (held.directControl) {
            return launchSableDirect(player, held, direction.normalize(), speedScale);
        }
        if (held.traction != null) return ProgramResult.unavailable("sable_launch");
        if (!(player.level() instanceof ServerLevel)) return ProgramResult.rejected("host_unavailable");
        if (!launchNative(player, held, direction.normalize(), speedScale)) {
            return ProgramResult.rejected("launch_rejected");
        }
        return ProgramResult.completed("launched", reference);
    }

    /**
     * Direct-velocity program launch for a Sable body under a lightweight lease. The calibrated
     * skill-side throw speed is applied without any mass gate; the lease and this session's
     * control are withdrawn before the hand-off, and the body remains observed in free flight.
     */
    private static ProgramResult launchSableDirect(ServerPlayer player, Session held, Vec3 direction,
                                                   double speedScale) {
        traceLiftProbeCommand("program_launch", player, held.reference);
        if (!(player.level() instanceof ServerLevel level)) {
            return ProgramResult.rejected("host_unavailable");
        }
        var status = StructureDirectControl.validate(level, held.reference, player, held.controllerId);
        if (status != StructureDirectControl.Status.ACTIVE) {
            terminate(player.getUUID());
            return ProgramResult.rejected(status == StructureDirectControl.Status.OCCUPIED
                    ? "structure_controlled" : "structure_unavailable");
        }
        var skill = Skills.KINETIC_THROW.get();
        var speed = KineticThrow.throwSpeed(skill.hasProficiencyMilestone(player, 3)) * speedScale;
        held.closeControl();
        if (!StructureBackendRouter.launch(level, held.reference, direction, speed)) {
            terminate(player.getUUID());
            return ProgramResult.rejected("sable_launch_rejected");
        }
        held.sableBody = true;
        held.stage = Stage.FLYING;
        held.lifted = true;
        held.expiresAt = level.getGameTime() + KineticThrow.MAXIMUM_FLIGHT_TICKS;
        held.velocity = direction.scale(speed);
        return ProgramResult.completed("launched", held.reference);
    }

    /**
     * Program release entry: idempotent and token-scoped. A stale or foreign token is a no-op and
     * never terminates a successor's control; structure, region and inertia are always retained.
     */
    public static ProgramResult programRelease(ServerPlayer player, StructureRef reference, UUID controlId) {
        requireThread(player);
        var held = SESSIONS.get(player.getUUID());
        if (held == null || !held.reference.equals(reference) || !held.controllerId.equals(controlId)) {
            return ProgramResult.completed("release_noop", null);
        }
        terminate(player.getUUID());
        return ProgramResult.completed("released", reference);
    }

    /** H-press routing outcomes; only {@link #THROW} changes world state. */
    public enum PressRoute {
        CHARGE, THROW, REJECT
    }

    public enum PressStatus {
        CHARGING, PREPARING, THROWN, REJECTED
    }

    public record PressResult(PressStatus status, String detail) {
        static PressResult charging() {
            return new PressResult(PressStatus.CHARGING, "charge");
        }

        static PressResult thrown(String detail) {
            return new PressResult(PressStatus.THROWN, detail);
        }

        static PressResult rejected(String detail) {
            return new PressResult(PressStatus.REJECTED, detail);
        }
    }

    /**
     * Pure H-press routing: a visible, permitted structure is always thrown directly and its
     * mass never participates in the decision; no structure keeps the existing charge flow.
     */
    public static PressRoute pressRoute(boolean structureHit, boolean permitted, boolean physical) {
        if (!structureHit) return PressRoute.CHARGE;
        if (!permitted || !physical) return PressRoute.REJECT;
        return PressRoute.THROW;
    }

    /**
     * Direct-press throw speed. The calibrated response is deliberately independent of the
     * body's mass so a heavy structure cannot be filtered out by a traction budget.
     */
    public static double directThrowSpeed(double mass, boolean speedMilestone) {
        if (!Double.isFinite(mass) || mass <= 0) {
            throw new IllegalArgumentException("Structure mass must be finite and positive");
        }
        return KineticThrow.throwSpeed(speedMilestone);
    }

    /**
     * H-press decision entry. The caster's own hovering structure is launched along the current
     * view without requiring them to look back at it; otherwise a looked-at physicalized structure is thrown
     * directly along the view. No hit reports {@link PressStatus#CHARGING} so the caller keeps
     * the existing charge/capture flow; every refusal carries a machine-readable detail.
     */
    public static PressResult press(ServerPlayer player) {
        traceLiftProbeCommand("press", player, null);
        requireThread(player);
        if (!(player.level() instanceof ServerLevel level)) return PressResult.rejected("host_unavailable");
        var look = player.getLookAngle();
        if (look.lengthSqr() <= 1.0e-8) return PressResult.rejected("invalid_look");
        var held = SESSIONS.get(player.getUUID());
        if (held != null && held.stage == Stage.LIFTING && held.usableBy(player)
                && held.controlActive() && held.manualHover) {
            return launchHeld(player, held) ? PressResult.thrown("held")
                    : PressResult.rejected("held_launch_rejected");
        }
        var range = Skills.KINETIC_THROW.get().scaledRange(player, KineticThrow.TARGET_RANGE);
        var eye = player.getEyePosition();
        var end = eye.add(look.normalize().scale(range));
        var hit = StructureQueries.raycast(level, eye, end).orElse(null);
        if (hit == null) return PressResult.charging();
        var validation = StructureQueries.validate(level, eye, end, hit);
        if (!validation.succeeded()) {
            return PressResult.rejected("ray_" + validation.status().name().toLowerCase(java.util.Locale.ROOT));
        }
        var current = validation.currentHit().orElseThrow();
        var permitted = level.mayInteract(player, BlockPos.containing(current.worldPoint()));
        var physical = isPhysical(level, current.block().structure());
        if (permitted && !physical && NativeStructureRuntime.ID.equals(current.block().structure().backend())) {
            return prepareExistingThrow(player, level, current, look.normalize());
        }
        return switch (pressRoute(true, permitted, physical)) {
            case CHARGE -> PressResult.charging();
            case REJECT -> PressResult.rejected(permitted ? "structure_not_physical" : "permission_denied");
            case THROW -> directThrow(player, level, current, look.normalize());
        };
    }

    private static boolean isPhysical(ServerLevel level, StructureRef reference) {
        if (StructureBackendRouter.SABLE_ID.equals(reference.backend()) || SubLevelStructureRefs.isSubLevel(reference)) {
            return StructureDynamics.motion(level, reference).isPresent();
        }
        return StructurePhysics.activeRegion(level, reference)
                .flatMap(region -> region.motion(reference)).isPresent();
    }

    /** Keeps legacy content in its current slot and attaches that same handle before throwing. */
    private static PressResult prepareExistingThrow(ServerPlayer player, ServerLevel level,
                                                     StructureHit hit, Vec3 direction) {
        var reference = hit.block().structure();
        for (var entry : SESSIONS.entrySet()) {
            if (!entry.getKey().equals(player.getUUID()) && entry.getValue().reference.equals(reference)) {
                return PressResult.rejected("structure_controlled");
            }
        }
        var handle = StructureSpaces.resolve(level.getServer(), reference).orElse(null);
        if (handle == null || handle.lifecycle() != StructureLifecycle.ACTIVE) {
            return PressResult.rejected("structure_unavailable");
        }
        var region = StructurePhysics.attachedRegion(level, reference).orElse(null);
        if (region == null) {
            try {
                region = StructurePhysics.adoptExistingRegion(level, reference);
            } catch (IllegalStateException | IllegalArgumentException rejection) {
                AcademyCraft.LOGGER.info("Kinetic Throw cannot physicalize retained {}: {}",
                        reference, rejection.getMessage());
                return PressResult.rejected("physicalization_region_unavailable");
            }
        }
        if (region.control(reference).isPresent()) return PressResult.rejected("structure_controlled");
        var held = new Session(reference, level.dimension(), 0, presentedCellCount(level, reference), region, null);
        held.stage = Stage.AWAITING_PHYSICS;
        held.pendingThrowDirection = direction;
        held.expiresAt = level.getGameTime() + CAPTURE_TIMEOUT_TICKS;
        terminate(player.getUUID());
        SESSIONS.put(player.getUUID(), held);
        return new PressResult(PressStatus.PREPARING, "physicalizing_retained_structure");
    }

    /** Direct velocity hand-off; mass, force and traction budgets do not gate this path. */
    private static PressResult directThrow(ServerPlayer player, ServerLevel level,
                                           StructureHit hit, Vec3 direction) {
        var reference = hit.block().structure();
        var motion = StructureDynamics.motion(level, reference).orElse(null);
        if (motion == null) return PressResult.rejected("motion_unavailable");
        var skill = Skills.KINETIC_THROW.get();
        var speed = directThrowSpeed(motion.mass(), skill.hasProficiencyMilestone(player, 3));
        var sable = StructureBackendRouter.SABLE_ID.equals(reference.backend());
        var direct = sable || SubLevelStructureRefs.isSubLevel(reference);
        StructurePhysicsRegion region = null;
        if (direct) {
            if (!StructureBackendRouter.launch(level, reference, direction, speed)) {
                return PressResult.rejected("sable_launch_rejected");
            }
        } else {
            region = StructurePhysics.activeRegion(level, reference).orElse(null);
            if (region == null) return PressResult.rejected("structure_not_physical");
            try {
                // A foreign lease still refuses instead of being stolen; a stale unleased
                // control is cleared inside setVelocity.
                region.setVelocity(reference, direction.scale(speed), Vec3.ZERO);
            } catch (IllegalStateException | IllegalArgumentException rejection) {
                AcademyCraft.LOGGER.info("Kinetic Throw direct throw was rejected for {}: {}",
                        reference, rejection.getMessage());
                return PressResult.rejected("structure_controlled");
            }
        }
        terminate(player.getUUID());
        var session = new Session(reference, level.dimension(), 0,
                presentedCellCount(level, reference), region, null);
        session.sableBody = direct;
        session.stage = Stage.FLYING;
        session.lifted = true;
        session.expiresAt = level.getGameTime() + KineticThrow.MAXIMUM_FLIGHT_TICKS;
        session.velocity = direction.scale(speed);
        SESSIONS.put(player.getUUID(), session);
        AcademyCraft.LOGGER.info("KINETIC_STRUCTURE_DIRECT_THROW tick={} structure={} mass={} speed={} sable={}",
                level.getGameTime(), reference, motion.mass(), speed, sable);
        return PressResult.thrown(sable ? "sable_direct" : "native_direct");
    }

    private static int presentedCellCount(ServerLevel level, StructureRef reference) {
        if (SubLevelStructureRefs.isSubLevel(reference)) {
            return SubLevelServer.runtime(level)
                    .map(runtime -> runtime.view(SubLevelStructureRefs.id(reference).orElseThrow()).cells().size())
                    .orElse(1);
        }
        return NativeStructureRuntime.existing(level.getServer())
                .map(runtime -> runtime.presentedCellCount(reference))
                .filter(count -> count > 0).orElse(1);
    }

    private static PlanOutcome planCaptureInternal(ServerPlayer player, BlockPos seed, int tier, int budgetOverride,
                                                    boolean liftedCapture) {
        var level = player.level();
        // The selection is free, but a capture removes the source blocks. Refuse before the
        // first mutation when no backend can turn the result into a moving body.
        var backendProblem = StructurePhysics.physicalBackendProblem(level.getServer());
        if (backendProblem.isPresent()) return new PlanOutcome(null, backendProblem.get());
        var selection = BlockStructureApi.selectLowerEllipsoidWithUpperCylinder(
                level,
                seed,
                KineticThrow.horizontalRadius(tier),
                KineticThrow.verticalRadius(tier),
                KineticThrow.cylinderRadius(tier),
                KineticThrow.cylinderHeight(tier),
                capturePolicy(player)
        );
        if (!selection.succeeded()) return new PlanOutcome(null, "selection_empty");
        var forward = Vec3.directionFromRotation(0.0f, player.getYRot());
        var playerSafeSelection = BlockStructureApi.cropToForwardHalfSpace(
                selection.positions(), player.position(), forward, KineticThrow.PLAYER_CLEARANCE);
        boolean localCapture = !sableMode(level);
        // A backend's cell filter must run before clearance validation. Otherwise a selected
        // partial block can hide a real ceiling and then be left behind by the backend crop.
        if (!localCapture) playerSafeSelection = BlockStructureApi.cropNonFullBlocks(level, playerSafeSelection);
        var positions = BlockStructureApi.cropImmediatelyBlocked(
                level, playerSafeSelection, Direction.UP,
                position -> localCapture && org.academy.internal.common.sublevel.SubLevelTerrainBreaker
                        .canClearObstacle(level, player, position));
        boolean seedUnblocked = positions.contains(seed);
        // Preserve selected plants and partial collision shapes with their supporting cells.
        // The capture backend validates the complete body before any source cell is removed.
        // The optional Sable bridge still uses its curated full-cell transaction contract.
        if (positions.isEmpty() || !positions.contains(seed)) {
            AcademyCraft.LOGGER.debug("Kinetic capture selection rejected seed={} player={} yaw={} forward={}"
                            + " selected={} seed_selected={} forward_cells={} seed_forward={} seed_unblocked={}"
                            + " admitted_cells={} seed_state={} above_state={} permitted={}",
                    seed, player.position(), player.getYRot(), forward, selection.positions().size(),
                    selection.positions().contains(seed), playerSafeSelection.size(), playerSafeSelection.contains(seed),
                    seedUnblocked, positions.size(), level.getBlockState(seed), level.getBlockState(seed.above()),
                    level.mayInteract(player, seed));
            return new PlanOutcome(null, "selection_blocked_or_seed_missing");
        }
        var budget = cellBudget(level);
        // At tier zero a normal full terrain selection can exceed the 128 mass admission
        // budget before the player has any chance to lift it. Bound the default one-mass
        // cell selection first; the exact backend mass gate still rejects heavier materials.
        if (!localCapture && liftedCapture) budget = Math.min(budget, KineticStructureControl.massBudget(tier));
        if (budgetOverride > 0) budget = Math.min(budget, budgetOverride);
        var cropped = liftedCapture ? cropToLiftBudget(positions, seed, budget)
                : cropToBudget(positions, seed, budget);
        if (cropped.isEmpty() || !cropped.contains(seed)) {
            return new PlanOutcome(null, "budget_crop_lost_seed");
        }
        return new PlanOutcome(new CapturePlan(seed.immutable(), tier, cropped), "");
    }

    /** The same forward, clearance and budget gates used by the direct kinetic skill. */
    public static List<BlockPos> programCaptureSelection(ServerPlayer player, BlockPos seed, int tier, int budget) {
        var outcome = planCaptureInternal(player, seed, tier, budget, false);
        return outcome.plan() == null ? List.of() : outcome.plan().positions();
    }

    private static StructureCaptureResult startCapture(ServerPlayer player, CapturePlan plan,
                                                       boolean report, boolean program) {
        var level = player.level();
        var budget = cellBudget(level);
        var result = StructureTransactions.capture(level, new StructureCaptureRequest(
                plan.positions(), budget, StructureCaptureRequest.MAXIMUM_SPAN, capturePermission(player), true));
        var reference = result.structure().orElse(null);
        if (result.pending()) {
            if (reference == null) {
                return StructureCaptureResult.failure(result.transactionId(),
                        StructureCaptureResult.Status.RECOVERY_REQUIRED, "pending_without_reference");
            }
            CAPTURES.put(player.getUUID(), new CaptureState(reference, level.dimension(), plan.tier(),
                    plan.positions(), result.transactionId(), level.getGameTime(), sableMode(level), report, program));
            return result;
        }
        if (!result.succeeded()) return result;
        if (reference == null) {
            return StructureCaptureResult.failure(result.transactionId(),
                    StructureCaptureResult.Status.RECOVERY_REQUIRED, "committed_without_reference");
        }
        beginLift(player, reference, plan.tier(), plan.positions(), report, program);
        return result;
    }

    private static @Nullable Session controlledSession(ServerPlayer player, StructureRef reference, UUID controlId) {
        Objects.requireNonNull(reference, "reference");
        Objects.requireNonNull(controlId, "controlId");
        var held = SESSIONS.get(player.getUUID());
        if (held == null || !held.reference.equals(reference) || !held.controllerId.equals(controlId)) {
            return null;
        }
        return held.usableBy(player) ? held : null;
    }

    private static boolean programControlUsable(ServerPlayer player, Session held, UUID runtimeId) {
        if (held.stage == Stage.CAPTURING) return false;
        if (!held.usableBy(player)) return false;
        var handle = StructureSpaces.resolve(player.level().getServer(), held.reference).orElse(null);
        return handle != null && handle.runtimeId().equals(runtimeId);
    }

    private static boolean isFiniteVector(Vec3 value) {
        return value != null
                && Double.isFinite(value.x)
                && Double.isFinite(value.y)
                && Double.isFinite(value.z);
    }

    private record PlanOutcome(@Nullable CapturePlan plan, String rejection) {
    }

    /** Per-player driver; must run once on the owning server thread. */
    public static void tick(ServerPlayer player) {
        requireThread(player);
        tickCapture(player);
        var held = SESSIONS.get(player.getUUID());
        if (held == null) return;
        if (!held.usableBy(player) || player.level().getGameTime() > held.expiresAt) {
            // Expiration releases the hover. Launching requires an explicit H press or node
            // command; merely looking at a structure must never turn it into a projectile.
            if (held.stage == Stage.LIFTING && held.lifted) {
                AcademyCraft.LOGGER.info(
                        "Kinetic Throw hover expired; releasing control for {}",
                        held.reference);
            }
            terminate(player.getUUID());
            return;
        }
        switch (held.stage) {
            case AWAITING_PHYSICS -> tickAwaitingPhysics(player, held);
            case LIFTING -> tickLifting(player, held);
            case FLYING -> tickFlight(player, held);
            case CAPTURING -> throw new IllegalStateException("Held structure cannot be capturing");
        }
    }

    /**
     * The hover window begins when the lift completes, so the hold lasts the full
     * {@link KineticThrow#HOVER_TICKS} instead of sharing that budget with the lift itself.
     */
    public static long hoverDeadline(long liftedAt) {
        return liftProfile().hoverDeadline(liftedAt);
    }

    private static KineticLiftProfile liftProfile() {
        return org.academy.internal.common.sublevel.SubLevelEnhancementBootstrap.KINETIC_LIFT;
    }

    private static StructureMotionState motionState(StructureDynamics.State state) {
        return new StructureMotionState(state.pose(), state.linearVelocity(), state.angularVelocity(),
                state.localCenterOfMass(), state.mass(), false, 0, 0);
    }

    /** Launches the lifted session along the caster's view. False keeps the current session. */
    public static boolean launch(ServerPlayer player) {
        requireThread(player);
        var held = SESSIONS.get(player.getUUID());
        if (held == null || held.stage != Stage.LIFTING || !held.usableBy(player)
                || player.level().getGameTime() > held.expiresAt) {
            return false;
        }
        return launchHeld(player, held);
    }

    /** Shared validation for an explicit launch command. */
    private static boolean launchHeld(ServerPlayer player, Session held) {
        if (!(player.level() instanceof ServerLevel level)) return false;
        var direction = KineticThrow.throwDirection(player.getLookAngle());
        if (direction.lengthSqr() <= 1.0e-8) return false;
        if (held.traction != null || held.directControl) return launchSable(player, held, level, direction);
        return launchNative(player, held, direction, 1.0);
    }

    /**
     * Sable owns the body's physics; the launch replaces its velocity through the pipeline and
     * releases Academy control. Sable also owns contact handling, so no region follows here.
     */
    private static boolean launchSable(ServerPlayer player, Session held, ServerLevel level, Vec3 direction) {
        traceLiftProbeCommand("manual_launch", player, held.reference);
        var skill = Skills.KINETIC_THROW.get();
        var speed = KineticThrow.throwSpeed(skill.hasProficiencyMilestone(player, 3));
        held.closeControl();
        if (!StructureBackendRouter.launch(level, held.reference, direction, speed)) {
            terminate(player.getUUID());
            return false;
        }
        held.stage = Stage.FLYING;
        held.lifted = true;
        held.expiresAt = level.getGameTime() + KineticThrow.MAXIMUM_FLIGHT_TICKS;
        held.velocity = direction.scale(speed);
        return true;
    }

    private static void traceLiftProbeCommand(String action, ServerPlayer player, @Nullable StructureRef reference) {
        if (!Boolean.getBoolean("academy.sublevelLiftTiersSmoke")) return;
        var source = StackWalker.getInstance().walk(frames ->
                frames.skip(1).limit(12).map(StackWalker.StackFrame::toString).toList());
        AcademyCraft.LOGGER.info("SUBLEVEL_LIFT_COMMAND action={} tick={} player={} reference={} source={}",
                action, player.level().getGameTime(), player.getUUID(), reference, source);
    }

    private static void traceLiftProbeBaseline(ServerPlayer player, StructureRef reference,
                                               StructureDynamics.State motion, Vec3 sourceOrigin,
                                               Vec3 sourceCenter) {
        if (!Boolean.getBoolean("academy.sublevelLiftTiersSmoke")) return;
        var actual = motion.pose().localToWorldPoint(motion.localCenterOfMass());
        AcademyCraft.LOGGER.info("SUBLEVEL_LIFT_ADOPT tick={} reference={} source_origin={} local_com={} "
                        + "source_com={} actual_com={} delta={} pose={} linear={} angular={}",
                player.level().getGameTime(), reference, sourceOrigin, motion.localCenterOfMass(), sourceCenter,
                actual, actual.subtract(sourceCenter), motion.pose(), motion.linearVelocity(), motion.angularVelocity());
    }

    /** Releases this session's transient control; the structure, region and inertia are retained. */
    public static void terminate(ServerPlayer player) {
        requireThread(player);
        terminate(player.getUUID());
    }

    /** Silent lifecycle cleanup; pending captures are dropped and the structure is retained. */
    public static void clear(ServerPlayer player) {
        if (player == null) return;
        var playerId = player.getUUID();
        CAPTURES.remove(playerId);
        terminate(playerId);
    }

    /** Server shutdown cleanup; never throws into the stopping server. */
    public static void clearAll() {
        for (var playerId : List.copyOf(SESSIONS.keySet())) {
            try {
                terminate(playerId);
            } catch (RuntimeException failure) {
                AcademyCraft.LOGGER.warn("Kinetic Throw shutdown cleanup failed for {}", playerId, failure);
            }
        }
        CAPTURES.clear();
        SESSIONS.clear();
    }

    private static void beginLift(ServerPlayer player, StructureRef reference, int tier,
                                  List<BlockPos> positions, boolean report, boolean program) {
        terminate(player.getUUID());
        if (SubLevelStructureRefs.isSubLevel(reference)) {
            if (program) beginIndependentProgramExtract(player, reference, tier, positions);
            else beginSubLevelLift(player, reference, tier, positions);
            return;
        }
        if (isSable(reference)) {
            if (program) {
                beginIndependentProgramExtract(player, reference, tier, positions);
                return;
            }
            beginSableLift(player, reference, tier, positions, report);
            return;
        }
        if (!(player.level() instanceof ServerLevel level)) return;
        var handle = StructureSpaces.resolve(level.getServer(), reference).orElse(null);
        if (handle == null) {
            rejectCapture(player, "captured_handle_unavailable", report);
            return;
        }
        var limits = new AABB(level.getWorldBorder().getMinX(), level.getMinY(), level.getWorldBorder().getMinZ(),
                level.getWorldBorder().getMaxX(), level.getMaxY(), level.getWorldBorder().getMaxZ());
        var bounds = regionBounds(positions, KineticThrow.liftHeight(tier), INITIAL_GROUND_CLEARANCE,
                level.getMinY(), level.getMaxY(), limits);
        StructurePhysicsRegion region = StructurePhysics.attachedRegion(level, reference).orElse(null);
        if (region != null) {
            // The capture transaction already built the body before removing its source. Adopt
            // that region and re-base it to the lift envelope; never call setPose on a body.
            if (bounds != null) region.rebind(bounds);
        } else {
            var originalPose = handle.pose();
            try {
                // Legacy path for captures without the physical flag: keep the fresh body out of
                // its own source socket before the first uncontrolled solver step.
                handle.setPose(new StructurePose(
                        originalPose.worldOrigin().add(0.0, INITIAL_GROUND_CLEARANCE, 0.0),
                        originalPose.rotationX(), originalPose.rotationY(),
                        originalPose.rotationZ(), originalPose.rotationW()));
            } catch (IllegalStateException | IllegalArgumentException rejection) {
                AcademyCraft.LOGGER.error("Kinetic Throw could not apply initial ground clearance to {}",
                        reference, rejection);
                rejectCapture(player, "initial_clearance_rejected", report);
                return;
            }
            if (bounds == null) {
                restoreInitialPose(handle, originalPose);
                rejectCapture(player, "region_cannot_fit_structure", report);
                return;
            }
            try {
                region = StructurePhysics.openRegion(level, bounds);
            } catch (RuntimeException | AssertionError failure) {
                AcademyCraft.LOGGER.error("Kinetic Throw could not open a physics region for {}", reference, failure);
                restoreInitialPose(handle, originalPose);
                rejectCapture(player, "region_open_failed", report);
                return;
            }
            try {
                region.attach(handle);
            } catch (RuntimeException | AssertionError failure) {
                AcademyCraft.LOGGER.error("Kinetic Throw could not attach {}", reference, failure);
                restoreInitialPose(handle, originalPose);
                try {
                    region.close();
                } catch (RuntimeException cleanup) {
                    failure.addSuppressed(cleanup);
                }
                rejectCapture(player, "region_attach_failed", report);
                return;
            }
        }
        var held = new Session(reference, level.dimension(), tier, positions.size(), region, null);
        held.capturedCells = List.copyOf(positions);
        held.stage = Stage.AWAITING_PHYSICS;
        held.manualHover = !program;
        held.expiresAt = level.getGameTime() + KineticThrow.HOVER_TICKS;
        SESSIONS.put(player.getUUID(), held);
        var motion = region.motion(reference).orElse(null);
        playKineticCue(level, motion == null ? region.bounds().getCenter()
                : motion.pose().localToWorldPoint(motion.localCenterOfMass()));
    }

    private static void beginSubLevelLift(ServerPlayer player, StructureRef reference, int tier,
                                         List<BlockPos> positions) {
        terminate(player.getUUID());
        var level = (ServerLevel) player.level();
        var motion = StructureDynamics.motion(level, reference).orElse(null);
        if (motion == null) return;
        var held = new Session(reference, level.dimension(), tier, positions.size(), null, null);
        if (StructureDirectControl.acquire(level, reference, player, held.controllerId)
                != StructureDirectControl.Status.ACTIVE) return;
        held.directControl = true;
        held.sableBody = true;
        held.manualHover = true;
        held.capturedCells = List.copyOf(positions);
        held.stage = Stage.LIFTING;
        // Capture can finish after the newly created body has already taken a solver step.
        // Keep the hover above its source cells, not above a displaced intermediate pose.
        var sourceOrigin = new Vec3(positions.stream().mapToInt(BlockPos::getX).min().orElseThrow(),
                positions.stream().mapToInt(BlockPos::getY).min().orElseThrow(),
                positions.stream().mapToInt(BlockPos::getZ).min().orElseThrow());
        // The capture baseline is the source grid pose, not an already stepped body pose.
        var plan = liftProfile().planTarget(net.minecraft.util.Mth.clamp(tier, 0, 2),
                new KineticLiftProfile.SourceBaseline(StructurePose.identity(sourceOrigin),
                        motion.localCenterOfMass()));
        traceLiftProbeBaseline(player, reference, motion, sourceOrigin, plan.sourceWorldCenterOfMass());
        held.liftPlan = plan;
        held.holdPose = StructurePose.identity(sourceOrigin);
        held.liftStartY = plan.sourceWorldCenterOfMass().y;
        held.liftTarget = plan.targetWorldCenterOfMass();
        held.controlTarget = held.liftTarget;
        held.expiresAt = level.getGameTime() + KineticThrow.HOVER_TICKS;
        SESSIONS.put(player.getUUID(), held);
        playKineticCue(level, plan.sourceWorldCenterOfMass());
    }

    private static void beginSableLift(ServerPlayer player, StructureRef reference, int tier,
                                       List<BlockPos> positions, boolean report) {
        if (!(player.level() instanceof ServerLevel level)) return;
        var handle = StructureSpaces.resolve(level.getServer(), reference).orElse(null);
        var motion = StructureDynamics.motion(level, reference).orElse(null);
        if (handle == null || motion == null) {
            discardSableCapture(level, reference, "sable_structure_unavailable");
            rejectCapture(player, "sable_structure_unavailable", report);
            return;
        }
        var held = new Session(reference, level.dimension(), tier, positions.size(), null, null);
        var acquired = StructureDirectControl.acquire(level, reference, player, held.controllerId);
        if (acquired != StructureDirectControl.Status.ACTIVE) {
            var detail = "sable_lift_" + acquired.name().toLowerCase(java.util.Locale.ROOT);
            discardSableCapture(level, reference, detail);
            rejectCapture(player, detail, report);
            return;
        }
        // H lifts the captured body as a whole. A ray-hit spring pulls an off-center surface,
        // tilts the freshly excavated body against its socket and also retains gravity sag.
        // Use the same mass-center force lease and source-grid baseline as the local backend.
        var sourceOrigin = new Vec3(positions.stream().mapToInt(BlockPos::getX).min().orElseThrow(),
                positions.stream().mapToInt(BlockPos::getY).min().orElseThrow(),
                positions.stream().mapToInt(BlockPos::getZ).min().orElseThrow());
        var plan = liftProfile().planTarget(net.minecraft.util.Mth.clamp(tier, 0, 2),
                new KineticLiftProfile.SourceBaseline(StructurePose.identity(sourceOrigin), motion.localCenterOfMass()));
        traceLiftProbeBaseline(player, reference, motion, sourceOrigin, plan.sourceWorldCenterOfMass());
        held.directControl = true;
        held.sableBody = true;
        held.capturedCells = List.copyOf(positions);
        held.stage = Stage.LIFTING;
        held.manualHover = true;
        held.liftPlan = plan;
        held.liftStartY = plan.sourceWorldCenterOfMass().y;
        held.liftTarget = plan.targetWorldCenterOfMass();
        held.controlTarget = held.liftTarget;
        held.expiresAt = level.getGameTime() + KineticThrow.HOVER_TICKS;
        SESSIONS.put(player.getUUID(), held);
        playKineticCue(level, plan.sourceWorldCenterOfMass());
    }

    /**
     * A capture that committed but cannot be adopted must not stay behind: remove the just-built
     * structure through its backend before the caller reports the rejection.
     */
    private static void discardSableCapture(ServerLevel level, StructureRef reference, String detail) {
        AcademyCraft.LOGGER.error("Kinetic Throw Sable lift was rejected after the capture ({}); discarding {}",
                detail, reference);
        if (!StructureTransactions.discard(level.getServer(), reference)) {
            AcademyCraft.LOGGER.error("Kinetic Throw could not discard the just-created structure {}", reference);
        }
    }

    /**
     * Program extraction on a Sable world retains the captured body without a skill lift or a
     * traction session. The following control node issues the lightweight direct-velocity lease;
     * an unavailable body rejects and the structure stays retained.
     */
    private static void beginIndependentProgramExtract(ServerPlayer player, StructureRef reference,
                                                       int tier, List<BlockPos> positions) {
        if (!(player.level() instanceof ServerLevel level)) return;
        var handle = StructureSpaces.resolve(level.getServer(), reference).orElse(null);
        if (handle == null || handle.lifecycle() != StructureLifecycle.ACTIVE) {
            if (isSable(reference)) discardSableCapture(level, reference, "structure_unavailable");
            rejectCapture(player, "structure_unavailable", false);
            return;
        }
        var held = new Session(reference, level.dimension(), tier, positions.size(), null, null);
        held.capturedCells = List.copyOf(positions);
        held.sableBody = true;
        held.stage = Stage.LIFTING;
        held.expiresAt = level.getGameTime() + KineticThrow.HOVER_TICKS;
        SESSIONS.put(player.getUUID(), held);
    }

    private static boolean launchNative(ServerPlayer player, Session held, Vec3 direction, double speedScale) {
        if (!(player.level() instanceof ServerLevel level) || held.region == null) return false;
        if (!isFiniteVector(direction) || direction.lengthSqr() <= 1.0e-8) return false;
        var motion = held.region.motion(held.reference).orElse(null);
        if (motion == null) return false;
        var skill = Skills.KINETIC_THROW.get();
        var speed = KineticThrow.throwSpeed(skill.hasProficiencyMilestone(player, 3)) * speedScale;
        var mass = motion.mass();
        var center = motion.pose().localToWorldPoint(motion.localCenterOfMass());
        held.programMove = false;
        held.closeControl();
        held.stage = Stage.FLYING;
        held.lifted = true;
        held.expiresAt = level.getGameTime() + KineticThrow.MAXIMUM_FLIGHT_TICKS;
        held.velocity = direction.scale(speed);
        try {
            // Clear lift momentum first so this single impulse defines the launch velocity
            // exactly; from here the body is pure inertia with an empty control slot.
            held.region.setVelocity(held.reference, Vec3.ZERO, Vec3.ZERO);
            held.region.applyImpulse(held.reference, direction.scale(speed * mass), center);
        } catch (RuntimeException | AssertionError failure) {
            AcademyCraft.LOGGER.error("Kinetic Throw launch was rejected for {}", held.reference, failure);
            terminate(player.getUUID());
            return false;
        }
        return true;
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
            rejectCapture(player, "transaction_evicted", capture.report);
            return;
        }
        var current = result.get();
        if (current.pending()) {
            if (level.getGameTime() - capture.startedAt > CAPTURE_TIMEOUT_TICKS) {
                CAPTURES.remove(playerId);
                AcademyCraft.LOGGER.warn("Kinetic Throw capture {} exceeded its tracking window; structure retained",
                        capture.transactionId);
            }
            return;
        }
        CAPTURES.remove(playerId);
        if (current.succeeded()) {
            beginLift(player, current.structure().orElse(capture.reference), capture.tier,
                    capture.positions, capture.report, capture.program);
        } else {
            rejectCapture(player, current.status() + ":" + current.detail(), capture.report);
        }
    }

    private static void tickAwaitingPhysics(ServerPlayer player, Session held) {
        var region = held.region;
        if (region == null) {
            terminate(player.getUUID());
            return;
        }
        if (region.status() == StructurePhysicsRegion.Status.FAILED) {
            AcademyCraft.LOGGER.error("Kinetic Throw region failed before lift: {}", region.diagnostic());
            terminate(player.getUUID());
            return;
        }
        if (region.status() != StructurePhysicsRegion.Status.READY) return;
        var motion = region.motion(held.reference).orElse(null);
        if (motion == null) return;
        if (held.pendingThrowDirection != null) {
            var direction = held.pendingThrowDirection;
            var speed = KineticThrow.throwSpeed(Skills.KINETIC_THROW.get().hasProficiencyMilestone(player, 3));
            try {
                region.setVelocity(held.reference, direction.scale(speed), Vec3.ZERO);
            } catch (IllegalStateException | IllegalArgumentException rejection) {
                rejectCapture(player, "retained_throw_rejected");
                terminate(player.getUUID());
                return;
            }
            held.pendingThrowDirection = null;
            held.stage = Stage.FLYING;
            held.lifted = true;
            held.velocity = direction.scale(speed);
            held.expiresAt = player.level().getGameTime() + KineticThrow.MAXIMUM_FLIGHT_TICKS;
            return;
        }
        var skill = Skills.KINETIC_THROW.get();
        var range = skill.scaledRange(player, KineticThrow.TARGET_RANGE + KineticThrow.liftHeight(held.tier));
        var settings = KineticStructureControl.settings(motion.mass(), held.tier,
                skill.getEffectiveProficiencyMilestone(player), range);
        var local = motion.localCenterOfMass();
        var plan = liftProfile().planTarget(net.minecraft.util.Mth.clamp(held.tier, 0, 2),
                new KineticLiftProfile.SourceBaseline(motion.pose(), motion.localCenterOfMass()));
        held.liftPlan = plan;
        held.liftStartY = plan.sourceWorldCenterOfMass().y;
        held.liftTarget = plan.targetWorldCenterOfMass();
        held.controlTarget = KineticStructureControl.gravityCompensatedTarget(held.liftTarget,
                motion.mass(), settings.stiffness());
        held.stiffness = settings.stiffness();
        held.damping = settings.damping();
        held.maxForce = settings.maxForce();
        var control = new StructurePhysicsControl.Anchor(local, held.controlTarget, Vec3.ZERO,
                settings.stiffness(), settings.damping(), settings.maxForce());
        Optional<StructurePhysicsLease> acquired;
        try {
            acquired = region.acquireEntityControl(held.reference, player, held.controllerId,
                    control, TRACTION_LEASE_TICKS);
        } catch (IllegalStateException | IllegalArgumentException rejection) {
            AcademyCraft.LOGGER.warn("Kinetic Throw lift control was rejected for {}", held.reference, rejection);
            terminate(player.getUUID());
            return;
        }
        if (acquired.isEmpty()) {
            AcademyCraft.LOGGER.warn("Kinetic Throw lift control slot is occupied for {}", held.reference);
            terminate(player.getUUID());
            return;
        }
        held.lease = acquired.orElseThrow();
        held.stage = Stage.LIFTING;
    }

    private static void tickLifting(ServerPlayer player, Session held) {
        if (held.directControl) {
            if (!directControlActive(player, held)) {
                terminate(player.getUUID());
                return;
            }
            if ((held.programMove || held.manualHover || SubLevelStructureRefs.isSubLevel(held.reference))
                    && player.level() instanceof ServerLevel level) {
                var motion = StructureDynamics.motion(level, held.reference).orElse(null);
                if (motion == null) return;
                var skill = Skills.KINETIC_THROW.get();
                var settings = KineticStructureControl.settings(motion.mass(), held.tier,
                        held.programMove ? 0 : skill.getEffectiveProficiencyMilestone(player),
                        held.programMove ? PROGRAM_CONTROL_RANGE
                                : skill.scaledRange(player, KineticThrow.TARGET_RANGE + KineticThrow.liftHeight(held.tier)));
                held.controlTarget = KineticStructureControl.gravityCompensatedTarget(held.liftTarget,
                        motion.mass(), settings.stiffness());
                var anchor = new StructurePhysicsControl.Anchor(motion.localCenterOfMass(), held.controlTarget,
                        Vec3.ZERO, settings.stiffness(), settings.damping(), settings.maxForce());
                var submitted = held.holdPose != null
                        ? StructureDirectControl.submit(level, held.reference, player, held.controllerId,
                                anchor, held.holdPose)
                        : StructureDirectControl.submit(level, held.reference, player, held.controllerId, anchor);
                if (!submitted) {
                    terminate(player.getUUID());
                }
                held.velocity = motion.linearVelocity();
                if (!held.programMove && !held.lifted && settledAtLiftTarget(held, motionState(motion))) {
                    held.lifted = true;
                    held.expiresAt = hoverDeadline(level.getGameTime());
                }
            }
            return;
        }
        if (held.traction != null) {
            tickSableLift(player, held);
            return;
        }
        if (held.region == null && isIndependent(held.reference)) {
            // Program extraction retains an independent body until its control node issues the lease.
            if (!(player.level() instanceof ServerLevel level)
                    || StructureSpaces.resolve(level.getServer(), held.reference)
                    .filter(handle -> handle.lifecycle() == StructureLifecycle.ACTIVE).isEmpty()) {
                terminate(player.getUUID());
            }
            return;
        }
        var region = held.region;
        if (region == null) {
            terminate(player.getUUID());
            return;
        }
        if (region.status() == StructurePhysicsRegion.Status.FAILED) {
            AcademyCraft.LOGGER.error("Kinetic Throw region failed while lifting: {}", region.diagnostic());
            terminate(player.getUUID());
            return;
        }
        var motion = region.motion(held.reference).orElse(null);
        if (motion == null) {
            if (++held.unavailableTicks > REGION_UNAVAILABLE_GRACE_TICKS) terminate(player.getUUID());
            return;
        }
        held.unavailableTicks = 0;
        var lease = held.lease;
        if (lease == null || lease.state() != StructurePhysicsLease.State.ACTIVE) {
            terminate(player.getUUID());
            return;
        }
        try {
            lease.setControl(new StructurePhysicsControl.Anchor(motion.localCenterOfMass(),
                    held.controlTarget, Vec3.ZERO, held.stiffness, held.damping, held.maxForce));
            lease.renew(TRACTION_LEASE_TICKS);
        } catch (IllegalStateException | IllegalArgumentException rejection) {
            AcademyCraft.LOGGER.warn("Kinetic Throw lift lease ended for {}", held.reference, rejection);
            terminate(player.getUUID());
            return;
        }
        held.velocity = motion.linearVelocity();
        if (held.programMove) {
            followRegion(player, held, motion);
        } else if (!held.lifted && settledAtLiftTarget(held, motion)) {
            held.lifted = true;
            held.expiresAt = hoverDeadline(player.level().getGameTime());
        }
        applyImpacts(player, held);
    }

    private static boolean directControlActive(ServerPlayer player, Session held) {
        if (!(player.level() instanceof ServerLevel level)) return false;
        return StructureDirectControl.validate(level, held.reference, player, held.controllerId)
                == StructureDirectControl.Status.ACTIVE;
    }

    private static void tickSableLift(ServerPlayer player, Session held) {
        var session = held.traction;
        if (session == null || session.status() != StructureTraction.Status.ACTIVE) {
            terminate(player.getUUID());
            return;
        }
        var status = session.update(held.liftTarget, Vec3.ZERO);
        if (status != StructureTraction.Status.ACTIVE) {
            AcademyCraft.LOGGER.info("Kinetic Throw sable lift ended: {} for {}", status, held.reference);
            terminate(player.getUUID());
            return;
        }
        held.velocity = Vec3.ZERO;
        var measured = StructureDynamics.motion(player.level(), held.reference).orElse(null);
        if (!held.lifted && measured != null && settledAtLiftTarget(held, motionState(measured))) {
            held.lifted = true;
            held.expiresAt = hoverDeadline(player.level().getGameTime());
        }
    }

    private static boolean settledAtLiftTarget(Session held, StructureMotionState motion) {
        if (held.liftPlan == null) return false;
        var center = motion.pose().localToWorldPoint(motion.localCenterOfMass());
        double tolerance = org.academy.internal.common.sublevel.SubLevelKineticLiftProfile.ACCEPTANCE_TOLERANCE;
        // The rising body can cross the target while still moving quickly. Starting the hold
        // at that crossing consumes the player's aiming time during the subsequent overshoot.
        return center.distanceToSqr(held.liftTarget) <= tolerance * tolerance
                && motion.linearVelocity().lengthSqr() <= 0.15 * 0.15
                && motion.angularVelocity().lengthSqr() <= 0.1 * 0.1;
    }

    private static void tickFlight(ServerPlayer player, Session held) {
        if (held.sableBody) {
            tickSableFlight(player, held);
            return;
        }
        var region = held.region;
        if (region == null) {
            terminate(player.getUUID());
            return;
        }
        if (region.status() == StructurePhysicsRegion.Status.FAILED) {
            AcademyCraft.LOGGER.error("Kinetic Throw region failed during flight: {}", region.diagnostic());
            terminate(player.getUUID());
            return;
        }
        var motion = region.motion(held.reference).orElse(null);
        if (motion == null) {
            if (++held.unavailableTicks > REGION_UNAVAILABLE_GRACE_TICKS * 2) terminate(player.getUUID());
            return;
        }
        held.unavailableTicks = 0;
        held.velocity = motion.linearVelocity();
        if (region.status() == StructurePhysicsRegion.Status.READY) followRegion(player, held, motion);
        applyImpacts(player, held);
    }

    /** Sable owns contact and collision; this only observes inertia until the window ends. */
    private static void tickSableFlight(ServerPlayer player, Session held) {
        if (!(player.level() instanceof ServerLevel level)) {
            terminate(player.getUUID());
            return;
        }
        var motion = StructureDynamics.motion(level, held.reference).orElse(null);
        if (motion == null) {
            if (++held.unavailableTicks > REGION_UNAVAILABLE_GRACE_TICKS * 2) terminate(player.getUUID());
            return;
        }
        held.unavailableTicks = 0;
        held.velocity = motion.linearVelocity();
        if (SubLevelStructureRefs.isSubLevel(held.reference)) applySubLevelImpacts(player, held, motion);
    }

    private static void applySubLevelImpacts(ServerPlayer player, Session held, StructureDynamics.State motion) {
        var level = (ServerLevel) player.level();
        var runtime = SubLevelServer.runtime(level).orElse(null);
        if (runtime == null) return;
        var local = runtime.localCollisionBoxes(SubLevelStructureRefs.id(held.reference).orElseThrow());
        if (local.isEmpty()) return;
        var previous = held.previousImpactPose == null ? motion.pose() : held.previousImpactPose;
        held.previousImpactPose = motion.pose();
        var boxes = NativeStructureCollisionBoxes.worldBoxes(local, motion.pose());
        var impactCenter = union(boxes).minmax(union(NativeStructureCollisionBoxes.worldBoxes(local, previous)))
                .getCenter();
        var bounds = StructureImpactSweep.sweptBounds(local, previous, motion.pose(), motion.localCenterOfMass());
        var now = level.getGameTime();
        for (var target : level.getEntities(player, bounds.inflate(0.1))) {
            if (target.noPhysics || !canAffect(player, target)) continue;
            var last = held.impactTicks.get(target.getUUID());
            if (last != null && now - last < IMPACT_COOLDOWN_TICKS) continue;
            var targetBox = target.getBoundingBox();
            if (NativeStructureCollisionBoxes.supports(boxes, targetBox, SUPPORT_TOLERANCE)
                    || !StructureImpactSweep.intersects(local, previous, motion.pose(), targetBox)) continue;
            settleImpact(player, held, null, target, impactCenter);
        }
        held.impactTicks.entrySet().removeIf(entry -> now - entry.getValue() > IMPACT_COOLDOWN_TICKS * 4L);
    }

    private static void followRegion(ServerPlayer player, Session held, StructureMotionState motion) {
        if (!(player.level() instanceof ServerLevel level) || held.region == null) return;
        var projection = projection(level, held.reference, held.region.bounds().inflate(16.0));
        if (projection == null) return;
        var occupied = union(projection.worldCollisionBoxes());
        if (occupied == null) return;
        var halfDiagonal = Math.sqrt(occupied.getXsize() * occupied.getXsize()
                + occupied.getYsize() * occupied.getYsize()
                + occupied.getZsize() * occupied.getZsize()) / 2.0;
        var predicted = occupied.move(motion.linearVelocity().scale(0.05))
                .inflate(motion.angularVelocity().length() * halfDiagonal * ANGULAR_MARGIN_SECONDS);
        var bounds = held.region.bounds();
        if (edgeDistance(bounds, predicted) >= FOLLOW_TRIGGER) return;
        var limits = new AABB(level.getWorldBorder().getMinX(), level.getMinY(), level.getWorldBorder().getMinZ(),
                level.getWorldBorder().getMaxX(), level.getMaxY(), level.getWorldBorder().getMaxZ());
        var candidate = followBounds(bounds, predicted, motion.linearVelocity(), FOLLOW_LOOKAHEAD_SECONDS,
                FOLLOW_TRAILING_MARGIN, level.getMinY(), level.getMaxY(), limits);
        if (candidate == null || candidate.equals(bounds) || !tryRebind(held.region, candidate)) {
            // Try the current occupancy before giving up on the conservative swept bounds.
            var conservative = followBounds(bounds, occupied, motion.linearVelocity(), FOLLOW_LOOKAHEAD_SECONDS,
                    FOLLOW_TRAILING_MARGIN, level.getMinY(), level.getMaxY(), limits);
            if (conservative == null || conservative.equals(bounds) || !tryRebind(held.region, conservative)) {
                try {
                    // Ordinary cancellation preserves inertia. A refused region follow must
                    // release this session's lease before explicitly stopping its motion.
                    held.closeControl();
                    held.region.setVelocity(held.reference, Vec3.ZERO, Vec3.ZERO);
                    AcademyCraft.LOGGER.warn("Kinetic Throw region follow could not rebase {}; "
                                    + "linear and angular velocity cleared before ending the session",
                            held.reference);
                } catch (RuntimeException | AssertionError | LinkageError failure) {
                    AcademyCraft.LOGGER.error("Kinetic Throw region follow could not stop motion for {}; "
                                    + "ending the skill session with the structure retained",
                            held.reference, failure);
                } finally {
                    terminate(player.getUUID());
                }
            }
        }
    }

    private static boolean tryRebind(StructurePhysicsRegion region, AABB candidate) {
        try {
            return region.rebind(candidate);
        } catch (RuntimeException | AssertionError failure) {
            AcademyCraft.LOGGER.error("Kinetic Throw region rebase failed", failure);
            return false;
        }
    }

    private static void applyImpacts(ServerPlayer player, Session held) {
        if (!(player.level() instanceof ServerLevel level) || held.region == null) return;
        var projection = projection(level, held.reference, held.region.bounds().inflate(16.0));
        if (projection == null) return;
        var boxes = projection.worldCollisionBoxes();
        if (boxes.isEmpty()) return;
        var union = union(boxes);
        if (union == null) return;
        var now = level.getGameTime();
        // A fast body can move several blocks between two published poses. Sweep the target
        // backwards along that motion so a 50 m/s throw cannot tunnel through a narrow entity.
        var sweep = held.velocity.scale(-0.05);
        var targets = new ArrayList<Entity>();
        for (var target : level.getEntities(player, union.inflate(1.5))) {
            if (target == projection || target.noPhysics || !canAffect(player, target)) continue;
            var last = held.impactTicks.get(target.getUUID());
            if (last != null && now - last < IMPACT_COOLDOWN_TICKS) continue;
            var targetBox = target.getBoundingBox();
            if (projection.supports(targetBox, SUPPORT_TOLERANCE)) continue;
            var sweptTarget = targetBox.expandTowards(sweep);
            if (boxes.stream().noneMatch(box -> box.intersects(sweptTarget))) continue;
            targets.add(target);
        }
        for (var target : targets) settleImpact(player, held, projection, target, union.getCenter());
        held.impactTicks.entrySet().removeIf(entry -> now - entry.getValue() > IMPACT_COOLDOWN_TICKS * 4L);
    }

    private static void settleImpact(ServerPlayer player, Session held,
                                     @Nullable NativeStructureProjectionEntity direct, Entity target, Vec3 center) {
        var level = (ServerLevel) player.level();
        var skill = Skills.KINETIC_THROW.get();
        var damage = KineticThrow.structureDamage(held.blockCount);
        if (target instanceof LivingEntity living && damage > 0.0f) {
            var before = living.getHealth();
            var source = direct == null ? SkillDamageSource.of(player, skill)
                    : SkillDamageSource.ofDirect(player, skill, direct);
            var accepted = living.hurtServer(level, source, damage);
            AcademyCraft.LOGGER.info(
                    "NATIVE_STRUCTURE_KINETIC_IMPACT tick={} target={} damage={} before={} after={} accepted={}",
                    level.getGameTime(), target.getStringUUID(), damage, before, living.getHealth(), accepted);
            if (accepted) {
                skill.onHurt(player, living, damage);
            }
        }
        var movement = held.velocity;
        if (movement.lengthSqr() > 1.0e-8 && EntityMotionGuard.canApplyMotionFrom(player, target)) {
            var milestoneScale = skill.hasProficiencyMilestone(player, 3) ? 1.25 : 1.0;
            var knockback = Math.min(4.0, 0.6 + Math.sqrt(held.blockCount) * 0.08) * milestoneScale;
            var impulse = movement.normalize().scale(knockback).add(0.0, 0.15, 0.0);
            EntityMotionGuard.runWithMotionSource(player, () -> {
                target.setDeltaMovement(target.getDeltaMovement().add(impulse));
                target.hurtMarked = true;
            });
        }
        held.impactTicks.put(target.getUUID(), level.getGameTime());
        held.impactCount = Math.incrementExact(held.impactCount);
        if (!held.impactCuePlayed) {
            held.impactCuePlayed = true;
            playImpactCue(level, center, held.tier);
        }
    }

    private static boolean canAffect(ServerPlayer player, Entity target) {
        return target != player
                && target.isAlive()
                && !target.isSpectator()
                && !TeamRelations.areAllied(player, target)
                && !PvpSetting.shouldPrevent(player, target);
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
        return sableMode(level) ? SABLE_CELL_BUDGET : NATIVE_CELL_BUDGET;
    }

    private static boolean sableMode(Level level) {
        return StructureBackendRouter.status(level.getServer()).mode() == StructureBackendStatus.Mode.SABLE;
    }

    private static boolean isSable(StructureRef reference) {
        return StructureBackendRouter.SABLE_ID.equals(reference.backend());
    }

    private static boolean isIndependent(StructureRef reference) {
        return isSable(reference) || SubLevelStructureRefs.isSubLevel(reference);
    }

    private static StructureHit lookedAtStructure(ServerPlayer player, StructureRef reference, double range) {
        if (!(player.level() instanceof ServerLevel level)) return null;
        var look = player.getLookAngle();
        if (look.lengthSqr() <= 1.0e-8) return null;
        var eye = player.getEyePosition();
        var end = eye.add(look.normalize().scale(range));
        var hit = StructureQueries.raycast(level, eye, end)
                .filter(candidate -> candidate.block().structure().equals(reference)).orElse(null);
        if (hit == null) return null;
        var validation = StructureQueries.validate(level, eye, end, hit);
        if (!validation.succeeded()) {
            AcademyCraft.LOGGER.info("Kinetic Throw structure ray was rejected: {}", validation.status());
            return null;
        }
        return validation.currentHit().orElseThrow();
    }

    private static boolean aimedAtHeld(ServerPlayer player, Session held, ServerLevel level) {
        var look = player.getLookAngle();
        if (look.lengthSqr() <= 1.0e-8) return false;
        var range = Skills.KINETIC_THROW.get().scaledRange(player, KineticThrow.TARGET_RANGE);
        var eye = player.getEyePosition();
        var end = eye.add(look.scale(range));
        return StructureQueries.raycast(level, eye, end)
                .filter(hit -> hit.block().structure().equals(held.reference)).isPresent();
    }

    private static boolean viewerInsideStructure(ServerPlayer player, Session held) {
        if (!(player.level() instanceof ServerLevel level) || held.region == null) return false;
        var projection = projection(level, held.reference, held.region.bounds().inflate(16.0));
        if (projection == null) return false;
        for (var box : projection.worldCollisionBoxes()) {
            if (box.intersects(player.getBoundingBox())) return true;
        }
        return false;
    }

    private static NativeStructureProjectionEntity projection(ServerLevel level, StructureRef reference, AABB area) {
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

    private static void playKineticCue(ServerLevel level, Vec3 center) {
        level.playSound(
                null,
                center.x, center.y, center.z,
                org.academy.internal.common.sounds.SoundEvents.VECTOR_REFLECTION.get(),
                SoundSource.PLAYERS,
                1.0f,
                1.0f
        );
    }

    private static void playImpactCue(ServerLevel level, Vec3 center, int tier) {
        var radius = KineticThrow.explosionRadius(tier);
        level.sendParticles(
                ParticleTypes.EXPLOSION_EMITTER,
                center.x, center.y, center.z,
                1,
                0.0, 0.0, 0.0,
                0.0
        );
        level.sendParticles(
                ParticleTypes.EXPLOSION,
                center.x, center.y, center.z,
                radius * 8,
                radius * 0.45, radius * 0.45, radius * 0.45,
                0.08
        );
        level.playSound(
                null,
                center.x, center.y, center.z,
                SoundEvents.GENERIC_EXPLODE,
                SoundSource.BLOCKS,
                4.0f,
                0.8f + level.getRandom().nextFloat() * 0.2f
        );
    }

    private static void rejectCapture(ServerPlayer player, String detail) {
        rejectCapture(player, detail, true);
    }

    /** Pre-attachment rollback only; an attached structure must never have its pose written here. */
    private static void restoreInitialPose(StructureHandle handle, StructurePose pose) {
        try {
            handle.setPose(pose);
        } catch (RuntimeException failure) {
            AcademyCraft.LOGGER.warn("Kinetic Throw could not restore the initial pose", failure);
        }
    }

    private static void rejectCapture(ServerPlayer player, String detail, boolean report) {
        AcademyCraft.LOGGER.info("Kinetic Throw capture rejected for {}: {}", player.getUUID(), detail);
        if (report) {
            player.sendOverlayMessage(Component.translatable(
                    "message.academy.kinetic_throw.capture_rejected"));
        }
    }

    private static void terminate(UUID playerId) {
        var held = SESSIONS.remove(playerId);
        if (held == null) return;
        held.closeControl();
        // The structure, its physics region and any inertia are deliberately retained.
    }

    private static void requireThread(ServerPlayer player) {
        Objects.requireNonNull(player, "player");
        if (!player.level().getServer().isSameThread()) {
            throw new IllegalStateException("Kinetic Throw requires the owning server thread");
        }
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
     * Budgeting a lift must keep the selected cells above every retained cell. Distance-only
     * cropping can leave a cap in the world while capturing the block immediately below it,
     * even though the untrimmed selection passed the clearance test.
     */
    static List<BlockPos> cropToLiftBudget(List<BlockPos> positions, BlockPos seed, int budget) {
        var ordered = cropToBudget(positions, seed, Integer.MAX_VALUE);
        if (budget < 1 || !ordered.contains(seed)) return List.of();
        var columns = new HashMap<Long, List<BlockPos>>();
        for (var position : ordered) {
            long column = ((long) position.getX() << 32) ^ Integer.toUnsignedLong(position.getZ());
            columns.computeIfAbsent(column, ignored -> new ArrayList<>()).add(position);
        }
        var retained = new java.util.LinkedHashSet<BlockPos>();
        for (var position : ordered) {
            if (retained.contains(position)) continue;
            long column = ((long) position.getX() << 32) ^ Integer.toUnsignedLong(position.getZ());
            var dependencies = columns.get(column).stream()
                    .filter(above -> above.getY() >= position.getY() && !retained.contains(above))
                    .sorted(Comparator.comparingInt((BlockPos above) -> above.getY()).reversed()).toList();
            if (retained.size() + dependencies.size() > budget) {
                if (position.equals(seed)) return List.of();
                continue;
            }
            retained.addAll(dependencies);
        }
        return ordered.stream().filter(retained::contains).toList();
    }

    /** Smallest usable physics region around the captured cells and their vertical lift. */
    static AABB regionBounds(List<BlockPos> positions, int lift, double minimumY, double maximumY, AABB limits) {
        return regionBounds(positions, lift, 0.0, minimumY, maximumY, limits);
    }

    /**
     * Same region sizing with an initial vertical clearance: the body starts and finishes
     * {@code clearance} higher, so the whole lift path shifts up by that bounded amount.
     */
    static AABB regionBounds(List<BlockPos> positions, int lift, double clearance,
                             double minimumY, double maximumY, AABB limits) {
        Objects.requireNonNull(positions, "positions");
        if (positions.isEmpty()) return null;
        if (!Double.isFinite(clearance) || clearance < 0) {
            throw new IllegalArgumentException("Initial clearance must be finite and non-negative");
        }
        int minX = Integer.MAX_VALUE, minY = Integer.MAX_VALUE, minZ = Integer.MAX_VALUE;
        int maxX = Integer.MIN_VALUE, maxY = Integer.MIN_VALUE, maxZ = Integer.MIN_VALUE;
        for (var position : positions) {
            minX = Math.min(minX, position.getX()); maxX = Math.max(maxX, position.getX());
            minY = Math.min(minY, position.getY()); maxY = Math.max(maxY, position.getY());
            minZ = Math.min(minZ, position.getZ()); maxZ = Math.max(maxZ, position.getZ());
        }
        var required = new AABB(minX, minY + clearance, minZ,
                maxX + 1.0, maxY + 1.0 + lift + clearance, maxZ + 1.0);
        double marginX = Math.min(REGION_MARGIN, (StructurePhysics.MAX_REGION_AXIS - required.getXsize()) / 2.0);
        double marginY = Math.min(REGION_MARGIN, (StructurePhysics.MAX_REGION_AXIS - required.getYsize()) / 2.0);
        double marginZ = Math.min(REGION_MARGIN, (StructurePhysics.MAX_REGION_AXIS - required.getZsize()) / 2.0);
        if (marginX < 0 || marginY < 0 || marginZ < 0) return null;
        var bounded = new AABB(required.minX - marginX, required.minY - marginY, required.minZ - marginZ,
                required.maxX + marginX, required.maxY + marginY, required.maxZ + marginZ);
        bounded = clampInside(bounded, minimumY, maximumY, limits);
        return bounded != null && contains(bounded, required) ? bounded : null;
    }

    /** Signed distance from a box to the nearest region edge; negative when the box sticks out. */
    static double edgeDistance(AABB bounds, AABB box) {
        return Math.min(Math.min(box.minX - bounds.minX, bounds.maxX - box.maxX),
                Math.min(Math.min(box.minY - bounds.minY, bounds.maxY - box.maxY),
                        Math.min(box.minZ - bounds.minZ, bounds.maxZ - box.maxZ)));
    }

    static AABB followBounds(AABB region, AABB occupied, Vec3 velocity, double lookaheadSeconds,
                             double trailingMargin, double minimumY, double maximumY, AABB limits) {
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

    private static AABB clampInside(AABB bounds, double minimumY, double maximumY, AABB limits) {
        double sizeX = bounds.getXsize(), sizeY = bounds.getYsize(), sizeZ = bounds.getZsize();
        if (sizeX <= 0 || sizeY <= 0 || sizeZ <= 0) return null;
        if (limits.getXsize() < sizeX || limits.getZsize() < sizeZ || maximumY - minimumY < sizeY) return null;
        var center = bounds.getCenter();
        double x = Mth.clamp(center.x, limits.minX + sizeX / 2.0, limits.maxX - sizeX / 2.0);
        double y = Mth.clamp(center.y, minimumY + sizeY / 2.0, maximumY - sizeY / 2.0);
        double z = Mth.clamp(center.z, limits.minZ + sizeZ / 2.0, limits.maxZ - sizeZ / 2.0);
        return new AABB(x - sizeX / 2.0, y - sizeY / 2.0, z - sizeZ / 2.0,
                x + sizeX / 2.0, y + sizeY / 2.0, z + sizeZ / 2.0);
    }

    private static boolean contains(AABB outer, AABB inner) {
        return outer.minX <= inner.minX && outer.minY <= inner.minY && outer.minZ <= inner.minZ
                && outer.maxX >= inner.maxX && outer.maxY >= inner.maxY && outer.maxZ >= inner.maxZ;
    }

    /**
     * One captured structure under vector control. Closing the session's control keeps the
     * structure, its region and its inertia; the instance is never persisted.
     */
    public static final class Session {
        private final StructureRef reference;
        private final ResourceKey<Level> dimension;
        private final int tier;
        private final int blockCount;
        private final UUID controllerId = UUID.randomUUID();
        private final Map<UUID, Long> impactTicks = new HashMap<>();
        private final StructurePhysicsRegion region;
        private final StructureTractionSession traction;
        private Stage stage = Stage.AWAITING_PHYSICS;
        private StructurePhysicsLease lease;
        private long expiresAt;
        private Vec3 liftTarget = Vec3.ZERO;
        private Vec3 controlTarget = Vec3.ZERO;
        private KineticLiftProfile.@Nullable LiftTarget liftPlan;
        private StructurePose holdPose;
        private double liftStartY;
        private double stiffness;
        private double damping;
        private double maxForce;
        private boolean lifted;
        private boolean impactCuePlayed;
        private int impactCount;
        private int unavailableTicks;
        private boolean programMove;
        private boolean manualHover;
        private boolean sableBody;
        private boolean directControl;
        private Vec3 velocity = Vec3.ZERO;
        private @Nullable StructurePose previousImpactPose;
        private @Nullable Vec3 pendingThrowDirection;
        private @Nullable List<BlockPos> capturedCells;

        private Session(StructureRef reference, ResourceKey<Level> dimension, int tier, int blockCount,
                        @Nullable StructurePhysicsRegion region, @Nullable StructureTractionSession traction) {
            this.reference = reference;
            this.dimension = dimension;
            this.tier = tier;
            this.blockCount = blockCount;
            this.region = region;
            this.traction = traction;
            this.sableBody = traction != null;
        }

        public Stage stage() {
            return stage;
        }

        public StructureRef reference() {
            return reference;
        }

        public int blockCount() {
            return blockCount;
        }

        public boolean lifted() {
            return lifted;
        }

        public boolean controlActive() {
            if (directControl) return true;
            if (traction != null) return traction.status() == StructureTraction.Status.ACTIVE;
            return lease != null && lease.state() == StructurePhysicsLease.State.ACTIVE;
        }

        public boolean flying() {
            return stage == Stage.FLYING;
        }

        public boolean sable() {
            return isSable(reference);
        }

        public int impacts() {
            return impactCount;
        }

        /** Runtime-facing exclusive control identity; it is never persisted. */
        public UUID controllerId() {
            return controllerId;
        }

        /**
         * Capture-time world cells of this session in capture order; empty for adopted or
         * Sable sessions whose local content was not captured by this service.
         */
        public List<BlockPos> capturedCells() {
            return capturedCells == null ? List.of() : capturedCells;
        }

        private boolean usableBy(ServerPlayer player) {
            return dimension.equals(player.level().dimension())
                    && StructureSpaces.resolve(player.level().getServer(), reference)
                    .map(handle -> handle.lifecycle() == StructureLifecycle.ACTIVE)
                    .orElse(false);
        }

        private void closeControl() {
            try {
                if (lease != null) lease.close();
            } catch (RuntimeException failure) {
                AcademyCraft.LOGGER.warn("Kinetic Throw lease cleanup failed for {}", reference, failure);
            } finally {
                lease = null;
            }
            try {
                if (traction != null) traction.close();
            } catch (RuntimeException failure) {
                AcademyCraft.LOGGER.warn("Kinetic Throw traction cleanup failed for {}", reference, failure);
            }
            if (directControl) {
                directControl = false;
                StructureDirectControl.release(reference, controllerId);
            }
        }
    }

    private record CaptureState(
            StructureRef reference,
            ResourceKey<Level> dimension,
            int tier,
            List<BlockPos> positions,
            UUID transactionId,
            long startedAt,
            boolean sable,
            boolean report,
            boolean program
    ) {
        private int blockCount() {
            return positions.size();
        }
    }
}
