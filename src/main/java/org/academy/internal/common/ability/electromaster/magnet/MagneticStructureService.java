package org.academy.internal.common.ability.electromaster.magnet;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.academy.AcademyCraft;
import org.academy.api.common.ability.electromaster.MagneticStructureControl;
import org.academy.api.common.ability.electromaster.MagneticTargets;
import org.academy.api.common.structure.StructureBackendStatus;
import org.academy.api.common.structure.StructureCaptureRequest;
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
import org.academy.internal.common.world.entity.structure.NativeStructureProjectionEntity;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
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

    private static final long CAPTURE_TIMEOUT_TICKS = 600;
    private static final int REGION_UNAVAILABLE_GRACE_TICKS = 40;

    private MagneticStructureService() {
    }

    public enum Stage {
        CAPTURING, AWAITING_PHYSICS, PULLING
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
        if (!MagneticTargets.isMagnetic(state)) return "not_magnetic";
        if (state.hasBlockEntity()) return "block_entity_not_supported";
        if (state.getDestroySpeed(level, pos) < 0.0f) return "indestructible";
        return null;
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
        AcademyCraft.LOGGER.info("Magnetic pull rejected for {}: {}", player.getUUID(), detail);
        player.sendOverlayMessage(Component.translatable(
                "message.academy.magnet_manipulation.structure_pull_rejected"));
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

        private Session(StructureRef reference, @Nullable UUID transactionId,
                        List<BlockPos> positions, long startedAt) {
            this.reference = reference;
            this.transactionId = transactionId;
            this.positions = List.copyOf(positions);
            this.startedAt = startedAt;
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

        public boolean controlActive() {
            return lease != null && lease.state() == StructurePhysicsLease.State.ACTIVE;
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
                    reject(player, "transaction_evicted");
                    close();
                    return false;
                }
                var current = result.get();
                if (current.pending()) {
                    if (level.getGameTime() - startedAt > CAPTURE_TIMEOUT_TICKS) {
                        AcademyCraft.LOGGER.warn(
                                "Magnetic pull capture {} exceeded its tracking window; structure retained",
                                transactionId);
                        reject(player, "transaction_tracking_timeout");
                        close();
                        return false;
                    }
                    return true;
                }
                transactionId = null;
                if (!current.succeeded()) {
                    reject(player, current.status() + ":" + current.detail());
                    close();
                    return false;
                }
            }
            if (stage == Stage.CAPTURING) {
                if (sable(reference)) {
                    // The shared service has no calibrated Sable pull entry; the captured
                    // structure is retained instead of being mutated through a legacy entity.
                    AcademyCraft.LOGGER.info("Magnetic pull sable control is not implemented; retaining {}",
                            reference);
                    reject(player, "sable_pull_unavailable");
                    close();
                    return false;
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
            if (stage == Stage.AWAITING_PHYSICS) {
                return tickAwaitingPhysics(player, destination, fallback, maxSpeedPerTick, stopDistance, milestone);
            }
            return tickPulling(player, destination, fallback, maxSpeedPerTick, stopDistance, range);
        }

        private boolean openRegion(ServerLevel level, ServerPlayer player) {
            var handle = StructureSpaces.resolve(level.getServer(), reference).orElse(null);
            if (handle == null || handle.lifecycle() != StructureLifecycle.ACTIVE) {
                reject(player, "captured_handle_unavailable");
                return false;
            }
            var limits = new AABB(level.getWorldBorder().getMinX(), level.getMinY(), level.getWorldBorder().getMinZ(),
                    level.getWorldBorder().getMaxX(), level.getMaxY(), level.getWorldBorder().getMaxZ());
            var bounds = regionBounds(positions, level.getMinY(), level.getMaxY(), limits);
            if (bounds == null) {
                reject(player, "region_cannot_fit_structure");
                return false;
            }
            StructurePhysicsRegion opened;
            try {
                opened = StructurePhysics.openRegion(level, bounds);
            } catch (RuntimeException | AssertionError failure) {
                AcademyCraft.LOGGER.error("Magnetic pull could not open a physics region for {}", reference, failure);
                reject(player, "region_open_failed");
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
                reject(player, "region_attach_failed");
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
                reject(player, "mass_over_budget");
                close();
                return false;
            }
            var control = new StructurePhysicsControl.Force(motion.localCenterOfMass(),
                    MagneticStructureControl.pullForce(motion.linearVelocity(),
                            motion.pose().localToWorldPoint(motion.localCenterOfMass()), destination, fallback,
                            maxSpeedPerTick, stopDistance, motion.mass()));
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
                lease.setControl(new StructurePhysicsControl.Force(motion.localCenterOfMass(),
                        MagneticStructureControl.pullForce(motion.linearVelocity(), center, destination, fallback,
                                maxSpeedPerTick, stopDistance, motion.mass())));
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
        }
    }
}
