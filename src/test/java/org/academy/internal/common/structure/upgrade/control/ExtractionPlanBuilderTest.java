package org.academy.internal.common.structure.upgrade.control;

import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.academy.api.common.structure.StructurePose;
import org.academy.api.common.structure.upgrade.StructureExtraction;
import org.academy.api.common.structure.upgrade.StructureOperation;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ExtractionPlanBuilderTest {
    private static final long TERRAIN_REVISION = 9;

    @Test
    void aClearPathPublishesTheVerifiedRouteAndKeepsTheRequestedHoverTime() {
        var outcome = ExtractionPlanBuilder.plan(limits(0.0), 3.0, 200, TERRAIN_REVISION, geometry(),
                snapshot(List.of(), TERRAIN_REVISION, ExtractionPlanner.Coverage.COMPLETE, false));

        assertEquals(StructureOperation.Status.READY, outcome.status());
        assertEquals("extraction_path_clear", outcome.detail());
        assertEquals(2, outcome.worldPath().size());
        assertEquals(3.0, outcome.worldPath().getLast().y, 1.0e-9);
        assertTrue(outcome.blockedCells().isEmpty());
        assertEquals(TERRAIN_REVISION, outcome.terrainRevision());
    }

    @Test
    void hardObstructionsPublishTheirCellWithoutAPartialPath() {
        var roof = obstacle("roof", ExtractionPlanner.ObstacleKind.HARD, new AABB(-4, 2, -4, 4, 3, 4));
        var outcome = ExtractionPlanBuilder.plan(limits(0.0), 3.0, 200, TERRAIN_REVISION, geometry(),
                snapshot(List.of(roof), TERRAIN_REVISION, ExtractionPlanner.Coverage.COMPLETE, false));

        assertEquals(StructureOperation.Status.BLOCKED, outcome.status());
        assertEquals("extraction_path_blocked", outcome.detail());
        assertTrue(outcome.worldPath().isEmpty());
        assertEquals(1, outcome.blockedCells().size());
    }

    @Test
    void softContentIsReportedAsRequiringAuthorizationInsteadOfAPassablePath() {
        var grass = obstacle("soft:1", ExtractionPlanner.ObstacleKind.SOFT_REQUIRES_PERMISSION,
                new AABB(-0.5, 1.5, -0.5, 1.5, 2.0, 1.5));
        var outcome = ExtractionPlanBuilder.plan(limits(0.0), 3.0, 200, TERRAIN_REVISION, geometry(),
                snapshot(List.of(grass), TERRAIN_REVISION, ExtractionPlanner.Coverage.COMPLETE, false));

        assertEquals(StructureOperation.Status.BLOCKED, outcome.status());
        assertTrue(outcome.detail().startsWith("soft_content_requires_authorization"),
                "Decorative content must stay distinguishable from a hard obstruction");
        assertTrue(outcome.worldPath().isEmpty());
    }

    @Test
    void unknownOrChangedTerrainNeverBecomesAnEmptyClearance() {
        var unknown = ExtractionPlanBuilder.plan(limits(0.0), 3.0, 200, TERRAIN_REVISION, geometry(),
                snapshot(List.of(), TERRAIN_REVISION, ExtractionPlanner.Coverage.UNKNOWN, false));
        assertEquals(StructureOperation.Status.TERRAIN_PENDING, unknown.status());
        assertTrue(unknown.worldPath().isEmpty());

        var stale = ExtractionPlanBuilder.plan(limits(0.0), 3.0, 200, TERRAIN_REVISION, geometry(),
                snapshot(List.of(), TERRAIN_REVISION + 1, ExtractionPlanner.Coverage.COMPLETE, false));
        assertEquals(StructureOperation.Status.STALE, stale.status());
        assertTrue(stale.worldPath().isEmpty());
    }

    @Test
    void aTruncatedSnapshotReportsABudgetConflictRatherThanSuccess() {
        var outcome = ExtractionPlanBuilder.plan(limits(0.0), 3.0, 200, TERRAIN_REVISION, geometry(),
                snapshot(List.of(), TERRAIN_REVISION, ExtractionPlanner.Coverage.COMPLETE, true));

        assertEquals(StructureOperation.Status.CONFLICT, outcome.status());
        assertEquals("planning_budget_exhausted", outcome.detail());
        assertTrue(outcome.worldPath().isEmpty());
    }

    @Test
    void plannerLimitsStayInsideTheSchemaBounds() {
        var tightened = ExtractionPlanBuilder.plannerLimits(limits(2.0), 64.0);
        assertTrue(tightened.maxPathLength() <= 256.0);
        assertEquals(2.0, tightened.maxRetreatDistance(), 1.0e-12);
        assertTrue(tightened.retreatStep() >= 0.001);
        assertThrows(IllegalArgumentException.class,
                () -> ExtractionPlanBuilder.plan(limits(0.0), 0.0, 200, TERRAIN_REVISION, geometry(),
                        snapshot(List.of(), TERRAIN_REVISION, ExtractionPlanner.Coverage.COMPLETE, false)));
    }

    private static ExtractionPlanBuilder.Geometry geometry() {
        return new ExtractionPlanBuilder.Geometry(StructurePose.identity(Vec3.ZERO),
                StructurePose.identity(Vec3.ZERO), new Vec3(0.5, 0.5, 0.5),
                List.of(new AABB(0, 0, 0, 1, 1, 1)), 1, 1);
    }

    private static StructureExtraction.Limits limits(double retreat) {
        return new StructureExtraction.Limits(50.0, 500.0, 5_000.0, retreat, 50_000);
    }

    private static ExtractionPlanner.Obstacle obstacle(String id, ExtractionPlanner.ObstacleKind kind, AABB box) {
        return new ExtractionPlanner.Obstacle(id, kind, TranslationSweep.Box.axisAligned(box));
    }

    private static ExtractionPlanner.ObstacleSnapshot snapshot(List<ExtractionPlanner.Obstacle> obstacles,
                                                               long revision, ExtractionPlanner.Coverage coverage,
                                                               boolean truncated) {
        return (bounds, maximum) -> new ExtractionPlanner.Query(revision, coverage, truncated,
                obstacles.stream().filter(obstacle -> bounds.intersects(obstacle.box().bounds())).toList());
    }
}
