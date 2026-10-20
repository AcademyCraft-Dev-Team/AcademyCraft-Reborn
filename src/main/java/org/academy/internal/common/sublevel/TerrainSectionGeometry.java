package org.academy.internal.common.sublevel;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.shapes.CollisionContext;
import org.academy.internal.common.structure.physics.PhysicsBox;
import org.academy.internal.common.structure.physics.PhysicsQuaternion;
import org.academy.internal.common.structure.physics.PhysicsVector;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** Direct block-state collision capture for one 16x16x16 terrain section. */
public final class TerrainSectionGeometry {
    private static final int MAX_MERGE_PASSES = 8;

    private TerrainSectionGeometry() {
    }

    /**
     * Boxes are relative to the section origin. Empty/air states are skipped; states with an
     * empty collision shape (grass, flowers) contribute nothing and do not fail the section.
     */
    public static SectionGeometry capture(ServerLevel host, int sectionX, int sectionY, int sectionZ) {
        Objects.requireNonNull(host, "host");
        if (host.getChunkSource().getChunkNow(sectionX, sectionZ) == null) {
            throw new IllegalStateException("Terrain section chunk is not loaded");
        }
        int minX = sectionX << 4, minY = sectionY << 4, minZ = sectionZ << 4;
        int maxY = Math.min(host.getMaxY(), minY + 16);
        var boxes = new ArrayList<PhysicsBox>();
        var context = CollisionContext.empty();
        var position = new BlockPos.MutableBlockPos();
        int blocks = 0;
        for (int x = minX; x < minX + 16; x++) {
            for (int z = minZ; z < minZ + 16; z++) {
                for (int y = Math.max(host.getMinY(), minY); y < maxY; y++) {
                    position.set(x, y, z);
                    var state = host.getBlockState(position);
                    if (state.isAir()) continue;
                    var shape = state.getCollisionShape(host, position, context);
                    if (shape.isEmpty()) continue;
                    blocks++;
                    for (var box : shape.toAabbs()) {
                        boxes.add(box(box.move(x, y, z), minX, minY, minZ));
                    }
                }
            }
        }
        return new SectionGeometry(List.copyOf(merge(boxes)), blocks, new AABB(minX, minY, minZ,
                minX + 16, maxY, minZ + 16));
    }

    /**
     * Packs exactly adjacent cuboids with identical cross-section spans. Each axis pass groups
     * boxes by the other two spans and merges contiguous runs, replacing the former pairwise
     * restart merge (O(n^3) on dense sections) with O(n log n) per pass. The union is exact; only
     * the box partition changes.
     */
    static List<PhysicsBox> merge(List<PhysicsBox> input) {
        if (input.size() < 2) return List.copyOf(input);
        List<AABB> boxes = new ArrayList<>(input.size());
        for (var box : input) {
            var center = box.center();
            var half = box.halfExtents();
            boxes.add(new AABB(center.x() - half.x(), center.y() - half.y(), center.z() - half.z(),
                    center.x() + half.x(), center.y() + half.y(), center.z() + half.z()));
        }
        for (int pass = 0; pass < MAX_MERGE_PASSES; pass++) {
            int before = boxes.size();
            boxes = mergeAxis(boxes, Axis.X);
            boxes = mergeAxis(boxes, Axis.Z);
            boxes = mergeAxis(boxes, Axis.Y);
            if (boxes.size() == before) break;
        }
        boxes.sort(Comparator.comparingDouble((AABB box) -> box.minX)
                .thenComparingDouble(box -> box.minY)
                .thenComparingDouble(box -> box.minZ)
                .thenComparingDouble(box -> box.maxX)
                .thenComparingDouble(box -> box.maxY)
                .thenComparingDouble(box -> box.maxZ));
        var result = new ArrayList<PhysicsBox>(boxes.size());
        for (var box : boxes) {
            result.add(new PhysicsBox(new PhysicsVector(box.getCenter().x, box.getCenter().y, box.getCenter().z),
                    PhysicsQuaternion.IDENTITY, new PhysicsVector(box.getXsize() / 2.0,
                    box.getYsize() / 2.0, box.getZsize() / 2.0)));
        }
        return result;
    }

    private static List<AABB> mergeAxis(List<AABB> boxes, Axis axis) {
        var groups = new HashMap<SpanKey, List<AABB>>();
        for (var box : boxes) {
            groups.computeIfAbsent(spanKey(box, axis), ignored -> new ArrayList<>()).add(box);
        }
        var merged = new ArrayList<AABB>(boxes.size());
        var comparator = comparator(axis);
        for (var group : groups.values()) {
            group.sort(comparator);
            AABB run = null;
            for (var box : group) {
                if (run != null && touches(run, box, axis)) {
                    run = extend(run, box, axis);
                } else {
                    if (run != null) merged.add(run);
                    run = box;
                }
            }
            if (run != null) merged.add(run);
        }
        return merged;
    }

    private static SpanKey spanKey(AABB box, Axis axis) {
        return switch (axis) {
            case X -> new SpanKey(box.minY, box.maxY, box.minZ, box.maxZ);
            case Y -> new SpanKey(box.minX, box.maxX, box.minZ, box.maxZ);
            case Z -> new SpanKey(box.minX, box.maxX, box.minY, box.maxY);
        };
    }

    private static Comparator<AABB> comparator(Axis axis) {
        return switch (axis) {
            case X -> Comparator.comparingDouble((AABB box) -> box.minX).thenComparingDouble(box -> box.maxX);
            case Y -> Comparator.comparingDouble((AABB box) -> box.minY).thenComparingDouble(box -> box.maxY);
            case Z -> Comparator.comparingDouble((AABB box) -> box.minZ).thenComparingDouble(box -> box.maxZ);
        };
    }

    private static boolean touches(AABB run, AABB next, Axis axis) {
        return switch (axis) {
            case X -> run.maxX == next.minX;
            case Y -> run.maxY == next.minY;
            case Z -> run.maxZ == next.minZ;
        };
    }

    private static AABB extend(AABB run, AABB next, Axis axis) {
        return switch (axis) {
            case X -> new AABB(run.minX, run.minY, run.minZ, next.maxX, run.maxY, run.maxZ);
            case Y -> new AABB(run.minX, run.minY, run.minZ, run.maxX, next.maxY, run.maxZ);
            case Z -> new AABB(run.minX, run.minY, run.minZ, run.maxX, run.maxY, next.maxZ);
        };
    }

    private static PhysicsBox box(AABB box, int minX, int minY, int minZ) {
        return new PhysicsBox(new PhysicsVector(box.getCenter().x - minX, box.getCenter().y - minY,
                box.getCenter().z - minZ), PhysicsQuaternion.IDENTITY,
                new PhysicsVector(box.getXsize() / 2.0, box.getYsize() / 2.0, box.getZsize() / 2.0));
    }

    private enum Axis { X, Y, Z }

    private record SpanKey(double first, double second, double third, double fourth) {
    }

    public record SectionGeometry(List<PhysicsBox> boxes, int blocks, AABB bounds) {
        public SectionGeometry {
            boxes = List.copyOf(boxes);
            if (blocks < 0) throw new IllegalArgumentException("Block count cannot be negative");
        }
    }
}
