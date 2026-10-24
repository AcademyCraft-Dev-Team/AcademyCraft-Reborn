package org.academy.internal.common.structure.sable;

import org.academy.internal.common.structure.sable.reflect.api.companion.math.Pose3d;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.academy.api.common.structure.StructurePose;
import org.joml.Quaterniond;
import org.joml.Vector3d;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class SableStructureTransformsTest {
    /** Independent pose fixture: ordinary tests must run without any optional mod JAR. */
    private static Pose3d pose(org.joml.Vector3d position, org.joml.Quaterniond orientation,
                               org.joml.Vector3d pivot, org.joml.Vector3d scale) {
        return (Pose3d) java.lang.reflect.Proxy.newProxyInstance(Pose3d.class.getClassLoader(),
                new Class<?>[]{Pose3d.class}, (proxy, method, args) -> switch (method.getName()) {
                    case "position" -> position;
                    case "orientation" -> orientation;
                    case "rotationPoint" -> pivot;
                    case "scale" -> scale;
                    case "transformPosition" -> {
                        var input = (Vec3) args[0];
                        var result = new Vector3d(input.x, input.y, input.z).sub(pivot).mul(scale);
                        orientation.transform(result).add(position);
                        yield new Vec3(result.x, result.y, result.z);
                    }
                    default -> throw new UnsupportedOperationException(method.toString());
                });
    }

    private static Pose3d pose() { return pose(new Vector3d(), new Quaterniond(), new Vector3d(), new Vector3d(1)); }
    @Test
    void mapsStableLocalGridThroughPlotOriginAndCenterOfMass() {
        var origin = new BlockPos(1_500_008, 64, -1_499_992);
        var pose = pose(new Vector3d(12, 90, -7), new Quaterniond().rotationYXZ(0.6, -0.3, 0.4),
                new Vector3d(origin.getX() + 1.25, origin.getY() - 2.5, origin.getZ() + 3.75), new Vector3d(1));
        var adapted = SableStructureTransforms.pose(pose, origin);
        for (var local : new Vec3[]{Vec3.ZERO, new Vec3(-3, 0.5, 7), new Vec3(1.25, -2.5, 3.75)}) {
            var expected = pose.transformPosition(local.add(Vec3.atLowerCornerOf(origin)));
            assertVector(expected, adapted.localToWorldPoint(local), 1.0E-8);
            assertVector(local, adapted.worldToLocalPoint(expected), 1.0E-8);
        }
    }

    @Test
    void changingCenterOfMassWithoutMovingCellsPreservesPublicPose() {
        var origin = new BlockPos(160_008, 64, 320_008);
        var pose = pose(new Vector3d(8, 70, 16), new Quaterniond().rotationY(1.2),
                new Vector3d(origin.getX(), origin.getY(), origin.getZ()), new Vector3d(1));
        var before = SableStructureTransforms.pose(pose, origin);
        var shift = new Vector3d(2, -1, 3);
        pose.rotationPoint().add(shift);
        pose.position().add(pose.orientation().transform(shift, new Vector3d()));
        assertTrue(SableStructureTransforms.equivalentPose(before, SableStructureTransforms.pose(pose, origin)));
    }

    @Test
    void rejectsScaleInsteadOfSilentlyChangingHitDistances() {
        for (double scale : new double[]{0, 0.5, 2, Double.NaN}) {
            var pose = pose();
            pose.scale().set(1, scale, 1);
            assertFalse(SableStructureTransforms.unitScale(pose));
            assertThrows(IllegalStateException.class, () -> SableStructureTransforms.pose(pose, BlockPos.ZERO));
        }
    }

    @Test
    void unsupportedScaledOccluderUsesCurrentPoseRatherThanCachedBounds() {
        var pose = pose(new Vector3d(20, 30, 40), new Quaterniond().rotationZ(Math.PI / 2),
                new Vector3d(100, 60, 200), new Vector3d(2, 3, 1));
        var bounds = SableStructureTransforms.worldBounds(pose, new AABB(99, 59, 199, 101, 61, 201));
        assertEquals(17, bounds.minX, 1.0E-8);
        assertEquals(23, bounds.maxX, 1.0E-8);
        assertEquals(28, bounds.minY, 1.0E-8);
        assertEquals(32, bounds.maxY, 1.0E-8);
        assertEquals(39, bounds.minZ, 1.0E-8);
        assertEquals(41, bounds.maxZ, 1.0E-8);
    }

    @Test
    void checkedPlotAddressCannotWrapIntoAnotherStructure() {
        assertEquals(new BlockPos(12, 24, 36), SableStructureTransforms.plotPosition(
                new BlockPos(10, 20, 30), new BlockPos(2, 4, 6)));
        assertThrows(ArithmeticException.class, () -> SableStructureTransforms.plotPosition(
                new BlockPos(Integer.MAX_VALUE, 0, 0), new BlockPos(1, 0, 0)));
    }

    @Test
    void equivalentPoseAcceptsQuaternionSignAndRejectsActualMovement() {
        var pose = new StructurePose(new Vec3(1, 2, 3), new Quaterniond().rotationXYZ(0.1, 0.2, 0.3));
        var negative = new StructurePose(pose.worldOrigin(), -pose.rotationX(), -pose.rotationY(),
                -pose.rotationZ(), -pose.rotationW());
        assertTrue(SableStructureTransforms.equivalentPose(pose, negative));
        assertFalse(SableStructureTransforms.equivalentPose(pose,
                new StructurePose(pose.worldOrigin().add(0.001, 0, 0), pose.rotation())));
    }

    private static void assertVector(Vec3 expected, Vec3 actual, double tolerance) {
        assertEquals(expected.x, actual.x, tolerance);
        assertEquals(expected.y, actual.y, tolerance);
        assertEquals(expected.z, actual.z, tolerance);
    }
}
