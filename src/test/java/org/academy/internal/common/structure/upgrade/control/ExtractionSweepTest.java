package org.academy.internal.common.structure.upgrade.control;

import net.minecraft.resources.Identifier;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.academy.api.common.structure.StructurePose;
import org.academy.api.common.structure.StructureRef;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ExtractionSweepTest {
    @Test void upwardTranslationLeavesFloorAndDoesNotInventSidewaysSocketPenetration() {
        var floor = obstacle(new AABB(-1, -1, -1, 2, 0, 2));
        var wall = obstacle(new AABB(1, 0, -1, 2, 3, 2));
        assertEquals(OrientationSweep.Status.CLEAR, LiveExtractionMotion.sweep(body(), new Vec3(0, .1, 0),
                .05, 1, 100, obstacles(List.of(floor, wall))));
    }

    @Test void gravityEnvelopeIncludesCurvedPathAboveVelocityFirstEndpoint() {
        var ceiling = obstacle(new AABB(-1, 1.005, -1, 2, 1.006, 2));
        assertEquals(OrientationSweep.Status.BLOCKED, LiveExtractionMotion.sweep(body(), new Vec3(.1, 0, 0),
                .05, 1, 100, obstacles(List.of(ceiling))));
    }

    @Test void unloadedAndBudgetLimitedCoverageNeverBecomesClear() {
        assertEquals(OrientationSweep.Status.TERRAIN_PENDING, LiveExtractionMotion.sweep(body(), new Vec3(0, .1, 0),
                .05, 1, 100, (bounds, budget) -> new ExtractionPlanner.Query(1,
                        ExtractionPlanner.Coverage.UNKNOWN, false, List.of())));
        assertEquals(OrientationSweep.Status.BUDGET_EXHAUSTED, LiveExtractionMotion.sweep(body(), new Vec3(0, .1, 0),
                .05, 1, 100, (bounds, budget) -> new ExtractionPlanner.Query(1,
                        ExtractionPlanner.Coverage.COMPLETE, true, List.of())));
    }

    private static ExtractionPlanner.Obstacle obstacle(AABB bounds) {
        return new ExtractionPlanner.Obstacle("host", ExtractionPlanner.ObstacleKind.HARD, TranslationSweep.Box.axisAligned(bounds));
    }

    private static ExtractionPlanner.ObstacleSnapshot obstacles(List<ExtractionPlanner.Obstacle> obstacles) {
        return (bounds, budget) -> new ExtractionPlanner.Query(1, ExtractionPlanner.Coverage.COMPLETE, false,
                obstacles.stream().filter(obstacle -> obstacle.box().bounds().intersects(bounds)).toList());
    }

    private static OrientationBackendAccess.Body body() {
        var pose = StructurePose.identity(Vec3.ZERO);
        return new OrientationBackendAccess.Body(new StructureRef(UUID.randomUUID(), Identifier.parse("minecraft:overworld"),
                Identifier.parse("academy:test"), 0), UUID.randomUUID(), 0, 0,
                new OrientationController.State(pose, new Vec3(.5, .5, .5), Vec3.ZERO, 1),
                Vec3.ZERO, new Vec3(0, -9.81, 0), List.of(new AABB(0, 0, 0, 1, 1, 1)),
                new OrientationTargets.Baselines(Optional.empty(), Optional.empty()), true);
    }
}
