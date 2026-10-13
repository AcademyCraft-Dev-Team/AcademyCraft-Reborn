package org.academy.internal.common.structure;

import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.academy.api.common.structure.StructurePose;
import org.academy.internal.common.structure.space.NativeStructureView;

import java.util.ArrayList;
import java.util.List;
import java.util.OptionalDouble;

/**
 * World-space collision boxes for a synced native structure view. The view resolves local
 * neighbours, so shape lookups match the stored geometry on both sides. Axis-aligned poses
 * transform exactly; other quaternion poses are approximated by a bounded uniform subdivision
 * of each local box, because Minecraft entity collision only accepts axis-aligned shapes.
 */
public final class NativeStructureCollisionBoxes {
    public static final int MAX_WORLD_BOXES = 2048;
    private static final int MAX_PARTS = 3;
    private static final double EPSILON = 1.0E-7;
    private static final double ALIGNMENT_EPSILON = 1.0E-6;

    private NativeStructureCollisionBoxes() {}

    /** Local-space collision boxes for every view cell; empty for a view with no collision. */
    public static List<AABB> localBoxes(NativeStructureView view) {
        var boxes = new ArrayList<AABB>();
        for (var cell : view.cells()) {
            var shape = cell.state().getCollisionShape(view, cell.position());
            for (var box : shape.toAabbs()) boxes.add(box.move(cell.position()));
        }
        return List.copyOf(boxes);
    }

    /** Whether the pose maps every local axis exactly onto a world axis (any 90-degree multiple). */
    public static boolean axisAligned(StructurePose pose) {
        return aligned(pose.localToWorldDirection(new Vec3(1, 0, 0)))
                && aligned(pose.localToWorldDirection(new Vec3(0, 1, 0)))
                && aligned(pose.localToWorldDirection(new Vec3(0, 0, 1)));
    }

    /** Bounded world-space boxes; subdivision parts are reduced until the output budget holds. */
    public static List<AABB> worldBoxes(List<AABB> localBoxes, StructurePose pose) {
        if (localBoxes.isEmpty()) return List.of();
        int parts = MAX_PARTS;
        if (!axisAligned(pose)) {
            while (parts > 1 && (long) localBoxes.size() * parts * parts * parts > MAX_WORLD_BOXES) parts--;
        } else {
            parts = 1;
        }
        var result = new ArrayList<AABB>(localBoxes.size() * Math.min(parts * parts * parts, 8));
        for (var box : localBoxes) {
            if (parts == 1) {
                result.add(transformBox(box, pose));
                continue;
            }
            for (var part : subdivide(box, parts)) result.add(transformBox(part, pose));
        }
        return List.copyOf(result);
    }

    /** Exact for axis-aligned poses; the tight world AABB of every corner otherwise. */
    public static AABB transformBox(AABB box, StructurePose pose) {
        AABB transformed = null;
        for (int corner = 0; corner < 8; corner++) {
            var world = pose.localToWorldPoint(new Vec3(
                    (corner & 1) == 0 ? box.minX : box.maxX,
                    (corner & 2) == 0 ? box.minY : box.maxY,
                    (corner & 4) == 0 ? box.minZ : box.maxZ));
            var point = new AABB(world, world);
            transformed = transformed == null ? point : transformed.minmax(point);
        }
        return transformed;
    }

    static List<AABB> subdivide(AABB box, int parts) {
        var result = new ArrayList<AABB>(parts * parts * parts);
        for (int x = 0; x < parts; x++) {
            for (int y = 0; y < parts; y++) {
                for (int z = 0; z < parts; z++) {
                    result.add(new AABB(
                            box.minX + (box.maxX - box.minX) * x / parts,
                            box.minY + (box.maxY - box.minY) * y / parts,
                            box.minZ + (box.maxZ - box.minZ) * z / parts,
                            box.minX + (box.maxX - box.minX) * (x + 1) / parts,
                            box.minY + (box.maxY - box.minY) * (y + 1) / parts,
                            box.minZ + (box.maxZ - box.minZ) * (z + 1) / parts));
                }
            }
        }
        return List.copyOf(result);
    }

    public static boolean supports(List<AABB> boxes, AABB entityBounds, double tolerance) {
        return supportSurfaceY(boxes, entityBounds, tolerance).isPresent();
    }

    /** Highest box top under the entity footprint within tolerance of its feet, if any. */
    public static OptionalDouble supportSurfaceY(List<AABB> boxes, AABB entityBounds, double tolerance) {
        var safeTolerance = Math.max(0.0, tolerance);
        var bestSurface = Double.NaN;
        var bestDistance = Double.POSITIVE_INFINITY;
        for (var box : boxes) {
            var topDifference = entityBounds.minY - box.maxY;
            if (topDifference >= -safeTolerance && topDifference <= safeTolerance
                    && entityBounds.maxX > box.minX + EPSILON
                    && entityBounds.minX < box.maxX - EPSILON
                    && entityBounds.maxZ > box.minZ + EPSILON
                    && entityBounds.minZ < box.maxZ - EPSILON) {
                var distance = Math.abs(topDifference);
                if (distance + EPSILON < bestDistance
                        || (Math.abs(distance - bestDistance) <= EPSILON && box.maxY > bestSurface)) {
                    bestSurface = box.maxY;
                    bestDistance = distance;
                }
            }
        }
        return Double.isNaN(bestSurface) ? OptionalDouble.empty() : OptionalDouble.of(bestSurface);
    }

    private static boolean aligned(Vec3 direction) {
        return (nearZero(direction.x) || nearOne(direction.x))
                && (nearZero(direction.y) || nearOne(direction.y))
                && (nearZero(direction.z) || nearOne(direction.z));
    }

    private static boolean nearZero(double value) {
        return Math.abs(value) <= ALIGNMENT_EPSILON;
    }

    private static boolean nearOne(double value) {
        return Math.abs(Math.abs(value) - 1.0) <= ALIGNMENT_EPSILON;
    }
}
