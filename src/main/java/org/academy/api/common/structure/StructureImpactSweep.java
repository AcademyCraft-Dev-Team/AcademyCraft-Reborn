package org.academy.api.common.structure;

import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.academy.internal.common.structure.BlockStructureOrientedBox;

import java.util.List;

/** Shared bounded shape sweep for skill impacts against moving, rotated structures. */
public final class StructureImpactSweep {
    private StructureImpactSweep() {
    }

    /**
     * Conservative candidate bounds for both solver motion about the center of mass and the
     * origin interpolation used by {@link #intersects}. Endpoint boxes alone miss the arc of
     * a rotating cell. Each envelope includes every local corner at any intermediate rotation.
     */
    public static AABB sweptBounds(List<AABB> localBoxes, StructurePose previous,
                                   StructurePose current, Vec3 localCenterOfMass) {
        var massPath = new AABB(previous.localToWorldPoint(localCenterOfMass),
                current.localToWorldPoint(localCenterOfMass));
        var originPath = new AABB(previous.worldOrigin(), current.worldOrigin());
        return massPath.inflate(radius(localBoxes, localCenterOfMass))
                .minmax(originPath.inflate(radius(localBoxes, Vec3.ZERO)));
    }

    /** Samples the actual oriented cells, avoiding damage through gaps in the structure. */
    public static boolean intersects(List<AABB> localBoxes, StructurePose previous,
                                     StructurePose current, AABB target) {
        var targetBox = BlockStructureOrientedBox.axisAligned(target);
        double distance = previous.worldOrigin().distanceTo(current.worldOrigin());
        double radius = radius(localBoxes, Vec3.ZERO);
        var rotationDot = Math.abs(previous.rotationX() * current.rotationX()
                + previous.rotationY() * current.rotationY() + previous.rotationZ() * current.rotationZ()
                + previous.rotationW() * current.rotationW());
        distance += 2 * Math.acos(Math.min(1, rotationDot)) * radius;
        int samples = Math.max(4, Math.min(128, (int) Math.ceil(distance / 0.1)));
        for (int sample = 0; sample <= samples; sample++) {
            var pose = previous.lerp(current, (double) sample / samples);
            for (var box : localBoxes) {
                if (BlockStructureOrientedBox.transform(box, pose).intersects(targetBox)) return true;
            }
        }
        return false;
    }

    private static double radius(List<AABB> localBoxes, Vec3 pivot) {
        double radius = 0;
        for (var box : localBoxes) {
            double x = Math.max(Math.abs(box.minX - pivot.x), Math.abs(box.maxX - pivot.x));
            double y = Math.max(Math.abs(box.minY - pivot.y), Math.abs(box.maxY - pivot.y));
            double z = Math.max(Math.abs(box.minZ - pivot.z), Math.abs(box.maxZ - pivot.z));
            radius = Math.max(radius, Math.sqrt(x * x + y * y + z * z));
        }
        return radius;
    }
}
