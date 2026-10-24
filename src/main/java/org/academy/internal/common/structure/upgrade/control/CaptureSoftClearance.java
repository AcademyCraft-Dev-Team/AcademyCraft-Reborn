package org.academy.internal.common.structure.upgrade.control;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import org.academy.api.common.structure.StructureCaptureRequest;
import org.academy.api.common.structure.StructurePose;
import org.academy.api.common.structure.StructureTerrainBreakPolicy;
import org.academy.api.common.structure.upgrade.StructureExtraction;
import org.academy.api.common.structure.upgrade.StructureOperation;
import org.academy.internal.common.structure.StructureBackendRouter;
import org.academy.internal.common.sublevel.SubLevelTerrainBreakPolicy;
import org.academy.internal.common.sublevel.SubLevelTerrainBreaker;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;

/** Bounded explicit clearance shared by the three capture actions, before their read-only preview. */
public final class CaptureSoftClearance {
    // Solver contacts include tangent faces/edges. Clear only eligible soft materials within
    // this narrow band before preparing terrain; never change the body's collision geometry.
    private static final double SOFT_CONTACT_SKIN = 0.01;

    private CaptureSoftClearance() {}

    /** Read all admission/permission gates before spending a bounded soft-cell destruction budget. */
    public static StructureOperation.Result<StructureCaptureRequest> prepareSelection(
            ServerLevel host, Entity actor, StructureCaptureRequest capture) {
        if (!host.getServer().isSameThread()) throw new IllegalStateException("Clearance requires server thread");
        if (actor == null || actor.level() != host || !actor.isAlive() || actor.isRemoved()) {
            return rejected("capture_actor_unavailable");
        }
        if (!capture.physical() || host.getServer().getLevel(host.dimension()) != host
                || org.academy.internal.common.structure.space.NativeStructureDimensions.isStorageLevel(host.dimension())) {
            return rejected("capture_host_or_mode_unavailable");
        }
        var backend = StructureBackendRouter.captureTarget(host, capture.physical()).orElse(null);
        if (backend == null) return rejected("extraction_backend_unavailable");
        if (capture.positions().isEmpty() || capture.positions().size() > capture.maximumBlocks()
                || capture.positions().size() > backend.cellBudget()) return rejected("capture_selection_budget");
        var origin = LevelCaptureSource.origin(capture.positions());
        var retained = new ArrayList<BlockPos>();
        var soft = new ArrayList<StructureTerrainBreakPolicy.Candidate>();
        boolean solid = false;
        for (var position : capture.positions()) {
            if (position.getX() - origin.getX() >= capture.maximumSpan()
                    || position.getY() - origin.getY() >= capture.maximumSpan()
                    || position.getZ() - origin.getZ() >= capture.maximumSpan()) return rejected("selection_span_exceeded");
            if (!host.isInWorldBounds(position) || !host.getWorldBorder().isWithinBounds(position)
                    || host.getChunkSource().getChunkNow(position.getX() >> 4, position.getZ() >> 4) == null) {
                return rejected("capture_source_unloaded_or_out_of_world");
            }
            if (!actor.mayInteract(host, position) || !host.mayInteract(actor, position)
                    || !capture.permission().test(host, position)) return rejected("capture_source_permission_denied");
            var state = host.getBlockState(position);
            if (state.isAir()) continue;
            String rejection = backend.rejectionDetail(host, position, state, host.getBlockEntity(position));
            boolean clearable = (rejection == null || canClearRejected(rejection))
                    && !state.isCollisionShapeFullBlock(host, position)
                    && SubLevelTerrainBreaker.canClearObstacle(host, actor, position);
            if (clearable) {
                soft.add(new StructureTerrainBreakPolicy.Candidate(position, state));
            } else if (rejection != null) {
                return rejected(rejection);
            } else {
                retained.add(position);
                solid |= !state.getCollisionShape(host, position).isEmpty() && backend.canSeed(host, position, state);
            }
        }
        if (!org.academy.internal.common.structure.transaction.StructureCellPayload.completePairs(host, retained)) return rejected("incomplete_double_chest");
        if (!solid) return rejected("capture_no_seedable_content");
        if (!soft.isEmpty()) {
            SubLevelTerrainBreakPolicy.applyCandidates(host, actor, soft,
                    !(actor instanceof Player player) || !player.hasInfiniteMaterials(),
                    new StructureTerrainBreakPolicy.Limits(256, 4096, 256), false);
            // A neighbour update may already have broken a tall plant's other half. Count it
            // as cleared only when the actual world is air, never when a listener substituted it.
            if (soft.stream().anyMatch(cell -> !host.getBlockState(cell.position()).isAir())) {
                return rejected("capture_soft_clearance_denied_or_changed");
            }
        }
        var prepared = retained.equals(capture.positions()) ? capture : new StructureCaptureRequest(retained,
                capture.maximumBlocks(), capture.maximumSpan(), capture.permission(), capture.physical(), capture.skillCreation());
        return new StructureOperation.Result<>(StructureOperation.Status.READY, "capture_selection_prepared",
                Optional.empty(), Optional.of(prepared));
    }

    static boolean canClearRejected(String rejection) {
        return "not_in_plain_block_whitelist".equals(rejection) || "not_full_block".equals(rejection)
                || "scheduled_tick_pending".equals(rejection);
    }

    private static StructureOperation.Result<StructureCaptureRequest> rejected(String detail) {
        return new StructureOperation.Result<>(StructureOperation.Status.DENIED, detail, Optional.empty(), Optional.empty());
    }

    /** Explicit isolated vegetation smoke only; normal clients never produce per-step traces. */
    static boolean traceEnabled() {
        return Boolean.getBoolean("academy.sublevelLiftTiersSmoke") && Boolean.getBoolean("academy.sublevelLiftVegetation");
    }

    /** Newly placed vegetation must be destroyed through the same event path before the next step. */
    public static int clearMotion(ServerLevel host, Entity actor, OrientationBackendAccess.Body body,
                                  StructurePose nextPose) {
        if (!host.getServer().isSameThread()) throw new IllegalStateException("Clearance requires server thread");
        if (actor == null || actor.level() != host || !actor.isAlive() || actor.isRemoved()) return 0;
        var policy = new SubLevelTerrainBreakPolicy();
        var limits = new StructureTerrainBreakPolicy.Limits(4096, 65_536, 48);
        var sweep = policy.scan(host, body.reference(), body.motion().pose(), nextPose,
                softContactBoxes(body.localBoxes()), limits);
        if (traceEnabled()) org.academy.AcademyCraft.LOGGER.info(
                "EXTRACTION_SOFT_SCAN tick={} gameTick={} body={} previous={} next={} exhausted={} candidates={}",
                host.getServer().getTickCount(), host.getGameTime(), body.reference(), body.motion().pose(), nextPose,
                sweep.budgetExhausted(), sweep.candidates());
        var plan = policy.authorize(host, actor, sweep,
                !(actor instanceof Player player) || !player.hasInfiniteMaterials(), limits);
        var result = policy.apply(host, actor, plan, limits);
        if (traceEnabled()) org.academy.AcademyCraft.LOGGER.info(
                "EXTRACTION_SOFT_APPLY tick={} gameTick={} body={} permitted={} destroyed={} rejected={} exhausted={}",
                host.getServer().getTickCount(), host.getGameTime(), body.reference(), plan.permitted(),
                result.destroyed(), result.rejected(), result.budgetExhausted());
        return result.destroyed().size();
    }

    public static int clear(ServerLevel host, Entity actor, StructureCaptureRequest capture, double height) {
        if (!host.getServer().isSameThread()) throw new IllegalStateException("Clearance requires server thread");
        if (actor == null || actor.level() != host || !actor.isAlive() || actor.isRemoved()
                || !Double.isFinite(height) || height <= 0 || height > StructureExtraction.MAX_LIFT_HEIGHT) return 0;
        var excluded = new HashSet<>(capture.positions());
        LevelCaptureSource.Snapshot source;
        try { source = LevelCaptureSource.read(host, capture).orElse(null); }
        catch (LevelCaptureSource.Rejected rejected) { return 0; }
        if (source == null || source.localBoxes().isEmpty()) return 0;
        var boxes = softContactBoxes(source.localBoxes()).stream()
                .map(box -> box.move(source.source().origin()).expandTowards(0, height, 0)).toList();
        var bounds = boxes.stream().reduce(AABB::minmax).orElseThrow();
        long volume = (long) Math.ceil(bounds.getXsize() + 1) * (long) Math.ceil(bounds.getYsize() + 1)
                * (long) Math.ceil(bounds.getZsize() + 1);
        if (volume > 4096) return 0;
        var candidates = new ArrayList<StructureTerrainBreakPolicy.Candidate>();
        var view = new LoadedChunkView(host);
        int tests = 0;
        for (var position : BlockPos.betweenClosed(BlockPos.containing(bounds.minX, bounds.minY, bounds.minZ),
                BlockPos.containing(Math.nextDown(bounds.maxX), Math.nextDown(bounds.maxY), Math.nextDown(bounds.maxZ)))) {
            if (excluded.contains(position)) continue;
            if (host.getChunkSource().getChunkNow(position.getX() >> 4, position.getZ() >> 4) == null) return 0;
            if (!SubLevelTerrainBreaker.canClearObstacle(host, actor, position)) continue;
            if (!actor.mayInteract(host, position) || !host.mayInteract(actor, position)) continue;
            try {
                var state = view.getBlockState(position);
                var shapes = LoadedChunkView.inspectShape(() -> state.getShape(view, position).toAabbs());
                boolean hit = false;
                for (var shape : shapes) {
                    if ((tests += boxes.size()) > 65_536) return 0;
                    if (intersects(boxes, shape.move(position))) { hit = true; break; }
                }
                if (hit) candidates.add(new StructureTerrainBreakPolicy.Candidate(position.immutable(), state));
            } catch (LoadedChunkView.Unavailable | LoadedChunkView.ShapeFailure unavailable) { return 0; }
        }
        if (!view.current()) return 0;
        return SubLevelTerrainBreakPolicy.applyCandidates(host, actor, candidates,
                !(actor instanceof Player player) || !player.hasInfiniteMaterials(),
                new StructureTerrainBreakPolicy.Limits(4096, 65_536, 48), false).destroyed().size();
    }

    static boolean intersects(List<AABB> boxes, AABB cell) {
        return boxes.stream().anyMatch(box -> box.intersects(cell));
    }

    static List<AABB> softContactBoxes(List<AABB> collisionBoxes) {
        return collisionBoxes.stream().map(box -> box.inflate(SOFT_CONTACT_SKIN)).toList();
    }
}
