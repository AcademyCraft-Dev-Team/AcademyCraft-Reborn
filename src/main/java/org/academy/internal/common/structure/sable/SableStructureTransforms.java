package org.academy.internal.common.structure.sable;

import org.academy.internal.common.structure.sable.reflect.api.companion.math.Pose3dc;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.academy.api.common.structure.StructurePose;

/** Keeps the public local grid independent of Sable's storage plot and moving center of mass. */
final class SableStructureTransforms {
    private SableStructureTransforms() {}

    static StructurePose pose(Pose3dc pose, BlockPos plotOrigin) {
        if (!unitScale(pose)) throw new IllegalStateException("Scaled Sable structures are not supported");
        return new StructurePose(pose.transformPosition(Vec3.atLowerCornerOf(plotOrigin)), pose.orientation());
    }

    static boolean unitScale(Pose3dc pose) {
        var scale = pose.scale();
        return scale.x() == 1.0 && scale.y() == 1.0 && scale.z() == 1.0;
    }

    static AABB worldBounds(Pose3dc pose, AABB plotBounds) {
        Vec3 minimum = null;
        Vec3 maximum = null;
        for (int corner = 0; corner < 8; corner++) {
            var point = pose.transformPosition(new Vec3(
                    (corner & 1) == 0 ? plotBounds.minX : plotBounds.maxX,
                    (corner & 2) == 0 ? plotBounds.minY : plotBounds.maxY,
                    (corner & 4) == 0 ? plotBounds.minZ : plotBounds.maxZ));
            minimum = minimum == null ? point : new Vec3(Math.min(minimum.x, point.x),
                    Math.min(minimum.y, point.y), Math.min(minimum.z, point.z));
            maximum = maximum == null ? point : new Vec3(Math.max(maximum.x, point.x),
                    Math.max(maximum.y, point.y), Math.max(maximum.z, point.z));
        }
        return new AABB(minimum, maximum);
    }

    static BlockPos plotPosition(BlockPos origin, BlockPos local) {
        return new BlockPos(Math.addExact(origin.getX(), local.getX()),
                Math.addExact(origin.getY(), local.getY()), Math.addExact(origin.getZ(), local.getZ()));
    }

    static boolean equivalentPose(StructurePose first, StructurePose second) {
        if (first.worldOrigin().distanceToSqr(second.worldOrigin()) > 1.0E-20) return false;
        var a = first.rotation();
        var b = second.rotation();
        return near(a.x, a.y, a.z, a.w, b.x, b.y, b.z, b.w)
                || near(a.x, a.y, a.z, a.w, -b.x, -b.y, -b.z, -b.w);
    }

    private static boolean near(double ax, double ay, double az, double aw,
                                double bx, double by, double bz, double bw) {
        return Math.abs(ax - bx) <= 1.0E-10 && Math.abs(ay - by) <= 1.0E-10
                && Math.abs(az - bz) <= 1.0E-10 && Math.abs(aw - bw) <= 1.0E-10;
    }
}
