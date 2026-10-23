package org.academy.internal.common.structure.upgrade.control;

import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.academy.api.common.structure.StructurePose;
import org.joml.Quaterniond;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ExtractionPlannerTest {
    private static final AABB UNIT = new AABB(0, 0, 0, 1, 1, 1);
    private static final long TERRAIN_REVISION = 9;

    @Test
    void threeFiveSevenUseOriginalComHeightAndDoNotConsumeHoverTime() {
        var source = StructurePose.identity(new Vec3(-7, 10, 4));
        var current = new StructurePose(new Vec3(-7, 9.7, 4), new Quaterniond().rotationZ(0.2));
        var com = new Vec3(0.8, 0.3, 0.4);
        var geometry = new ExtractionPlanner.Geometry(source, current, com, List.of(UNIT), 5, 6);
        for (double height : new double[]{3, 5, 7}) {
            var result = ExtractionPlanner.plan(new ExtractionPlanner.Request(geometry, height, 200,
                    TERRAIN_REVISION, limits(0.0)), snapshot(List.of()));

            assertEquals(ExtractionPlanner.Status.CLEAR, result.status());
            var endpoint = result.originPath().getLast();
            assertEquals(source.localToWorldPoint(com).y + height,
                    endpoint.y + current.localToWorldDirection(com).y, 1.0e-12);
            assertEquals(current.worldOrigin().x, endpoint.x);
            assertEquals(current.worldOrigin().z, endpoint.z);
            assertEquals(200, result.hoverTicks());
            assertEquals(5, result.contentRevision());
            assertEquals(6, result.poseRevision());
            assertEquals(TERRAIN_REVISION, result.terrainRevision());
        }
    }

    @Test
    void realGeometryPreservesHolesInsteadOfUsingOneOverallAabb() {
        var geometry = geometry(List.of(new AABB(0, 0, 0, 1, 1, 1), new AABB(2, 0, 0, 3, 1, 1)));
        var throughHole = hard("pillar", new AABB(1.2, -1, 0.2, 1.8, 5, 0.8));
        var result = ExtractionPlanner.plan(request(geometry, limits(0.0)), snapshot(List.of(throughHole)));

        assertEquals(ExtractionPlanner.Status.CLEAR, result.status());
        assertEquals(3.0, result.originPath().getLast().y);
    }

    @Test
    void unevenSocketRetreatIsCheckedAtRaisedHeightAndPreservesSourceGoal() {
        var geometry = geometry(List.of(UNIT, new AABB(1, 2, 0, 2, 3, 1)));
        var terrain = List.of(hard("floor", new AABB(-10, -1, -10, 10, 0, 10)),
                hard("step", new AABB(1, 0, 0, 2, 2, 1)),
                hard("overhang", new AABB(0.75, 1.5, 0, 1, 1.6, 1)));
        var result = ExtractionPlanner.plan(request(geometry, limits(0.5)), snapshot(terrain));

        assertEquals(ExtractionPlanner.Status.CLEAR, result.status());
        assertEquals(4, result.originPath().size());
        assertEquals(0.5 - 1.0e-6, result.originPath().get(1).y, 1.0e-12);
        assertEquals(-0.5, result.originPath().get(2).x);
        assertEquals(new Vec3(-0.5, 3, 0), result.originPath().getLast());
        assertEquals(200, result.hoverTicks());
        assertTrue(result.usage().candidates() > 2, "The unsafe +X retreat must fail before -X succeeds");
        assertPathClear(geometry, result.originPath(), terrain);
    }

    @Test
    void fullRoofAndInitialPenetrationNeverProduceAnExecutablePartialPath() {
        var roof = hard("roof", new AABB(-4, 1.5, -4, 4, 2, 4));
        var blocked = ExtractionPlanner.plan(request(geometry(List.of(UNIT)), limits(0.5)), snapshot(List.of(roof)));
        assertEquals(ExtractionPlanner.Status.BLOCKED, blocked.status());
        assertTrue(blocked.originPath().isEmpty());
        assertEquals("roof", blocked.blockage().orElseThrow().obstacleId());

        var overlap = hard("embedded", new AABB(0.5, 0.5, 0.5, 1.5, 1.5, 1.5));
        var embedded = ExtractionPlanner.plan(request(geometry(List.of(UNIT)), limits(0.5)), snapshot(List.of(overlap)));
        assertEquals(ExtractionPlanner.Status.BLOCKED, embedded.status());
        assertTrue(embedded.blockage().orElseThrow().initialOverlap());
        assertTrue(embedded.originPath().isEmpty());
        assertEquals(1, embedded.usage().candidates());
    }

    @Test
    void softGeometryRequiresExplicitAuthorizationAndIsDeduplicatedWithoutAnyMutation() {
        var first = soft("plant", new AABB(0.2, 1.1, 0.2, 0.8, 1.2, 0.8));
        var second = soft("plant", new AABB(0.2, 1.3, 0.2, 0.8, 1.4, 0.8));
        var source = new ArrayList<>(List.of(first, second));
        var result = ExtractionPlanner.plan(request(geometry(List.of(UNIT)), limits(0.0)), snapshot(source));

        assertEquals(ExtractionPlanner.Status.NEEDS_AUTHORIZATION, result.status());
        assertEquals(List.of("plant"), result.softObstacleIds());
        assertEquals(List.of(first, second), source);
        assertFalse(result.originPath().isEmpty());
        assertThrows(UnsupportedOperationException.class, () -> result.softObstacleIds().clear());
    }

    @Test
    void missingCoverageAndChangedRevisionCannotBeInterpretedAsEmptySpace() {
        var request = request(geometry(List.of(UNIT)), limits(0.0));
        var unknown = ExtractionPlanner.plan(request, (bounds, maximum) -> new ExtractionPlanner.Query(
                TERRAIN_REVISION, ExtractionPlanner.Coverage.UNKNOWN, false, List.of()));
        assertEquals(ExtractionPlanner.Status.TERRAIN_PENDING, unknown.status());
        assertTrue(unknown.originPath().isEmpty());
        var stale = ExtractionPlanner.plan(request, (bounds, maximum) -> new ExtractionPlanner.Query(
                TERRAIN_REVISION + 1, ExtractionPlanner.Coverage.COMPLETE, false, List.of()));
        assertEquals(ExtractionPlanner.Status.STALE, stale.status());
        assertTrue(stale.originPath().isEmpty());
    }

    @Test
    void truncationPairBudgetAndLengthBudgetAllFailClosed() {
        var geometry = geometry(List.of(UNIT));
        var truncated = ExtractionPlanner.plan(request(geometry, limits(0.0)), (bounds, maximum) ->
                new ExtractionPlanner.Query(TERRAIN_REVISION, ExtractionPlanner.Coverage.COMPLETE, true, List.of()));
        assertEquals(ExtractionPlanner.Status.BUDGET_EXHAUSTED, truncated.status());

        var small = new ExtractionPlanner.Limits(1, 8, 8, 9, 10, 0, 0.5);
        var candidates = List.of(soft("one", new AABB(0, 1.5, 0, 1, 1.6, 1)),
                soft("two", new AABB(0, 2.0, 0, 1, 2.1, 1)));
        var pairs = ExtractionPlanner.plan(request(geometry, small), snapshot(candidates));
        assertEquals(ExtractionPlanner.Status.BUDGET_EXHAUSTED, pairs.status());
        assertEquals(1, pairs.usage().shapeTests());
        assertTrue(pairs.originPath().isEmpty());

        var queryCount = new AtomicInteger();
        var shortPath = new ExtractionPlanner.Limits(10, 8, 8, 9, 2, 0, 0.5);
        var length = ExtractionPlanner.plan(request(geometry, shortPath), (bounds, maximum) -> {
            queryCount.incrementAndGet();
            return new ExtractionPlanner.Query(TERRAIN_REVISION, ExtractionPlanner.Coverage.COMPLETE, false, List.of());
        });
        assertEquals(ExtractionPlanner.Status.BUDGET_EXHAUSTED, length.status());
        assertEquals(0, queryCount.get());
    }

    @Test
    void retreatSearchHasBoundedCandidatesAndSnapshotQueries() {
        var roof = List.of(hard("roof", new AABB(-4, 1.5, -4, 4, 2, 4)));
        var candidates = new ExtractionPlanner.Limits(1_000, 100, 100, 1, 10, 0.5, 0.5);
        var result = ExtractionPlanner.plan(request(geometry(List.of(UNIT)), candidates), snapshot(roof));
        assertEquals(ExtractionPlanner.Status.BUDGET_EXHAUSTED, result.status());
        assertEquals(1, result.usage().candidates());

        var queries = new ExtractionPlanner.Limits(1_000, 100, 1, 9, 10, 0.5, 0.5);
        var noQueries = ExtractionPlanner.plan(request(geometry(List.of(UNIT)), queries), snapshot(roof));
        assertEquals(ExtractionPlanner.Status.BUDGET_EXHAUSTED, noQueries.status());
        assertEquals(1, noQueries.usage().queries());
    }

    @Test
    void replanningCannotAccumulateRetreatBeyondTheOriginalSourceAnchor() {
        var geometry = new ExtractionPlanner.Geometry(StructurePose.identity(Vec3.ZERO),
                StructurePose.identity(new Vec3(0.4, 0, 0)), new Vec3(0.5, 0.5, 0.5), List.of(UNIT), 5, 6);
        var roof = hard("roof", new AABB(-4, 1.5, -4, 0.6, 2, 4));
        var result = ExtractionPlanner.plan(request(geometry, limits(0.5)), snapshot(List.of(roof)));

        assertEquals(ExtractionPlanner.Status.BLOCKED, result.status());
        assertTrue(result.originPath().isEmpty(), "Another +0.5 move would exceed the original 0.5 allowance");
    }

    @Test
    void snapshotGeometryAndQueryListsCannotChangeAfterConstruction() {
        var boxes = new ArrayList<>(List.of(UNIT));
        var geometry = geometry(boxes);
        boxes.clear();
        assertEquals(List.of(UNIT), geometry.localBoxes());
        assertThrows(UnsupportedOperationException.class, () -> geometry.localBoxes().clear());
        var obstacles = new ArrayList<>(List.of(hard("one", UNIT)));
        var query = new ExtractionPlanner.Query(TERRAIN_REVISION, ExtractionPlanner.Coverage.COMPLETE, false, obstacles);
        obstacles.clear();
        assertEquals(1, query.obstacles().size());
        assertThrows(IllegalArgumentException.class, () -> geometry(List.of()));
        assertThrows(IllegalArgumentException.class, () -> new ExtractionPlanner.Limits(1, 1, 1, 1, 10, 2.01, 0.5));
    }

    private static ExtractionPlanner.Geometry geometry(List<AABB> boxes) {
        return new ExtractionPlanner.Geometry(StructurePose.identity(Vec3.ZERO), StructurePose.identity(Vec3.ZERO),
                new Vec3(0.5, 0.5, 0.5), boxes, 5, 6);
    }

    private static ExtractionPlanner.Limits limits(double retreat) {
        return new ExtractionPlanner.Limits(10_000, 1_000, 100, 9, 10, retreat, 0.5);
    }

    private static ExtractionPlanner.Request request(ExtractionPlanner.Geometry geometry, ExtractionPlanner.Limits limits) {
        return new ExtractionPlanner.Request(geometry, 3, 200, TERRAIN_REVISION, limits);
    }

    private static ExtractionPlanner.Obstacle hard(String id, AABB box) {
        return new ExtractionPlanner.Obstacle(id, ExtractionPlanner.ObstacleKind.HARD, TranslationSweep.Box.axisAligned(box));
    }

    private static ExtractionPlanner.Obstacle soft(String id, AABB box) {
        return new ExtractionPlanner.Obstacle(id, ExtractionPlanner.ObstacleKind.SOFT_REQUIRES_PERMISSION,
                TranslationSweep.Box.axisAligned(box));
    }

    private static ExtractionPlanner.ObstacleSnapshot snapshot(List<ExtractionPlanner.Obstacle> obstacles) {
        var immutable = List.copyOf(obstacles);
        return (bounds, maximum) -> {
            var matches = immutable.stream().filter(obstacle -> bounds.intersects(obstacle.box().bounds())).toList();
            return new ExtractionPlanner.Query(TERRAIN_REVISION, ExtractionPlanner.Coverage.COMPLETE,
                    matches.size() > maximum, matches.stream().limit(maximum).toList());
        };
    }

    private static void assertPathClear(ExtractionPlanner.Geometry geometry, List<Vec3> path,
                                        List<ExtractionPlanner.Obstacle> obstacles) {
        for (int point = 1; point < path.size(); point++) {
            var origin = path.get(point - 1);
            var pose = new StructurePose(origin, geometry.currentPose().rotation());
            for (var box : geometry.localBoxes()) {
                for (var obstacle : obstacles) {
                    assertTrue(TranslationSweep.firstHit(TranslationSweep.Box.of(box, pose),
                            path.get(point).subtract(origin), obstacle.box()).isEmpty());
                }
            }
        }
    }
}
