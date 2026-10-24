package org.academy.internal.common.sublevel.pipeline;

import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.AABB;
import org.academy.api.common.structure.StructurePipeline;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * PIPE-02 draft compiler. Exact occupied volume: full cells of the same material are merged into
 * rectangular parts, partial cells keep their real box list, and no global shrink box is produced.
 * Merging consumes each full cell once; the only repeated reads are growth-boundary checks.
 */
public final class PipelineGeometryCompiler {
    private static final double EPSILON = 1.0E-7;
    private static final double MAX_COORDINATE = 30_000_000.0;
    private static final int TIME_CHECK_MASK = 31;

    public StructurePipeline.Geometry compile(StructurePipeline.GeometryInput input,
                                              StructurePipeline.WorkLimit limit) {
        if (input == null) throw new IllegalArgumentException("Geometry input is required");
        if (limit == null) throw new IllegalArgumentException("Work limit is required");
        validateEpoch(input.epoch());
        if (limit.maximumNanos() <= 0 || limit.maximumCells() <= 0 || limit.maximumPublications() < 0) {
            throw new IllegalArgumentException("Geometry work limits must be positive and bounded");
        }
        var cells = input.cells();
        if (cells.size() > 4_096_096) throw new IllegalArgumentException("Geometry input exceeds its cell bound");
        var seen = new HashSet<BlockPos>();
        var full = new HashMap<BlockPos, Integer>();
        var partial = new ArrayList<StructurePipeline.CellShape>();
        int visited = 0;
        long started = System.nanoTime();
        for (var cell : cells) {
            if (cell == null) throw new IllegalArgumentException("Geometry cell is required");
            validateCell(cell);
            if (!seen.add(cell.localCell())) throw new IllegalArgumentException("Duplicate geometry cell " + cell.localCell());
            if (visited >= limit.maximumCells()) break;
            if ((visited & TIME_CHECK_MASK) == 0 && elapsed(started) > limit.maximumNanos()) break;
            visited++;
            if (isFull(cell)) full.put(cell.localCell(), cell.material());
            else partial.add(cell);
        }
        boolean complete = visited == cells.size();
        var parts = new ArrayList<StructurePipeline.ShapePart>(full.size() + partial.size());
        if (!full.isEmpty()) mergeFullCells(full, parts);
        for (var cell : partial) {
            for (var box : cell.boxes()) parts.add(new StructurePipeline.ShapePart(box.move(cell.localCell()), cell.material()));
        }
        if (parts.size() > 1_048_576) throw new IllegalArgumentException("Compiled geometry exceeds its part bound");
        return new StructurePipeline.Geometry(input.epoch(), List.copyOf(parts), visited, complete);
    }

    private static boolean isFull(StructurePipeline.CellShape cell) {
        if (cell.boxes().size() != 1) return false;
        var box = cell.boxes().getFirst();
        return near(box.minX, 0) && near(box.minY, 0) && near(box.minZ, 0)
                && near(box.maxX, 1) && near(box.maxY, 1) && near(box.maxZ, 1);
    }

    /** Deterministic greedy 3D merge of adjacent full cells with one material. */
    private static void mergeFullCells(Map<BlockPos, Integer> full, List<StructurePipeline.ShapePart> parts) {
        var positions = new ArrayList<BlockPos>(full.keySet());
        positions.sort(Comparator.comparingInt((BlockPos position) -> position.getY())
                .thenComparingInt(position -> position.getZ())
                .thenComparingInt(position -> position.getX()));
        var used = new HashSet<BlockPos>();
        var mutable = new BlockPos.MutableBlockPos();
        for (var origin : positions) {
            if (used.contains(origin)) continue;
            int material = full.get(origin);
            int x = origin.getX(), y = origin.getY(), z = origin.getZ();
            int maxX = x;
            while (full.getOrDefault(mutable.set(maxX + 1, y, z), Integer.MIN_VALUE) == material
                    && !used.contains(mutable)) {
                maxX++;
            }
            int maxZ = z;
            growZ:
            while (true) {
                int nextZ = maxZ + 1;
                for (int probeX = x; probeX <= maxX; probeX++) {
                    mutable.set(probeX, y, nextZ);
                    if (full.getOrDefault(mutable, Integer.MIN_VALUE) != material || used.contains(mutable)) break growZ;
                }
                maxZ = nextZ;
            }
            int maxY = y;
            growY:
            while (true) {
                int nextY = maxY + 1;
                for (int probeX = x; probeX <= maxX; probeX++) {
                    for (int probeZ = z; probeZ <= maxZ; probeZ++) {
                        mutable.set(probeX, nextY, probeZ);
                        if (full.getOrDefault(mutable, Integer.MIN_VALUE) != material || used.contains(mutable)) break growY;
                    }
                }
                maxY = nextY;
            }
            for (int markX = x; markX <= maxX; markX++) {
                for (int markY = y; markY <= maxY; markY++) {
                    for (int markZ = z; markZ <= maxZ; markZ++) used.add(new BlockPos(markX, markY, markZ));
                }
            }
            parts.add(new StructurePipeline.ShapePart(new AABB(x, y, z, maxX + 1.0, maxY + 1.0, maxZ + 1.0), material));
        }
    }

    private static void validateEpoch(StructurePipeline.Epoch epoch) {
        if (epoch == null || epoch.reference() == null || epoch.runtimeId() == null || epoch.revision() < 0) {
            throw new IllegalArgumentException("Geometry epoch is invalid");
        }
    }

    private static void validateCell(StructurePipeline.CellShape cell) {
        var position = cell.localCell();
        if (Math.abs((long) position.getX()) > MAX_COORDINATE || Math.abs((long) position.getY()) > MAX_COORDINATE
                || Math.abs((long) position.getZ()) > MAX_COORDINATE) {
            throw new IllegalArgumentException("Geometry cell is outside the coordinate bound");
        }
        if (cell.material() < 0) throw new IllegalArgumentException("Geometry material must be nonnegative");
        if (cell.boxes().isEmpty()) throw new IllegalArgumentException("Geometry cell requires at least one box");
        for (var box : cell.boxes()) {
            if (box == null || !finite(box) || box.getXsize() <= 0 || box.getYsize() <= 0 || box.getZsize() <= 0) {
                throw new IllegalArgumentException("Geometry boxes must be finite and positive");
            }
            if (Math.abs(box.minX) > MAX_COORDINATE || Math.abs(box.minY) > MAX_COORDINATE
                    || Math.abs(box.minZ) > MAX_COORDINATE || Math.abs(box.maxX) > MAX_COORDINATE
                    || Math.abs(box.maxY) > MAX_COORDINATE || Math.abs(box.maxZ) > MAX_COORDINATE) {
                throw new IllegalArgumentException("Geometry box is outside the coordinate bound");
            }
        }
    }

    private static boolean finite(AABB box) {
        return Double.isFinite(box.minX) && Double.isFinite(box.minY) && Double.isFinite(box.minZ)
                && Double.isFinite(box.maxX) && Double.isFinite(box.maxY) && Double.isFinite(box.maxZ);
    }

    private static boolean near(double value, double expected) {
        return Math.abs(value - expected) <= EPSILON;
    }

    private static long elapsed(long started) {
        long now = System.nanoTime();
        return now - started < 0 ? Long.MAX_VALUE : now - started;
    }
}
