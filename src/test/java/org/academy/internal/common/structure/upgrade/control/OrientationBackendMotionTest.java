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

import static org.junit.jupiter.api.Assertions.*;

class OrientationBackendMotionTest {
    @Test
    void knownFreeStepCoversVelocityFirstAndContinuousGravityPaths() {
        var velocity = new Vec3(2, 0.3, -1);
        var gravity = new Vec3(0, -11, 0);
        var body = body(velocity, gravity);
        double dt = 0.025;
        var endpoint = body.freeStepDisplacement(dt);
        for (int index = 0; index <= 100; index++) {
            double fraction = index / 100.0;
            double time = dt * fraction;
            var continuous = velocity.scale(time).add(gravity.scale(0.5 * time * time));
            var velocityFirst = velocity.add(gravity.scale(dt)).scale(time);
            var center = endpoint.scale(fraction);
            assertTrue(continuous.distanceTo(center) <= body.freeStepMargin(dt) + 1.0e-12);
            assertTrue(velocityFirst.distanceTo(center) <= body.freeStepMargin(dt) + 1.0e-12);
        }
    }

    @Test
    void alreadyAppliedGravityCompensationIsNotAppliedTwice() {
        double dt = 0.05;
        var body = body(new Vec3(3, 9.81 * dt, 0), new Vec3(0, -9.81, 0));
        assertEquals(0, body.freeStepDisplacement(dt).y, 1.0e-12);
        assertEquals(3 * dt, body.freeStepDisplacement(dt).x, 1.0e-12);
        assertTrue(body.freeStepMargin(dt) > 0);
    }

    @Test
    void stationaryBodyStillSweepsItsNextGravityStep() {
        var body = body(Vec3.ZERO, new Vec3(0, -9.81, 0));
        var floor = new ExtractionPlanner.Obstacle("floor", ExtractionPlanner.ObstacleKind.HARD,
                TranslationSweep.Box.axisAligned(new AABB(-1, -1, -1, 2, -0.004, 2)));
        ExtractionPlanner.ObstacleSnapshot obstacles = (bounds, maximum) ->
                new ExtractionPlanner.Query(0, ExtractionPlanner.Coverage.COMPLETE, false, List.of(floor));
        var boxes = List.of(new AABB(0, 0, 0, 1, 1, 1));
        var source = StructurePose.identity(Vec3.ZERO);
        var center = new Vec3(0.5, 0.5, 0.5);
        var omega = new Vec3(0.01, 0, 0);
        assertEquals(OrientationSweep.Status.CLEAR, OrientationSweep.check(boxes, source, center, omega,
                Vec3.ZERO, 0.025, 0, 100, obstacles).status());
        assertEquals(OrientationSweep.Status.BLOCKED, OrientationSweep.check(boxes, source, center, omega,
                body.freeStepDisplacement(0.025), body.freeStepMargin(0.025), 0.025, 0, 100, obstacles).status());
    }

    @Test
    void invalidIntegrationIntervalsAndMarginsAreRejected() {
        var body = body(Vec3.ZERO, Vec3.ZERO);
        for (double dt : new double[]{0, -0.01, 0.051, Double.NaN}) {
            assertThrows(IllegalArgumentException.class, () -> body.freeStepDisplacement(dt));
            assertThrows(IllegalArgumentException.class, () -> body.freeStepMargin(dt));
        }
        assertThrows(IllegalArgumentException.class, () -> OrientationSweep.check(
                List.of(new AABB(0, 0, 0, 1, 1, 1)), StructurePose.identity(Vec3.ZERO), Vec3.ZERO,
                Vec3.ZERO, Vec3.ZERO, -1, 0.025, 0, 100,
                (bounds, maximum) -> new ExtractionPlanner.Query(0, ExtractionPlanner.Coverage.COMPLETE, false, List.of())));
    }

    @Test
    void oldEntityEnvelopeIncludesAnEntireOuterTickAndGravity() {
        var current = new AABB(10, 1, 0, 11, 2, 1);
        var envelope = OrientationWorldSnapshot.legacyEntityEnvelope(current, new Vec3(-10, 0, 0), true);
        assertTrue(envelope.intersects(new AABB(0, 0.97, 0, 1, 1.1, 1)));
        assertFalse(OrientationWorldSnapshot.legacyEntityEnvelope(current, Vec3.ZERO, false)
                .intersects(new AABB(0, 0.97, 0, 1, 1.1, 1)));
    }

    @Test
    void oldEntityBroadPhaseCannotAllocateUnboundedSectionArrays() {
        assertTrue(OrientationWorldSnapshot.boundedLegacyQuery(new AABB(-20, -20, -20, 20, 20, 20)));
        assertFalse(OrientationWorldSnapshot.boundedLegacyQuery(new AABB(-1e8, -1e8, -1e8, 1e8, 1e8, 1e8)));
    }

    private static OrientationBackendAccess.Body body(Vec3 velocity, Vec3 gravity) {
        return new OrientationBackendAccess.Body(new StructureRef(UUID.randomUUID(),
                Identifier.parse("minecraft:overworld"), Identifier.parse("academy:test"), 0), UUID.randomUUID(), 0, 0,
                new OrientationController.State(StructurePose.identity(Vec3.ZERO), Vec3.ZERO, Vec3.ZERO, 1),
                velocity, gravity, List.of(new AABB(0, 0, 0, 1, 1, 1)),
                new OrientationTargets.Baselines(Optional.empty(), Optional.empty()), true);
    }
}
