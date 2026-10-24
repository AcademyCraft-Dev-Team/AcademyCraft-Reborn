package org.academy.internal.common.structure.upgrade.control;

import net.minecraft.resources.Identifier;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.academy.api.common.structure.StructurePose;
import org.academy.api.common.structure.StructureRef;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;
import java.util.Random;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class ExtractionContactSweepTest {
    @Test void existingSideOverlapMayDecreaseButCannotSlideDeeperOrRemainWedged() {
        var body = body(StructurePose.identity(new Vec3(.01, 0, 0)), Vec3.ZERO);
        var wall = new AABB(1, -1, -1, 2, 2, 2);
        assertEquals(OrientationSweep.Status.CLEAR, check(body, new Vec3(-.02, .02, 0), wall).status());
        assertEquals(OrientationSweep.Status.BLOCKED, check(body, new Vec3(.02, .02, 0), wall).status());
        assertEquals(OrientationSweep.Status.BLOCKED, check(body, new Vec3(0, .02, 0), wall).status());
    }

    @Test void tightSocketRotationAwayIsAdmittedWithoutShrinkingTheBox() {
        var pose = StructurePose.fromYawAroundPivot(Vec3.ZERO, 1, new Vec3(.5, .5, .5));
        // fromYawAroundPivot has negative world Y rotation; positive omega returns toward identity.
        var body = body(pose, new Vec3(0, .1, 0));
        var wall = new AABB(1, -1, -1, 2, 2, 2);
        assertEquals(OrientationSweep.Status.CLEAR, check(body, new Vec3(0, .02, 0), wall).status());
        assertEquals(OrientationSweep.Status.BLOCKED, check(body(pose, new Vec3(0, -.1, 0)), new Vec3(0, .02, 0), wall).status());
    }

    @Test void newThinInterveningObstacleCannotBeSkippedByEscapeException() {
        assertEquals(OrientationSweep.Status.BLOCKED, check(body(StructurePose.identity(Vec3.ZERO), Vec3.ZERO),
                new Vec3(2, 0, 0), new AABB(1.2, -1, -1, 1.201, 2, 2)).status());
    }

    @Test void movingBodyEnvelopeOverlapCannotBeReclassifiedAsExistingHostContact() {
        var body = body(StructurePose.identity(new Vec3(.01, 0, 0)), Vec3.ZERO);
        var result = ExtractionContactSweep.check(body, new Vec3(-.02, .02, 0), .05, 0, 100,
                (bounds, budget) -> new ExtractionPlanner.Query(0, ExtractionPlanner.Coverage.COMPLETE, false,
                        List.of(new ExtractionPlanner.Obstacle("other-body", ExtractionPlanner.ObstacleKind.HARD,
                                TranslationSweep.Box.axisAligned(new AABB(1, -1, -1, 2, 2, 2))))));
        assertEquals(OrientationSweep.Status.BLOCKED, result.status());
    }

    @Test void sableComRoundingIsContactButActualSidePenetrationStillRequiresEscape() {
        var wall = new AABB(1, -1, -1, 2, 2, 2);
        var rounding = body(StructurePose.identity(new Vec3(7.450580596923828E-9, 0, 0)), Vec3.ZERO);
        assertEquals(OrientationSweep.Status.CLEAR, check(rounding, new Vec3(0, .02, 0), wall).status());
        var penetration = body(StructurePose.identity(new Vec3(1E-5, 0, 0)), Vec3.ZERO);
        assertEquals(OrientationSweep.Status.BLOCKED, check(penetration, new Vec3(0, .02, 0), wall).status());
        assertEquals(OrientationSweep.Status.BLOCKED, check(rounding, new Vec3(1E-5, .02, 0), wall).status());
    }

    @Test void floatComObservationMayPassTheSteppedHostSideWithoutIncreasingItsOffset() {
        var body = steppedSableBody();
        var delta = new Vec3(4.214750807054993E-7, .17942967653264275, -1.064518559197443E-7);
        var wall = new AABB(-152, 125, 58, -151, 126, 59);
        assertEquals(OrientationSweep.Status.BLOCKED, check(body, delta, wall).status(),
                "A backend without the float-COM contract gets no wider contact band");
        assertEquals(OrientationSweep.Status.CLEAR, quantizedCheck(body, delta, wall, "host:step").status());
    }

    @Test void floatComBandCannotPayForNewMotionIntoTheSteppedWall() {
        var delta = new Vec3(4.214750807054993E-7, .17942967653264275, 1E-7);
        assertEquals(OrientationSweep.Status.BLOCKED, quantizedCheck(steppedSableBody(), delta,
                new AABB(-152, 125, 58, -151, 126, 59), "host:step").status());
    }

    @Test void floatComBandCannotPayForNewPenetrationAfterStartingWithClearance() {
        var body = body(StructurePose.identity(new Vec3(0, 0, -1E-5)), Vec3.ZERO);
        // Start 1E-5 clear, end 1E-6 inside: the endpoint fits the band, but this is new contact.
        assertEquals(OrientationSweep.Status.BLOCKED, quantizedCheck(body, new Vec3(0, .02, 1.1E-5),
                new AABB(-1, -1, 1, 2, 2, 2), "host:wall").status());
    }

    @Test void floatComBandDoesNotRelaxRealCeilingsOrMovingBodyEnvelopes() {
        var delta = new Vec3(4.214750807054993E-7, .17942967653264275, -1.064518559197443E-7);
        assertEquals(OrientationSweep.Status.BLOCKED, quantizedCheck(steppedSableBody(), delta,
                new AABB(-153, 125, 57, -150, 126, 58), "host:roof").status());
        assertEquals(OrientationSweep.Status.BLOCKED, quantizedCheck(steppedSableBody(), delta,
                new AABB(-152, 125, 58, -151, 126, 59), "other-body").status());
    }

    @Test void analyticalArcMinimumBoundsDenseSamplesForManyAxesAndSpeeds() {
        var random = new Random(19);
        for (int trial = 0; trial < 500; trial++) {
            double constant = random.nextDouble() * 10, rate = random.nextDouble() * 40 - 20;
            double cosine = random.nextDouble() * 10 - 5, sine = random.nextDouble() * 10 - 5;
            double speed = random.nextDouble() * 50, duration = .05;
            double minimum = ExtractionContactSweep.minimumCurve(constant, rate, cosine, sine, speed, duration);
            double sampled = Double.POSITIVE_INFINITY;
            for (int step = 0; step <= 1000; step++) {
                double time = duration * step / 1000;
                sampled = Math.min(sampled, constant + rate * time + cosine * Math.cos(speed * time) + sine * Math.sin(speed * time));
            }
            assertTrue(minimum <= sampled + 1E-10);
            assertEquals(sampled, minimum, 1E-5);
        }
    }

    private static ExtractionContactSweep.Result check(OrientationBackendAccess.Body body, Vec3 delta, AABB obstacle) {
        return ExtractionContactSweep.check(body, delta, .05, 0, 100, (bounds, budget) -> new ExtractionPlanner.Query(0,
                ExtractionPlanner.Coverage.COMPLETE, false, List.of(new ExtractionPlanner.Obstacle("host:wall",
                ExtractionPlanner.ObstacleKind.HARD, TranslationSweep.Box.axisAligned(obstacle)))));
    }

    private static ExtractionContactSweep.Result quantizedCheck(OrientationBackendAccess.Body body, Vec3 delta,
                                                                 AABB obstacle, String id) {
        var rounding = new Vec3(Math.ulp(-154.54167F) * .5, Math.ulp(124.20475F) * .5, Math.ulp(57.479168F) * .5);
        return ExtractionContactSweep.check(body, delta, .025, 0, 100, (bounds, budget) -> new ExtractionPlanner.Query(0,
                ExtractionPlanner.Coverage.COMPLETE, false, List.of(new ExtractionPlanner.Obstacle(id,
                ExtractionPlanner.ObstacleKind.HARD, TranslationSweep.Box.axisAligned(obstacle)))), rounding);
    }

    private static OrientationBackendAccess.Body steppedSableBody() {
        return body(StructurePose.identity(new Vec3(-160.0000050738454, 122.83997090657564, 53.00000128149986)),
                Vec3.ZERO, new Vec3(5.458333320915699, 2.2047526041665435, 4.479166656732559),
                List.of(new AABB(8, 1, 4, 9, 2, 5)));
    }

    private static OrientationBackendAccess.Body body(StructurePose pose, Vec3 angular) {
        return body(pose, angular, new Vec3(.5, .5, .5), List.of(new AABB(0, 0, 0, 1, 1, 1)));
    }

    private static OrientationBackendAccess.Body body(StructurePose pose, Vec3 angular, Vec3 localCenter, List<AABB> boxes) {
        return new OrientationBackendAccess.Body(new StructureRef(UUID.randomUUID(), Identifier.parse("minecraft:overworld"),
                Identifier.parse("academy:test"), 0), UUID.randomUUID(), 0, 0,
                new OrientationController.State(pose, localCenter, angular, 1),
                Vec3.ZERO, new Vec3(0, -9.81, 0), boxes,
                new OrientationTargets.Baselines(Optional.empty(), Optional.empty()), true);
    }
}
