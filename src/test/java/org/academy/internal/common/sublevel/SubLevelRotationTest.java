package org.academy.internal.common.sublevel;

import net.minecraft.world.phys.Vec3;
import org.academy.api.common.structure.StructurePose;
import org.joml.Quaterniond;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SubLevelRotationTest {
    @Test
    void repeatedAbsoluteRotationsPreserveTheOriginalWorldMassCenter() {
        var center = new Vec3(2.75, 0.5, 1.25);
        var pose = new StructurePose(new Vec3(24, 80, -17), new Quaterniond().rotateXYZ(0.3, 0.6, -0.1));
        var worldCenter = pose.localToWorldPoint(center);
        for (int step = 0; step < 8; step++) {
            var target = new StructurePose(new Vec3(1_000, 2_000, 3_000),
                    new Quaterniond().rotateXYZ(step * 0.2, step * 0.4, step * -0.3));
            pose = SubLevelRuntime.rotationAround(pose, center, target);
            assertTrue(worldCenter.distanceTo(pose.localToWorldPoint(center)) < 1.0E-9);
            assertTrue(pose.localToWorldDirection(new Vec3(0, 1, 0))
                    .distanceTo(target.localToWorldDirection(new Vec3(0, 1, 0))) < 1.0E-9);
        }
    }

    @Test
    void poseChangesDoNotInvalidateGeometryTargets() {
        var subLevel = new SubLevel(SubLevelId.random(), new PlotPos(0, 0),
                new net.minecraft.world.phys.AABB(0, 0, 0, 2, 2, 2), StructurePose.identity(Vec3.ZERO));
        subLevel.setPose(StructurePose.identity(new Vec3(1, 0, 0)));
        assertEquals(0, subLevel.geometryRevision());
        subLevel.geometryChanged();
        assertEquals(1, subLevel.geometryRevision());
        subLevel.setPose(StructurePose.identity(new Vec3(2, 0, 0)));
        assertEquals(1, subLevel.geometryRevision());
    }
}
