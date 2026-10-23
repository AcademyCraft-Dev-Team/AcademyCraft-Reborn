package org.academy.internal.common.structure.upgrade.control;

import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.academy.api.common.structure.StructurePose;
import org.academy.api.common.structure.upgrade.StructureExtraction;
import org.academy.api.common.structure.upgrade.StructureOperation;

import java.util.List;
import java.util.Objects;

/**
 * Maps the pure {@link ExtractionPlanner} onto the public operation vocabulary so capture
 * previews and later structural plans share one interpretation. Nothing here reads or writes a
 * world: the caller supplies a frozen geometry and a bounded obstacle snapshot, and receives a
 * status plus the verified path. A preview never claims that a body may be moved.
 */
public final class ExtractionPlanBuilder {
    /** Host shapes admitted per swept segment before the planner reports its budget as spent. */
    public static final int MAX_OBSTACLE_SHAPES = 8_192;

    private ExtractionPlanBuilder() {
    }

    /**
     * Source pose is the capture baseline, not a displaced intermediate pose. Local boxes are
     * real positive-volume collision cuboids of the selection; zero-collision decorative cells
     * remain an adapter/content concern.
     */
    public record Geometry(StructurePose sourcePose, StructurePose currentPose, Vec3 localCenterOfMass,
                           List<AABB> localBoxes, long contentRevision, long poseRevision) {
        public Geometry {
            Objects.requireNonNull(sourcePose, "sourcePose");
            Objects.requireNonNull(currentPose, "currentPose");
            Objects.requireNonNull(localCenterOfMass, "localCenterOfMass");
            Objects.requireNonNull(localBoxes, "localBoxes");
            localBoxes = List.copyOf(localBoxes);
            if (contentRevision < 0 || poseRevision < 0) throw new IllegalArgumentException("Negative revision");
        }
    }

    /** Path and blocked cells are observations; they never authorize a capture or a move. */
    public record Outcome(StructureOperation.Status status, String detail, List<Vec3> worldPath,
                          List<BlockPos> blockedCells, long terrainRevision) {
        public Outcome {
            Objects.requireNonNull(status, "status");
            Objects.requireNonNull(detail, "detail");
            worldPath = List.copyOf(worldPath);
            blockedCells = List.copyOf(blockedCells);
            if (detail.isBlank() || detail.length() > 256) throw new IllegalArgumentException("Invalid detail");
            if (terrainRevision < 0) throw new IllegalArgumentException("Negative terrain revision");
            if (status != StructureOperation.Status.READY && !worldPath.isEmpty()) {
                throw new IllegalArgumentException("Only a ready plan publishes a path");
            }
        }
    }

    public static Outcome plan(StructureExtraction.Limits limits, double liftHeight, int hoverTicks,
                               long terrainRevision, Geometry geometry, ExtractionPlanner.ObstacleSnapshot snapshot) {
        Objects.requireNonNull(limits, "limits");
        Objects.requireNonNull(geometry, "geometry");
        Objects.requireNonNull(snapshot, "snapshot");
        if (!Double.isFinite(liftHeight) || liftHeight <= 0.0
                || liftHeight > StructureExtraction.MAX_LIFT_HEIGHT) {
            throw new IllegalArgumentException("Invalid lift height");
        }
        if (hoverTicks < 0 || hoverTicks > StructureExtraction.MAX_DURATION_TICKS) {
            throw new IllegalArgumentException("Invalid hover ticks");
        }
        var result = ExtractionPlanner.plan(new ExtractionPlanner.Request(
                        new ExtractionPlanner.Geometry(geometry.sourcePose(), geometry.currentPose(),
                                geometry.localCenterOfMass(), geometry.localBoxes(),
                                geometry.contentRevision(), geometry.poseRevision()),
                        liftHeight, hoverTicks, terrainRevision, plannerLimits(limits, liftHeight)),
                snapshot);
        return switch (result.status()) {
            case CLEAR -> new Outcome(StructureOperation.Status.READY, "extraction_path_clear",
                    result.originPath(), List.of(), result.terrainRevision());
            case NEEDS_AUTHORIZATION -> new Outcome(StructureOperation.Status.BLOCKED,
                    "soft_content_requires_authorization:" + result.softObstacleIds().size(),
                    List.of(), List.of(), result.terrainRevision());
            case BLOCKED -> new Outcome(StructureOperation.Status.BLOCKED, "extraction_path_blocked",
                    List.of(), blockedCell(result.blockage().orElse(null)), result.terrainRevision());
            case TERRAIN_PENDING -> new Outcome(StructureOperation.Status.TERRAIN_PENDING,
                    "terrain_coverage_unknown", List.of(), List.of(), result.terrainRevision());
            case BUDGET_EXHAUSTED -> new Outcome(StructureOperation.Status.CONFLICT,
                    "planning_budget_exhausted", List.of(), List.of(), result.terrainRevision());
            case STALE -> new Outcome(StructureOperation.Status.STALE, "terrain_revision_changed",
                    List.of(), List.of(), result.terrainRevision());
        };
    }

    private static List<BlockPos> blockedCell(ExtractionPlanner.@org.jspecify.annotations.Nullable Blockage blockage) {
        if (blockage == null) return List.of();
        var point = blockage.pathOrigin();
        if (!Double.isFinite(point.x) || !Double.isFinite(point.y) || !Double.isFinite(point.z)
                || Math.abs(point.x) > 30_000_000.0 || Math.abs(point.z) > 30_000_000.0
                || point.y < -30_000_000.0 || point.y > 30_000_000.0) {
            return List.of();
        }
        return List.of(BlockPos.containing(point));
    }

    /**
     * Deterministic search bounds derived from the public limits. The planner's own caps are
     * tighter than the schema; the smaller value wins so a wide request cannot bypass them.
     */
    static ExtractionPlanner.Limits plannerLimits(StructureExtraction.Limits limits, double liftHeight) {
        double retreat = limits.maxRetreatDistance();
        double step = retreat > 0.0 ? Math.max(0.001, Math.min(0.5, retreat)) : 0.5;
        double path = Math.min(256.0, Math.max(1.0, liftHeight + 2.0 * retreat + 2.0));
        return new ExtractionPlanner.Limits(limits.maxShapeTests(),
                Math.min(MAX_OBSTACLE_SHAPES, ExtractionPlanner.MAX_OBSTACLE_SHAPES),
                32, 9, path, retreat, step);
    }
}
