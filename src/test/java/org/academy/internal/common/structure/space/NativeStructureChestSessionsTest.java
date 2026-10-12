package org.academy.internal.common.structure.space;

import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;
import org.academy.api.common.structure.StructurePose;
import org.joml.Quaterniond;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class NativeStructureChestSessionsTest {
    @Test
    void reachFollowsTheRotatedChestInsteadOfTheStorageCellOrWorldOrigin() {
        var position = new BlockPos(-31, 300, 29);
        var pose = new StructurePose(new Vec3(1_000_000.25, -180.0, -2_000_000.5),
                new Quaterniond().rotationXYZ(0.37, -1.21, 2.17));
        var nearEye = pose.localToWorldPoint(Vec3.atCenterOf(position).add(0, 0, -3));
        assertTrue(NativeStructureChestSessions.withinReach(pose, position, nearEye, 4.5));
        assertFalse(NativeStructureChestSessions.withinReach(pose, position, Vec3.atCenterOf(position), 4.5));
        assertFalse(NativeStructureChestSessions.withinReach(pose, position, pose.worldOrigin(), 4.5));
        var moved = new StructurePose(pose.worldOrigin().add(40, 0, 0), pose.rotation());
        assertFalse(NativeStructureChestSessions.withinReach(moved, position, nearEye, 4.5));
    }

    @Test
    void reachUsesNearestBoxSurfaceWithVanillaFourBlockContainerBuffer() {
        var position = new BlockPos(10, 20, 30);
        var pose = StructurePose.identity(Vec3.ZERO);
        assertTrue(NativeStructureChestSessions.withinReach(pose, position, new Vec3(19.49, 20.5, 30.5), 4.5));
        assertFalse(NativeStructureChestSessions.withinReach(pose, position, new Vec3(19.5, 20.5, 30.5), 4.5));
        // The diagonal distance must use the nearest box point, not each axis independently.
        assertFalse(NativeStructureChestSessions.withinReach(pose, position, new Vec3(18, 28, 30.5), 4.5));
    }

    @Test
    void invalidInteractionRangesAndEyesFailClosed() {
        var pose = StructurePose.identity(Vec3.ZERO);
        for (double range : new double[] {-1, Double.NaN, Double.POSITIVE_INFINITY, 256.01}) {
            assertFalse(NativeStructureChestSessions.withinReach(pose, BlockPos.ZERO, Vec3.ZERO, range));
        }
        assertFalse(NativeStructureChestSessions.withinReach(pose, BlockPos.ZERO,
                new Vec3(Double.NaN, 0, 0), 4.5));
        assertFalse(NativeStructureChestSessions.withinReach(pose, BlockPos.ZERO,
                new Vec3(0, Double.POSITIVE_INFINITY, 0), 4.5));
    }
}
