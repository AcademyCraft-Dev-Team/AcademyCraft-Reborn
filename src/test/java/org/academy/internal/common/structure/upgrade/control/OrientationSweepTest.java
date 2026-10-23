package org.academy.internal.common.structure.upgrade.control;

import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.academy.api.common.structure.StructurePose;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class OrientationSweepTest {
    private static final StructurePose ORIGIN = StructurePose.identity(Vec3.ZERO);
    private static final AABB ROD = new AABB(-3, -0.05, -0.05, 3, 0.05, 0.05);

    @Test
    void detectsWallCrossedBetweenClearEndpoints() {
        var obstacles = obstacles(new AABB(1.9, -0.2, 1.9, 2.1, 0.2, 2.1));
        // The X-aligned rod's endpoints at 0 and 180 degrees are both clear. Its arc is not.
        assertEquals(OrientationSweep.Status.BLOCKED, check(List.of(ROD), new Vec3(0, Math.PI / .05, 0),
                Vec3.ZERO, 10_000, obstacles).status());
    }

    @Test
    void doesNotFoldFullTurnIntoZeroRotation() {
        assertEquals(OrientationSweep.Status.BLOCKED, check(List.of(ROD), new Vec3(0, 2 * Math.PI / .05, 0),
                Vec3.ZERO, 10_000, obstacles(new AABB(-.1, -.2, 2.7, .1, .2, 2.9))).status());
    }

    @Test
    void preservesRealGapBetweenSeparateBoxes() {
        var shapes = List.of(new AABB(-3, -.5, -.5, -2, .5, .5), new AABB(2, -.5, -.5, 3, .5, .5));
        assertEquals(OrientationSweep.Status.CLEAR, check(shapes, new Vec3(0, .1, 0),
                Vec3.ZERO, 10_000, obstacles(new AABB(-.2, -.2, -.2, .2, .2, .2))).status());
    }

    @Test
    void includesCenterOfMassTranslation() {
        assertEquals(OrientationSweep.Status.BLOCKED, check(List.of(new AABB(-.1, -.1, -.1, .1, .1, .1)),
                Vec3.ZERO, new Vec3(4, 0, 0), 100,
                obstacles(new AABB(1.9, -.2, -.2, 2.1, .2, .2))).status());
    }

    @Test
    void unknownAndChangedTerrainCannotAuthorizeMovement() {
        assertEquals(OrientationSweep.Status.TERRAIN_PENDING, check(List.of(ROD), Vec3.ZERO, Vec3.ZERO, 100,
                (bounds, budget) -> new ExtractionPlanner.Query(7, ExtractionPlanner.Coverage.UNKNOWN, false, List.of())).status());
        assertEquals(OrientationSweep.Status.STALE, check(List.of(ROD), Vec3.ZERO, Vec3.ZERO, 100,
                (bounds, budget) -> new ExtractionPlanner.Query(8, ExtractionPlanner.Coverage.COMPLETE, false, List.of())).status());
    }

    @Test
    void bothArcLengthAndObstacleWorkAreBounded() {
        assertEquals(OrientationSweep.Status.BUDGET_EXHAUSTED, check(List.of(ROD), new Vec3(0, 100, 0),
                Vec3.ZERO, 2, obstacles()).status());
        assertEquals(OrientationSweep.Status.BUDGET_EXHAUSTED, check(List.of(ROD), Vec3.ZERO, Vec3.ZERO, 1,
                obstacles(new AABB(100, 100, 100, 101, 101, 101))).status());
    }

    @Test
    void offsetCenterUsesItsFullArcRadius() {
        var result = OrientationSweep.check(List.of(new AABB(4, -.1, -.1, 4.2, .1, .1)), ORIGIN,
                new Vec3(2, 0, 0), new Vec3(0, Math.PI / .05, 0), Vec3.ZERO, .05, 7, 10_000,
                obstacles(new AABB(1.9, -.2, -2.2, 2.1, .2, -2)));
        assertEquals(OrientationSweep.Status.BLOCKED, result.status());
    }

    private static OrientationSweep.Result check(List<AABB> boxes, Vec3 omega, Vec3 translation, int budget,
                                                  ExtractionPlanner.ObstacleSnapshot obstacles) {
        return OrientationSweep.check(boxes, ORIGIN, Vec3.ZERO, omega, translation, .05, 7, budget, obstacles);
    }

    private static ExtractionPlanner.ObstacleSnapshot obstacles(AABB... boxes) {
        var shapes = java.util.stream.IntStream.range(0, boxes.length).mapToObj(i ->
                new ExtractionPlanner.Obstacle("wall_" + i, ExtractionPlanner.ObstacleKind.HARD,
                        TranslationSweep.Box.axisAligned(boxes[i]))).toList();
        return (bounds, budget) -> new ExtractionPlanner.Query(7, ExtractionPlanner.Coverage.COMPLETE, false, shapes);
    }
}
