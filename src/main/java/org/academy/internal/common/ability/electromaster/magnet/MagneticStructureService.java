package org.academy.internal.common.ability.electromaster.magnet;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.academy.AcademyCraft;
import org.academy.api.common.ability.electromaster.MagneticFieldTuning;
import org.academy.api.common.ability.electromaster.MagneticMovement;
import org.academy.api.common.ability.electromaster.MagneticStructureControl;
import org.academy.api.common.ability.electromaster.MagneticTargets;
import org.academy.api.common.structure.LegacyMotionUnits;
import org.academy.api.common.structure.StructureBackendStatus;
import org.academy.api.common.structure.StructureCaptureRequest;
import org.academy.api.common.structure.StructureDirectControl;
import org.academy.api.common.structure.StructureDynamics;
import org.academy.api.common.structure.StructureLifecycle;
import org.academy.api.common.structure.StructureMotionState;
import org.academy.api.common.structure.StructurePhysics;
import org.academy.api.common.structure.StructurePhysicsControl;
import org.academy.api.common.structure.StructurePhysicsLease;
import org.academy.api.common.structure.StructurePhysicsRegion;
import org.academy.api.common.structure.StructureRef;
import org.academy.api.common.structure.StructureSpaces;
import org.academy.api.common.structure.StructureTransactions;
import org.academy.api.server.ability.AbilityEffectPolicy;
import org.academy.api.server.ability.SkillTuning;
import org.academy.internal.common.ability.Skills;
import org.academy.internal.common.structure.StructureBackendRouter;
import org.academy.internal.common.structure.sable.SableStructureTraction;
import org.academy.internal.common.world.entity.structure.NativeStructureProjectionEntity;
import org.jspecify.annotations.Nullable;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.function.BiPredicate;

/**
 * Shared server implementation for the magnetic block path of Magnetic Field Operation and
 * later precision-program callers. The audited legacy path turned one magnetically admitted
 * block into a {@code FallingBlockEntity}; this service captures that exact cell through the
 * backend-routed capture transaction instead, then pulls it with the shared native force
 * control. The captured structure is never restored to blocks and never falls back to a
 * legacy entity.
 */
public final class MagneticStructureService {
    public static final int NATIVE_CELL_BUDGET = 128;
    public static final int SABLE_CELL_BUDGET = 256;
    public static final double REGION_MARGIN = 6.0;
    public static final double FOLLOW_TRIGGER = 2.0;
    public static final double FOLLOW_LOOKAHEAD_SECONDS = 0.5;
    public static final double FOLLOW_TRAILING_MARGIN = 2.0;
    public static final double ANGULAR_MARGIN_SECONDS = 0.05;
    /** Program extraction budget ceiling; zero keeps the backend default. */
    public static final int MAXIMUM_PROGRAM_BUDGET = 256;
    /** Bounded distance between the body center and a program move target. */
    public static final double MAXIMUM_PROGRAM_MOVE_RANGE = 32.0;
    /** A program move or an idle control window never extends beyond this many ticks. */
    public static final long MAXIMUM_PROGRAM_MOVE_TICKS = 600L;
    /** Explicit, paid forced-magnetization overlay duration in ticks. */
    public static final int MAGNETIZATION_TICKS = 200;
    public static final double PROGRAM_STOP_DISTANCE = 0.65;
    public static final double PROGRAM_CONTROL_RANGE = 32.0;
    /** Legacy magnetic launch speed in blocks per tick before per-program scaling. */
    public static final double LAUNCH_SPEED_PER_TICK = 1.4;

    private static final long CAPTURE_TIMEOUT_TICKS = 600;
    private static final int REGION_UNAVAILABLE_GRACE_TICKS = 40;

    private static final Map<UUID, ProgramControl> PROGRAM_CONTROLS = new HashMap<>();

    private MagneticStructureService() {
    }

    public enum Stage {
        CAPTURING, SABLE_RETAINED, AWAITING_PHYSICS, PULLING
    }

    /**
     * Starts a backend-routed capture for the magnetically admitted seed cell. The returned
     * session may still be pending; poll it every tick. Rejections log {@code detail} and
     * report the skill message while the source block is retained.
     */
    public static Optional<Session> begin(ServerLevel level, ServerPlayer player, List<BlockPos> positions) {
        Objects.requireNonNull(level, "level");
        Objects.requireNonNull(player, "player");
        Objects.requireNonNull(positions, "positions");
        if (positions.isEmpty()) {
            reject(player, "selection_empty");
            return Optional.empty();
        }
        var seed = positions.getFirst().immutable();
        var admission = admit(player, seed);
        if (admission != null) {
            reject(player, admission);
            return Optional.empty();
        }
        var budget = cellBudget(level);
        var cropped = cropToBudget(positions, seed, budget);
        if (cropped.isEmpty() || !cropped.contains(seed)) {
            reject(player, "budget_crop_lost_seed");
            return Optional.empty();
        }
        var result = StructureTransactions.capture(level, new StructureCaptureRequest(
                cropped, budget, StructureCaptureRequest.MAXIMUM_SPAN, capturePermission(player)));
        var reference = result.structure().orElse(null);
        if (result.pending()) {
            if (reference == null) {
                reject(player, "pending_without_reference");
                return Optional.empty();
            }
            return Optional.of(new Session(reference, result.transactionId(), cropped, level.getGameTime()));
        }
        if (!result.succeeded()) {
            reject(player, result.status() + ":" + result.detail());
            return Optional.empty();
        }
        if (reference == null) {
            reject(player, "committed_without_reference");
            return Optional.empty();
        }
        return Optional.of(new Session(reference, null, cropped, level.getGameTime()));
    }

    /** Legacy admission for one block target; {@code null} means the cell may be captured. */
    static @Nullable String admit(ServerPlayer player, BlockPos pos) {
        return admit(player, pos, false);
    }

    /**
     * Same admission with an explicit forced-magnetization switch: a caller that paid for the
     * bounded overlay may also admit otherwise non-magnetic editable cells. Every other gate
     * (destruction policy, chunk, permission, block entity, indestructible) still applies.
     */
    static @Nullable String admit(ServerPlayer player, BlockPos pos, boolean allowNonMagnetic) {
        var level = player.level();
        if (AbilityEffectPolicy.blockDestruction(level) == AbilityEffectPolicy.Decision.DENY) {
            return "destruction_denied";
        }
        if (!level.hasChunkAt(pos)) return "chunk_unloaded";
        if (!level.mayInteract(player, pos)) return "permission_denied";
        if (!SkillTuning.allowBlockDestruction(player, Skills.MAGNET_MANIPULATION.get())) {
            return "destruction_disabled";
        }
        var state = level.getBlockState(pos);
        if (!MagneticTargets.isMagnetic(state) && !allowNonMagnetic) return "not_magnetic";
        if (state.hasBlockEntity()) return "block_entity_not_supported";
        if (state.getDestroySpeed(level, pos) < 0.0f) return "indestructible";
        return null;
    }

    /**
     * Bounded program extraction selection: deterministic breadth-first over admitted direct
     * neighbours. Natively magnetic cells always qualify; forced magnetization additionally
     * admits non-magnetic editable cells for the lifetime of its overlay. Deterministic
     * direction order and the budget cap keep identical requests reproducible.
     */
    static List<BlockPos> magneticSelection(ServerPlayer player, BlockPos seed, int budget,
                                            boolean forceMagnetize) {
        Objects.requireNonNull(player, "player");
        Objects.requireNonNull(seed, "seed");
        if (budget < 1) return List.of();
        var immutableSeed = seed.immutable();
        if (admit(player, immutableSeed, forceMagnetize) != null) return List.of();
        var kept = new ArrayList<BlockPos>(Math.min(budget, 64));
        var seen = new HashSet<BlockPos>();
        var queue = new ArrayDeque<BlockPos>();
        seen.add(immutableSeed);
        queue.addLast(immutableSeed);
        kept.add(immutableSeed);
        while (!queue.isEmpty() && kept.size() < budget) {
            var current = queue.removeFirst();
            for (var direction : Direction.values()) {
                if (kept.size() >= budget) break;
                var next = current.relative(direction).immutable();
                if (!seen.add(next)) continue;
                if (admit(player, next, forceMagnetize) != null) continue;
                kept.add(next);
                queue.addLast(next);
            }
        }
        return List.copyOf(kept);
    }

    static int countMagnetic(ServerLevel level, List<BlockPos> positions) {
        var count = 0;
        for (var position : positions) {
            if (MagneticTargets.isMagnetic(level.getBlockState(position))) count++;
        }
        return count;
    }

    private static Vec3 selectionCenter(List<BlockPos> positions) {
        AABB bounds = null;
        for (var position : positions) {
            var cell = new AABB(position);
            bounds = bounds == null ? cell : bounds.minmax(cell);
        }
        return bounds == null ? Vec3.ZERO : bounds.getCenter();
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

    /** Smallest usable physics region around the captured cells, centred by a bounded margin. */
    static AABB regionBounds(List<BlockPos> positions, double minimumY, double maximumY, AABB limits) {
        Objects.requireNonNull(positions, "positions");
        if (positions.isEmpty()) return null;
        int minX = Integer.MAX_VALUE, minY = Integer.MAX_VALUE, minZ = Integer.MAX_VALUE;
        int maxX = Integer.MIN_VALUE, maxY = Integer.MIN_VALUE, maxZ = Integer.MIN_VALUE;
        for (var position : positions) {
            minX = Math.min(minX, position.getX()); maxX = Math.max(maxX, position.getX());
            minY = Math.min(minY, position.getY()); maxY = Math.max(maxY, position.getY());
            minZ = Math.min(minZ, position.getZ()); maxZ = Math.max(maxZ, position.getZ());
        }
        var required = new AABB(minX, minY, minZ, maxX + 1.0, maxY + 1.0, maxZ + 1.0);
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

    static int cellBudget(ServerLevel level) {
        return sableMode(level) ? SABLE_CELL_BUDGET : NATIVE_CELL_BUDGET;
    }

    static boolean sableMode(Level level) {
        return StructureBackendRouter.status(level.getServer()).mode() == StructureBackendStatus.Mode.SABLE;
    }

    static boolean sable(StructureRef reference) {
        return StructureBackendRouter.SABLE_ID.equals(reference.backend());
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
     * Program extraction entry: bounded magnetic-neighbourhood capture through the same staged
     * content transaction as the skill path without skill messages. A pending capture publishes
     * no structure reference; the source blocks stay untouched on every rejection. Forced
     * magnetization is a separately authorized, paid and time-limited overlay recorded on the
     * program session, never a permanent grant.
     */
    public static ProgramResult programExtract(ServerPlayer player, BlockPos seed, int budget,
                                               boolean forceMagnetize) {
        requireThread(player);
        Objects.requireNonNull(seed, "seed");
        if (budget < 0 || budget > MAXIMUM_PROGRAM_BUDGET) {
            return ProgramResult.rejected("invalid_budget");
        }
        if (!(player.level() instanceof ServerLevel level)) {
            return ProgramResult.rejected("host_unavailable");
        }
        var immutableSeed = seed.immutable();
        var admission = admit(player, immutableSeed, forceMagnetize);
        if (admission != null) return ProgramResult.rejected(admission);
        var limit = cellBudget(level);
        if (budget > 0) limit = Math.min(limit, budget);
        var selection = magneticSelection(player, immutableSeed, limit, forceMagnetize);
        if (selection.isEmpty()) return ProgramResult.rejected("selection_empty");
        var magneticCells = countMagnetic(level, selection);
        var result = StructureTransactions.capture(level, new StructureCaptureRequest(
                selection, limit, StructureCaptureRequest.MAXIMUM_SPAN, capturePermission(player)));
        var reference = result.structure().orElse(null);
        if (result.pending()) {
            if (reference == null) return ProgramResult.rejected("pending_without_reference");
            var pendingSession = new Session(reference, result.transactionId(), selection, level.getGameTime());
            pendingSession.report = false;
            pendingSession.program = true;
            registerProgram(player, pendingSession, magneticCells, forceMagnetize);
            return ProgramResult.pending(result.status() + ":" + result.detail());
        }
        if (!result.succeeded()) return ProgramResult.rejected(result.status() + ":" + result.detail());
        if (reference == null) return ProgramResult.rejected("committed_without_reference");
        var session = new Session(reference, null, selection, level.getGameTime());
        session.report = false;
        session.program = true;
        registerProgram(player, session, magneticCells, forceMagnetize);
        return ProgramResult.completed("committed", reference);
    }

    /**
     * Program control entry. An existing session of the same structure is reused; otherwise one
     * active, unleased physics region of the requested epoch is adopted under a new exclusive
     * session. Another actor's program control or occupied lease is rejected instead of stolen.
     * Sable bodies receive a lightweight direct-velocity lease: no force, no mass gate, and the
     * structure is never turned into a legacy entity.
     */
    public static ProgramResult programControl(ServerPlayer player, StructureRef reference, UUID runtimeId) {
        requireThread(player);
        Objects.requireNonNull(reference, "reference");
        Objects.requireNonNull(runtimeId, "runtimeId");
        var playerId = player.getUUID();
        var existing = PROGRAM_CONTROLS.get(playerId);
        if (existing != null && existing.session.reference().equals(reference)) {
            if (sable(reference)) {
                return controlSableExisting(player, existing, runtimeId);
            }
            if (!programControlUsable(player, existing.session, runtimeId)) {
                return ProgramResult.rejected("structure_unavailable");
            }
            return ProgramResult.completed("control_active", reference);
        }
        for (var entry : PROGRAM_CONTROLS.entrySet()) {
            if (!entry.getKey().equals(playerId) && entry.getValue().session.reference().equals(reference)) {
                return ProgramResult.rejected("structure_controlled");
            }
        }
        if (!(player.level() instanceof ServerLevel level)) {
            return ProgramResult.rejected("host_unavailable");
        }
        if (sable(reference)) return controlSable(player, level, reference, runtimeId);
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
        if (region.control(reference).isPresent()) {
            return ProgramResult.rejected("structure_controlled");
        }
        var motion = region.motion(reference).orElse(null);
        if (motion == null) return ProgramResult.rejected("motion_unavailable");
        var captured = new Session(reference, List.of(), level.getGameTime(), region);
        captured.report = false;
        captured.program = true;
        // Adopting another structure first ends the previous session while retaining that
        // structure, its region and its inertia.
        releaseProgram(playerId, "replaced");
        PROGRAM_CONTROLS.put(playerId, new ProgramControl(player, captured,
                motion.pose().localToWorldPoint(motion.localCenterOfMass()), 0, 0, 0L));
        return ProgramResult.completed("control_acquired", reference);
    }

    private static ProgramResult controlSableExisting(ServerPlayer player, ProgramControl existing, UUID runtimeId) {
        if (!(player.level() instanceof ServerLevel level)) {
            return ProgramResult.rejected("host_unavailable");
        }
        var session = existing.session;
        if (session.pending()) return ProgramResult.rejected("capture_pending");
        var handle = StructureSpaces.resolve(level.getServer(), session.reference()).orElse(null);
        if (handle == null || handle.lifecycle() != StructureLifecycle.ACTIVE) {
            releaseProgram(player.getUUID(), "structure_unavailable");
            return ProgramResult.rejected("structure_unavailable");
        }
        if (!handle.runtimeId().equals(runtimeId)) {
            // The bound body epoch is gone; the stale token must not regain authority.
            releaseProgram(player.getUUID(), "stale_structure_epoch");
            return ProgramResult.rejected("stale_structure_epoch");
        }
        if (session.directControl) {
            var status = StructureDirectControl.validate(level, session.reference(), player, session.controllerId());
            if (status == StructureDirectControl.Status.ACTIVE) {
                return ProgramResult.completed("control_active", session.reference());
            }
            releaseProgram(player.getUUID(),
                    status == StructureDirectControl.Status.OCCUPIED ? "structure_controlled" : "structure_unavailable");
            return ProgramResult.rejected(status == StructureDirectControl.Status.OCCUPIED
                    ? "structure_controlled" : "structure_unavailable");
        }
        var status = StructureDirectControl.acquire(level, session.reference(), player, session.controllerId());
        if (status == StructureDirectControl.Status.OCCUPIED) {
            return ProgramResult.rejected("structure_controlled");
        }
        if (status != StructureDirectControl.Status.ACTIVE) {
            return ProgramResult.rejected("structure_unavailable");
        }
        session.directControl = true;
        session.stage = Stage.SABLE_RETAINED;
        return ProgramResult.completed("control_acquired", session.reference());
    }

    private static ProgramResult controlSable(ServerPlayer player, ServerLevel level,
                                              StructureRef reference, UUID runtimeId) {
        var handle = StructureSpaces.resolve(level.getServer(), reference).orElse(null);
        if (handle == null || handle.lifecycle() != StructureLifecycle.ACTIVE) {
            return ProgramResult.rejected("structure_unavailable");
        }
        if (!handle.runtimeId().equals(runtimeId)) {
            return ProgramResult.rejected("stale_structure_epoch");
        }
        var session = new Session(reference, null, List.of(), level.getGameTime());
        session.report = false;
        session.program = true;
        var status = StructureDirectControl.acquire(level, reference, player, session.controllerId());
        if (status == StructureDirectControl.Status.OCCUPIED) {
            return ProgramResult.rejected("structure_controlled");
        }
        if (status != StructureDirectControl.Status.ACTIVE) {
            return ProgramResult.rejected("structure_unavailable");
        }
        session.directControl = true;
        session.stage = Stage.SABLE_RETAINED;
        releaseProgram(player.getUUID(), "replaced");
        PROGRAM_CONTROLS.put(player.getUUID(), new ProgramControl(player, session, Vec3.ZERO, 0, 0, 0L));
        return ProgramResult.completed("control_acquired", reference);
    }

    /**
     * Program move entry: a bounded anchor correction toward an explicit world target while the
     * program session keeps its converted pull profile. Force is scaled by magnetic
     * participation, and the optional local force point becomes the application point. Expired
     * magnetization, lost regions and range violations end the action while retaining the body.
     */
    public static ProgramResult programMove(ServerPlayer player, StructureRef reference, UUID controlId,
                                            Vec3 target, long durationTicks,
                                            @Nullable BlockPos localForcePoint) {
        requireThread(player);
        var control = matchingProgram(player, reference, controlId);
        if (control == null) return ProgramResult.rejected("no_matching_control");
        var session = control.session;
        if (session.stage() == Stage.CAPTURING) {
            return ProgramResult.rejected("not_movable:capturing");
        }
        if (!isFiniteVector(target)) return ProgramResult.rejected("invalid_target");
        if (!(player.level() instanceof ServerLevel level)) {
            return ProgramResult.rejected("host_unavailable");
        }
        var block = BlockPos.containing(target);
        if (!level.hasChunkAt(block) || !level.getWorldBorder().isWithinBounds(block)) {
            return ProgramResult.rejected("target_unavailable");
        }
        var motion = StructureDynamics.motion(level, reference).orElse(null);
        var referencePoint = motion != null
                ? motion.pose().localToWorldPoint(motion.localCenterOfMass())
                : session.positions.isEmpty() ? null : selectionCenter(session.positions);
        if (referencePoint != null && referencePoint.distanceTo(target) > MAXIMUM_PROGRAM_MOVE_RANGE) {
            return ProgramResult.rejected("target_out_of_range");
        }
        if (control.participation(level.getGameTime()) <= 0.0) {
            return ProgramResult.rejected("no_magnetic_participation");
        }
        control.target = target;
        control.moving = true;
        control.localForcePoint = localForcePoint == null ? null : localForcePoint.immutable();
        control.moveExpiresAt = level.getGameTime()
                + Mth.clamp(durationTicks, 1L, MAXIMUM_PROGRAM_MOVE_TICKS);
        return ProgramResult.completed("move", reference);
    }

    /**
     * Program launch entry: one bounded impulse along an explicit direction from the control
     * that already holds the body. Damage is never pre-settled along a preset trajectory; the
     * session's field and magnetization overlay are withdrawn afterwards while the structure,
     * its region and its new inertia are retained.
     */
    public static ProgramResult programLaunch(ServerPlayer player, StructureRef reference, UUID controlId,
                                              Vec3 direction, double speedScale) {
        requireThread(player);
        var control = matchingProgram(player, reference, controlId);
        if (control == null) return ProgramResult.rejected("no_matching_control");
        var session = control.session;
        if (session.directControl) {
            if (!isFiniteVector(direction) || direction.lengthSqr() <= 1.0e-8) {
                return ProgramResult.rejected("invalid_direction");
            }
            if (!Double.isFinite(speedScale) || speedScale < 0.1 || speedScale > 2.0) {
                return ProgramResult.rejected("invalid_strength");
            }
            return launchSable(player, control, direction.normalize(), speedScale);
        }
        if (session.stage() != Stage.PULLING) {
            return ProgramResult.rejected("not_launchable:" + session.stage().name().toLowerCase(Locale.ROOT));
        }
        if (!isFiniteVector(direction) || direction.lengthSqr() <= 1.0e-8) {
            return ProgramResult.rejected("invalid_direction");
        }
        if (!Double.isFinite(speedScale) || speedScale < 0.1 || speedScale > 2.0) {
            return ProgramResult.rejected("invalid_strength");
        }
        var region = session.region;
        if (region == null) return ProgramResult.rejected("motion_unavailable");
        var motion = region.motion(reference).orElse(null);
        if (motion == null) return ProgramResult.rejected("motion_unavailable");
        var speed = launchSpeed(speedScale);
        var mass = motion.mass();
        var center = motion.pose().localToWorldPoint(motion.localCenterOfMass());
        var directionUnit = direction.normalize();
        session.close();
        try {
            // Clear any residual pull momentum first so this single impulse defines the launch
            // velocity exactly; contact effects belong to the flight, not to this request.
            region.setVelocity(reference, Vec3.ZERO, Vec3.ZERO);
            region.applyImpulse(reference, directionUnit.scale(speed * mass), center);
        } catch (RuntimeException | AssertionError failure) {
            AcademyCraft.LOGGER.error("Magnetic program launch was rejected for {}", reference, failure);
            PROGRAM_CONTROLS.remove(player.getUUID());
            return ProgramResult.rejected("launch_rejected");
        }
        PROGRAM_CONTROLS.remove(player.getUUID());
        return ProgramResult.completed("launched", reference);
    }

    /**
     * One direct-velocity launch for a Sable body under a lightweight lease. The legacy per-tick
     * launch speed converts to the same per-second value as the native path and no mass budget
     * participates. The session's field and magnetization overlay are withdrawn before the
     * hand-off; no preset-trajectory damage is settled.
     */
    private static ProgramResult launchSable(ServerPlayer player, ProgramControl control,
                                             Vec3 direction, double speedScale) {
        if (!(player.level() instanceof ServerLevel level)) {
            return ProgramResult.rejected("host_unavailable");
        }
        var session = control.session;
        var status = StructureDirectControl.validate(level, session.reference(), player, session.controllerId());
        if (status != StructureDirectControl.Status.ACTIVE) {
            releaseProgram(player.getUUID(),
                    status == StructureDirectControl.Status.OCCUPIED ? "structure_controlled" : "structure_unavailable");
            return ProgramResult.rejected(status == StructureDirectControl.Status.OCCUPIED
                    ? "structure_controlled" : "structure_unavailable");
        }
        var reference = session.reference();
        var speed = launchSpeed(speedScale);
        releaseProgram(player.getUUID(), "launched");
        if (!StructureBackendRouter.launch(level, reference, direction, speed)) {
            return ProgramResult.rejected("sable_launch_rejected");
        }
        return ProgramResult.completed("launched", reference);
    }

    /**
     * Calibrated program launch speed shared by the native impulse and the Sable direct-velocity
     * paths: the audited legacy per-tick launch speed converted to the solver's per-second units.
     */
    public static double launchSpeed(double speedScale) {
        if (!Double.isFinite(speedScale) || speedScale < 0.1 || speedScale > 2.0) {
            throw new IllegalArgumentException("Launch strength must be finite and in [0.1, 2.0]");
        }
        return LegacyMotionUnits.velocityPerSecond(LAUNCH_SPEED_PER_TICK) * speedScale;
    }

    /**
     * Program release entry: idempotent and token-scoped. A stale or foreign token is a no-op
     * and never terminates a successor's control; the structure, its region and its inertia
     * are retained and this session's field and magnetization overlay are withdrawn.
     */
    public static ProgramResult programRelease(ServerPlayer player, StructureRef reference, UUID controlId) {
        requireThread(player);
        var control = PROGRAM_CONTROLS.get(player.getUUID());
        if (control == null || !control.session.reference().equals(reference)
                || !control.session.controllerId().equals(controlId)) {
            return ProgramResult.completed("release_noop", null);
        }
        releaseProgram(player.getUUID(), "released");
        return ProgramResult.completed("released", reference);
    }

    /** The current program session for this player, if any. It is a view, not a lease. */
    public static Optional<Session> programSession(ServerPlayer player) {
        requireThread(player);
        var control = PROGRAM_CONTROLS.get(player.getUUID());
        return control == null ? Optional.empty() : Optional.of(control.session);
    }

    /** Silent lifecycle cleanup; the captured structure, inertia and physics region are retained. */
    public static void releasePrograms(ServerPlayer player) {
        if (player == null) return;
        releaseProgram(player.getUUID(), "controller_unavailable");
    }

    /** Server shutdown cleanup; never throws into the stopping server. */
    public static void clearProgramsAll() {
        for (var playerId : List.copyOf(PROGRAM_CONTROLS.keySet())) {
            try {
                releaseProgram(playerId, "shutdown");
            } catch (RuntimeException failure) {
                AcademyCraft.LOGGER.warn("Magnetic program shutdown cleanup failed for {}", playerId, failure);
            }
        }
    }

    /** Drives every program-controlled magnetic session once on the owning server thread. */
    public static void tickPrograms(MinecraftServer server) {
        if (server == null) return;
        for (var playerId : List.copyOf(PROGRAM_CONTROLS.keySet())) {
            var control = PROGRAM_CONTROLS.get(playerId);
            if (control == null) continue;
            var player = control.controller;
            if (player.hasDisconnected() || !player.isAlive()
                    || !(player.level() instanceof ServerLevel level)
                    || level.getServer() != server) {
                releaseProgram(playerId, "controller_unavailable");
                continue;
            }
            var session = control.session;
            var gameTime = level.getGameTime();
            if (control.magnetizeExpiresAt != 0L && gameTime > control.magnetizeExpiresAt) {
                control.magnetizeExpiresAt = 0L;
                AcademyCraft.LOGGER.info(
                        "Forced magnetization expired for {}; magnetic participation is now {}",
                        session.reference(), control.participation(gameTime));
                if (control.participation(gameTime) <= 0.0 && !session.directControl) {
                    releaseProgram(playerId, "magnetization_expired");
                    continue;
                }
            }
            if (control.moveExpiresAt == 0L) {
                if (session.stage() != Stage.CAPTURING) {
                    control.moveExpiresAt = gameTime + MAXIMUM_PROGRAM_MOVE_TICKS;
                }
            } else if (gameTime > control.moveExpiresAt) {
                releaseProgram(playerId, "move_window_ended");
                continue;
            }
            if (session.directControl) {
                if (StructureDirectControl.validate(level, session.reference(), player, session.controllerId())
                        != StructureDirectControl.Status.ACTIVE) {
                    releaseProgram(playerId, "structure_unavailable");
                    continue;
                }
                if (!control.moving) continue;
                var motion = StructureDynamics.motion(level, session.reference()).orElse(null);
                if (motion == null) continue;
                var point = control.localForcePoint == null
                        ? motion.localCenterOfMass() : Vec3.atCenterOf(control.localForcePoint);
                var origin = motion.pose().localToWorldPoint(point);
                var speed = MagneticFieldTuning.targetPullSpeed(
                        Skills.MAGNET_MANIPULATION.get().getEffectiveProficiencyMilestone(player));
                var force = MagneticStructureControl.pullForce(motion.linearVelocity(), origin,
                        control.target, Vec3.ZERO, speed, PROGRAM_STOP_DISTANCE,
                        motion.mass(), control.participation(gameTime));
                if (!StructureDirectControl.submit(level, session.reference(), player, session.controllerId(),
                        new StructurePhysicsControl.Force(point, force))) {
                    releaseProgram(playerId, "control_unavailable");
                }
                continue;
            }
            session.participation = control.participation(gameTime);
            session.forcePoint = control.localForcePoint;
            var milestone = Skills.MAGNET_MANIPULATION.get().getEffectiveProficiencyMilestone(player);
            var speed = MagneticFieldTuning.targetPullSpeed(milestone);
            if (!session.tick(player, control.target, Vec3.ZERO, speed,
                    PROGRAM_STOP_DISTANCE, PROGRAM_CONTROL_RANGE, milestone)) {
                releaseProgram(playerId, "session_ended");
            }
        }
    }

    private static void registerProgram(ServerPlayer player, Session session, int magneticCells,
                                        boolean forceMagnetize) {
        var gameTime = player.level().getGameTime();
        releaseProgram(player.getUUID(), "replaced");
        var overlay = forceMagnetize && magneticCells < session.blockCount()
                ? gameTime + MAGNETIZATION_TICKS : 0L;
        PROGRAM_CONTROLS.put(player.getUUID(), new ProgramControl(player, session,
                selectionCenter(session.positions), magneticCells, session.blockCount(), overlay));
    }

    private static @Nullable ProgramControl matchingProgram(ServerPlayer player, StructureRef reference,
                                                            UUID controlId) {
        Objects.requireNonNull(reference, "reference");
        Objects.requireNonNull(controlId, "controlId");
        var control = PROGRAM_CONTROLS.get(player.getUUID());
        if (control == null || !control.session.reference().equals(reference)
                || !control.session.controllerId().equals(controlId)) {
            return null;
        }
        return control;
    }

    private static boolean programControlUsable(ServerPlayer player, Session session, UUID runtimeId) {
        if (session.stage() == Stage.CAPTURING) return false;
        if (!(player.level() instanceof ServerLevel level)) return false;
        var handle = StructureSpaces.resolve(level.getServer(), session.reference()).orElse(null);
        return handle != null && handle.lifecycle() == StructureLifecycle.ACTIVE
                && handle.runtimeId().equals(runtimeId);
    }

    private static void releaseProgram(UUID playerId, String detail) {
        var control = PROGRAM_CONTROLS.remove(playerId);
        if (control == null) return;
        AcademyCraft.LOGGER.info("Magnetic program session {} for {} on {}",
                detail, playerId, control.session.reference());
        if (control.session.directControl) {
            StructureDirectControl.release(control.session.reference(), control.session.controllerId());
        }
        control.session.close();
    }

    private static boolean isFiniteVector(Vec3 value) {
        return value != null
                && Double.isFinite(value.x)
                && Double.isFinite(value.y)
                && Double.isFinite(value.z);
    }

    private static void requireThread(ServerPlayer player) {
        Objects.requireNonNull(player, "player");
        if (!player.level().getServer().isSameThread()) {
            throw new IllegalStateException("Magnetic structure service requires the owning server thread");
        }
    }

    /** One program-driven magnetic session; never persisted and never revived across sessions. */
    private static final class ProgramControl {
        private final ServerPlayer controller;
        private final Session session;
        private final int magneticCells;
        private final int totalCells;
        private Vec3 target;
        private boolean moving;
        private long moveExpiresAt;
        private long magnetizeExpiresAt;
        private @Nullable BlockPos localForcePoint;

        private ProgramControl(ServerPlayer controller, Session session, Vec3 target,
                               int magneticCells, int totalCells, long magnetizeExpiresAt) {
            this.controller = controller;
            this.session = session;
            this.target = target;
            this.magneticCells = magneticCells;
            this.totalCells = totalCells;
            this.magnetizeExpiresAt = magnetizeExpiresAt;
        }

        private double participation(long gameTime) {
            if (magnetizeExpiresAt != 0L && gameTime <= magnetizeExpiresAt) return 1.0;
            return MagneticStructureControl.magneticParticipation(magneticCells, totalCells);
        }
    }

    private static BiPredicate<ServerLevel, BlockPos> capturePermission(ServerPlayer player) {
        return (level, position) -> level.mayInteract(player, position);
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

    private static void reject(ServerPlayer player, String detail) {
        reject(player, detail, true);
    }

    private static void reject(ServerPlayer player, String detail, boolean report) {
        AcademyCraft.LOGGER.info("Magnetic pull rejected for {}: {}", player.getUUID(), detail);
        if (report) {
            player.sendOverlayMessage(Component.translatable(
                    "message.academy.magnet_manipulation.structure_pull_rejected"));
        }
    }

    /**
     * One player's magnetic pull on the captured structure. Tick it once per server tick;
     * a terminal tick releases the lease and keeps the structure and its region active.
     */
    public static final class Session implements AutoCloseable {
        private final StructureRef reference;
        private final List<BlockPos> positions;
        private final UUID controllerId = UUID.randomUUID();
        private final long startedAt;
        private @Nullable UUID transactionId;
        private @Nullable StructurePhysicsRegion region;
        private @Nullable StructurePhysicsLease lease;
        private Stage stage = Stage.CAPTURING;
        private int unavailableTicks;
        private boolean closed;
        private boolean report = true;
        private boolean program;
        private boolean directControl;
        private double participation = 1.0;
        private @Nullable BlockPos forcePoint;

        private Session(StructureRef reference, @Nullable UUID transactionId,
                        List<BlockPos> positions, long startedAt) {
            this.reference = reference;
            this.transactionId = transactionId;
            this.positions = List.copyOf(positions);
            this.startedAt = startedAt;
        }

        private Session(StructureRef reference, List<BlockPos> positions, long startedAt,
                        StructurePhysicsRegion region) {
            this.reference = reference;
            this.transactionId = null;
            this.positions = List.copyOf(positions);
            this.startedAt = startedAt;
            this.region = region;
            this.stage = Stage.AWAITING_PHYSICS;
        }

        public Stage stage() {
            return stage;
        }

        public StructureRef reference() {
            return reference;
        }

        public int blockCount() {
            return positions.size();
        }

        /**
         * Capture-time world cells of this session in capture order; empty for sessions whose
         * local content was not captured by this service.
         */
        public List<BlockPos> capturedCells() {
            return positions;
        }

        /** Runtime-facing exclusive control identity; it is never persisted. */
        public UUID controllerId() {
            return controllerId;
        }

        public boolean controlActive() {
            return directControl || lease != null && lease.state() == StructurePhysicsLease.State.ACTIVE;
        }

        /** True while the capture transaction has not committed yet. */
        private boolean pending() {
            return transactionId != null;
        }

        private void fail(ServerPlayer player, String detail) {
            reject(player, detail, report);
        }

        private Vec3 localForcePoint(StructureMotionState motion) {
            return forcePoint == null ? motion.localCenterOfMass() : Vec3.atCenterOf(forcePoint);
        }

        /** Returns true while the pull should continue; a false return has already released control. */
        public boolean tick(ServerPlayer player, Vec3 destination, Vec3 fallback, double maxSpeedPerTick,
                            double stopDistance, double range, int milestone) {
            if (closed) return false;
            if (transactionId != null) {
                if (!(player.level() instanceof ServerLevel level)) {
                    close();
                    return false;
                }
                var result = StructureTransactions.poll(level.getServer(), transactionId);
                if (result.isEmpty()) {
                    fail(player, "transaction_evicted");
                    close();
                    return false;
                }
                var current = result.get();
                if (current.pending()) {
                    if (level.getGameTime() - startedAt > CAPTURE_TIMEOUT_TICKS) {
                        AcademyCraft.LOGGER.warn(
                                "Magnetic pull capture {} exceeded its tracking window; structure retained",
                                transactionId);
                        fail(player, "transaction_tracking_timeout");
                        close();
                        return false;
                    }
                    return true;
                }
                transactionId = null;
                if (!current.succeeded()) {
                    fail(player, current.status() + ":" + current.detail());
                    close();
                    return false;
                }
            }
            if (stage == Stage.CAPTURING) {
                if (sable(reference)) {
                    if (!program) {
                        return startSableSkillPull(player, destination, fallback, maxSpeedPerTick, stopDistance, range);
                    }
                    stage = Stage.SABLE_RETAINED;
                    return true;
                }
                if (!(player.level() instanceof ServerLevel level)) {
                    close();
                    return false;
                }
                if (!openRegion(level, player)) {
                    close();
                    return false;
                }
                stage = Stage.AWAITING_PHYSICS;
                return true;
            }
            if (stage == Stage.SABLE_RETAINED) {
                if (!(player.level() instanceof ServerLevel level)) return false;
                var handle = StructureSpaces.resolve(level.getServer(), reference).orElse(null);
                return handle != null && handle.lifecycle() == StructureLifecycle.ACTIVE;
            }
            if (stage == Stage.AWAITING_PHYSICS) {
                return tickAwaitingPhysics(player, destination, fallback, maxSpeedPerTick, stopDistance, milestone);
            }
            if (sable(reference)) {
                return tickSablePull(player, destination, fallback, maxSpeedPerTick, stopDistance, range);
            }
            return tickPulling(player, destination, fallback, maxSpeedPerTick, stopDistance, range);
        }

        private boolean startSableSkillPull(ServerPlayer player, Vec3 destination, Vec3 fallback,
                                            double maxSpeedPerTick, double stopDistance, double range) {
            if (!(player.level() instanceof ServerLevel level)) {
                close();
                return false;
            }
            var status = StructureDirectControl.acquire(level, reference, player, controllerId);
            if (status != StructureDirectControl.Status.ACTIVE) {
                AcademyCraft.LOGGER.warn("Magnetic pull could not lease Sable body {}: {}", reference, status);
                // Capture already transferred ownership. Failed control must retain that content.
                fail(player, status == StructureDirectControl.Status.OCCUPIED
                        ? "structure_controlled" : "sable_pull_unavailable");
                close();
                return false;
            }
            directControl = true;
            stage = Stage.PULLING;
            return tickSablePull(player, destination, fallback, maxSpeedPerTick, stopDistance, range);
        }

        private boolean tickSablePull(ServerPlayer player, Vec3 destination, Vec3 fallback,
                                      double maxSpeedPerTick, double stopDistance, double range) {
            if (!(player.level() instanceof ServerLevel level)) {
                close();
                return false;
            }
            if (StructureDirectControl.validate(level, reference, player, controllerId)
                    != StructureDirectControl.Status.ACTIVE) {
                close();
                return false;
            }
            var motion = SableStructureTraction.motion(level, reference).orElse(null);
            if (motion == null) return continueOrGiveUp();
            unavailableTicks = 0;
            var center = motion.pose().localToWorldPoint(motion.localCenterOfMass());
            if (player.getEyePosition().distanceToSqr(center) > range * range) {
                close();
                return false;
            }
            var desired = MagneticMovement.calculatePullVelocity(
                    LegacyMotionUnits.velocityPerTick(motion.linearVelocity()),
                    center, destination, fallback, maxSpeedPerTick, stopDistance);
            if (!SableStructureTraction.setLinearVelocity(level, reference,
                    LegacyMotionUnits.velocityPerSecond(desired))) {
                return continueOrGiveUp();
            }
            return true;
        }

        private boolean openRegion(ServerLevel level, ServerPlayer player) {
            var handle = StructureSpaces.resolve(level.getServer(), reference).orElse(null);
            if (handle == null || handle.lifecycle() != StructureLifecycle.ACTIVE) {
                fail(player, "captured_handle_unavailable");
                return false;
            }
            var limits = new AABB(level.getWorldBorder().getMinX(), level.getMinY(), level.getWorldBorder().getMinZ(),
                    level.getWorldBorder().getMaxX(), level.getMaxY(), level.getWorldBorder().getMaxZ());
            var bounds = regionBounds(positions, level.getMinY(), level.getMaxY(), limits);
            if (bounds == null) {
                fail(player, "region_cannot_fit_structure");
                return false;
            }
            StructurePhysicsRegion opened;
            try {
                opened = StructurePhysics.openRegion(level, bounds);
            } catch (RuntimeException | AssertionError failure) {
                AcademyCraft.LOGGER.error("Magnetic pull could not open a physics region for {}", reference, failure);
                fail(player, "region_open_failed");
                return false;
            }
            try {
                opened.attach(handle);
            } catch (RuntimeException | AssertionError failure) {
                AcademyCraft.LOGGER.error("Magnetic pull could not attach {}", reference, failure);
                try {
                    opened.close();
                } catch (RuntimeException cleanup) {
                    failure.addSuppressed(cleanup);
                }
                fail(player, "region_attach_failed");
                return false;
            }
            region = opened;
            return true;
        }

        private boolean tickAwaitingPhysics(ServerPlayer player, Vec3 destination, Vec3 fallback,
                                            double maxSpeedPerTick, double stopDistance, int milestone) {
            var region = this.region;
            if (region == null) {
                close();
                return false;
            }
            if (region.status() == StructurePhysicsRegion.Status.FAILED) {
                AcademyCraft.LOGGER.error("Magnetic pull region failed before control: {}", region.diagnostic());
                close();
                return false;
            }
            if (region.status() != StructurePhysicsRegion.Status.READY) {
                return continueOrGiveUp();
            }
            var motion = region.motion(reference).orElse(null);
            if (motion == null) {
                return continueOrGiveUp();
            }
            unavailableTicks = 0;
            if (motion.mass() > MagneticStructureControl.massBudget(milestone)) {
                AcademyCraft.LOGGER.warn("Magnetic pull mass {} exceeds the milestone budget for {}",
                        motion.mass(), reference);
                fail(player, "mass_over_budget");
                close();
                return false;
            }
            var control = new StructurePhysicsControl.Force(localForcePoint(motion),
                    MagneticStructureControl.pullForce(motion.linearVelocity(),
                            motion.pose().localToWorldPoint(motion.localCenterOfMass()), destination, fallback,
                            maxSpeedPerTick, stopDistance, motion.mass(), participation));
            Optional<StructurePhysicsLease> acquired;
            try {
                acquired = region.acquireEntityControl(reference, player, controllerId, control,
                        MagneticStructureControl.TRACTION_LEASE_TICKS);
            } catch (IllegalStateException | IllegalArgumentException rejection) {
                AcademyCraft.LOGGER.warn("Magnetic pull control was rejected for {}", reference, rejection);
                close();
                return false;
            }
            if (acquired.isEmpty()) {
                AcademyCraft.LOGGER.warn("Magnetic pull control slot is occupied for {}", reference);
                close();
                return false;
            }
            lease = acquired.orElseThrow();
            stage = Stage.PULLING;
            return true;
        }

        private boolean tickPulling(ServerPlayer player, Vec3 destination, Vec3 fallback,
                                    double maxSpeedPerTick, double stopDistance, double range) {
            var region = this.region;
            if (region == null) {
                close();
                return false;
            }
            if (region.status() == StructurePhysicsRegion.Status.FAILED) {
                AcademyCraft.LOGGER.error("Magnetic pull region failed while pulling: {}", region.diagnostic());
                close();
                return false;
            }
            if (region.status() != StructurePhysicsRegion.Status.READY) {
                return continueOrGiveUp();
            }
            var motion = region.motion(reference).orElse(null);
            if (motion == null) {
                return continueOrGiveUp();
            }
            unavailableTicks = 0;
            var lease = this.lease;
            if (lease == null || lease.state() != StructurePhysicsLease.State.ACTIVE) {
                close();
                return false;
            }
            var center = motion.pose().localToWorldPoint(motion.localCenterOfMass());
            if (player.getEyePosition().distanceToSqr(center) > range * range) {
                close();
                return false;
            }
            try {
                lease.setControl(new StructurePhysicsControl.Force(localForcePoint(motion),
                        MagneticStructureControl.pullForce(motion.linearVelocity(), center, destination, fallback,
                                maxSpeedPerTick, stopDistance, motion.mass(), participation)));
                lease.renew(MagneticStructureControl.TRACTION_LEASE_TICKS);
            } catch (IllegalStateException | IllegalArgumentException rejection) {
                AcademyCraft.LOGGER.warn("Magnetic pull lease ended for {}", reference, rejection);
                close();
                return false;
            }
            return followRegion(player, motion);
        }

        private boolean continueOrGiveUp() {
            if (++unavailableTicks > REGION_UNAVAILABLE_GRACE_TICKS) {
                close();
                return false;
            }
            return true;
        }

        private boolean followRegion(ServerPlayer player, StructureMotionState motion) {
            if (!(player.level() instanceof ServerLevel level) || region == null) return true;
            var projection = projection(level, reference, region.bounds().inflate(16.0));
            if (projection == null) return true;
            var occupied = union(projection.worldCollisionBoxes());
            if (occupied == null) return true;
            var halfDiagonal = Math.sqrt(occupied.getXsize() * occupied.getXsize()
                    + occupied.getYsize() * occupied.getYsize()
                    + occupied.getZsize() * occupied.getZsize()) / 2.0;
            var predicted = occupied.move(motion.linearVelocity().scale(0.05))
                    .inflate(motion.angularVelocity().length() * halfDiagonal * ANGULAR_MARGIN_SECONDS);
            var bounds = region.bounds();
            if (edgeDistance(bounds, predicted) >= FOLLOW_TRIGGER) return true;
            var limits = new AABB(level.getWorldBorder().getMinX(), level.getMinY(), level.getWorldBorder().getMinZ(),
                    level.getWorldBorder().getMaxX(), level.getMaxY(), level.getWorldBorder().getMaxZ());
            var candidate = followBounds(bounds, predicted, motion.linearVelocity(), FOLLOW_LOOKAHEAD_SECONDS,
                    FOLLOW_TRAILING_MARGIN, level.getMinY(), level.getMaxY(), limits);
            if (candidate == null || candidate.equals(bounds) || !tryRebind(candidate)) {
                // A body that can no longer be kept inside a rebaseable region would fail the
                // region terminally on the next step. Ending the pull keeps the structure.
                var conservative = followBounds(bounds, occupied, motion.linearVelocity(), FOLLOW_LOOKAHEAD_SECONDS,
                        FOLLOW_TRAILING_MARGIN, level.getMinY(), level.getMaxY(), limits);
                if (conservative == null || conservative.equals(bounds) || !tryRebind(conservative)) {
                    AcademyCraft.LOGGER.warn("Magnetic pull region follow could not rebase {}; ending safely",
                            reference);
                    close();
                    return false;
                }
            }
            return true;
        }

        private boolean tryRebind(AABB candidate) {
            var region = this.region;
            if (region == null) return false;
            try {
                return region.rebind(candidate);
            } catch (RuntimeException | AssertionError failure) {
                AcademyCraft.LOGGER.error("Magnetic pull region rebase failed", failure);
                return false;
            }
        }

        /** Releases this session's transient control; the structure, region and inertia are retained. */
        @Override
        public void close() {
            if (closed) return;
            closed = true;
            try {
                if (lease != null) lease.close();
            } catch (RuntimeException failure) {
                AcademyCraft.LOGGER.warn("Magnetic pull lease cleanup failed for {}", reference, failure);
            } finally {
                lease = null;
            }
            if (directControl) {
                StructureDirectControl.release(reference, controllerId);
                directControl = false;
            }
        }
    }
}
