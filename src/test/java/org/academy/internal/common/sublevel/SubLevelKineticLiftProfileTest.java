package org.academy.internal.common.sublevel;

import net.minecraft.world.phys.Vec3;
import org.academy.api.common.ability.accelerator.KineticLiftProfile;
import org.academy.api.common.structure.StructureMotionState;
import org.academy.api.common.structure.StructurePose;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SubLevelKineticLiftProfileTest {
    private final SubLevelKineticLiftProfile profile = new SubLevelKineticLiftProfile();

    private static StructurePose pose(double x, double y, double z) {
        return StructurePose.identity(new Vec3(x, y, z));
    }

    @Test
    void tiersMapToThreeFiveAndSevenBlocks() {
        var local = new Vec3(0.5, 0.5, 0.5);
        for (int tier = 0; tier < 3; tier++) {
            var target = profile.planTarget(tier, new KineticLiftProfile.SourceBaseline(pose(10, 64, -5), local));
            assertEquals(3.0 + 2.0 * tier, target.requestedHeightBlocks(), 1.0E-9);
            assertEquals(64.5 + 3.0 + 2.0 * tier, target.targetWorldCenterOfMass().y, 1.0E-9);
            assertEquals(10.5, target.targetWorldCenterOfMass().x, 1.0E-9);
            assertEquals(-4.5, target.targetWorldCenterOfMass().z, 1.0E-9);
        }
    }

    @Test
    void targetUsesSourceWorldCenterOfMassNotGridOrigin() {
        var sourcePose = pose(100, 70, 200);
        var localCom = new Vec3(2.0, 3.0, 4.0);
        var target = profile.planTarget(1, new KineticLiftProfile.SourceBaseline(sourcePose, localCom));
        var expectedSource = sourcePose.localToWorldPoint(localCom);
        assertEquals(expectedSource.x, target.sourceWorldCenterOfMass().x, 1.0E-9);
        assertEquals(expectedSource.y, target.sourceWorldCenterOfMass().y, 1.0E-9);
        assertEquals(expectedSource.z, target.sourceWorldCenterOfMass().z, 1.0E-9);
        assertEquals(expectedSource.y + 5.0, target.targetWorldCenterOfMass().y, 1.0E-9);
    }

    @Test
    void completionMeasuresActualCenterOfMassAndTolerance() {
        var plan = profile.planTarget(2, new KineticLiftProfile.SourceBaseline(pose(0, 60, 0),
                new Vec3(0.5, 0.5, 0.5)));
        var localCom = new Vec3(0.5, 0.5, 0.5);
        var reached = new StructureMotionState(pose(0, 67.05, 0), Vec3.ZERO, Vec3.ZERO, localCom, 20,
                false, 0, 0);
        var measurement = profile.measureCompletion(plan, reached, SubLevelKineticLiftProfile.ACCEPTANCE_TOLERANCE);
        assertTrue(measurement.reached());
        assertEquals(7.05, measurement.actualRiseBlocks(), 1.0E-9);
        assertTrue(measurement.heightErrorBlocks() < 0.0);

        var shortLift = new StructureMotionState(pose(0, 66.85, 0), Vec3.ZERO, Vec3.ZERO, localCom, 20,
                false, 0, 0);
        assertFalse(profile.measureCompletion(plan, shortLift,
                SubLevelKineticLiftProfile.ACCEPTANCE_TOLERANCE).reached());
    }

    @Test
    void hoverDeadlineIsFirstReachedTickPlusTwoHundred() {
        assertEquals(1_200L, profile.hoverDeadline(1_000L));
        assertThrows(IllegalArgumentException.class, () -> profile.hoverDeadline(-1L));
    }

    @Test
    void invalidTierIsRejected() {
        var baseline = new KineticLiftProfile.SourceBaseline(pose(0, 0, 0), Vec3.ZERO);
        assertThrows(IllegalArgumentException.class, () -> profile.planTarget(-1, baseline));
        assertThrows(IllegalArgumentException.class, () -> profile.planTarget(3, baseline));
    }

    @Test
    void toleranceMustBeFiniteAndNonnegative() {
        var plan = profile.planTarget(0, new KineticLiftProfile.SourceBaseline(pose(0, 0, 0), Vec3.ZERO));
        var observation = new StructureMotionState(pose(0, 3, 0), Vec3.ZERO, Vec3.ZERO, Vec3.ZERO, 1,
                false, 0, 0);
        assertThrows(IllegalArgumentException.class, () -> profile.measureCompletion(plan, observation, -1));
        assertThrows(IllegalArgumentException.class, () -> profile.measureCompletion(plan, observation, Double.NaN));
    }
}
