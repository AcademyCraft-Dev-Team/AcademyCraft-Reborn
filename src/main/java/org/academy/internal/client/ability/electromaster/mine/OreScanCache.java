package org.academy.internal.client.ability.electromaster.mine;

import java.util.*;
import java.util.function.LongSupplier;

/** Incremental three-dimensional scans. Results are published at each position, never at sweep completion. */
public final class OreScanCache {
    public static final int CELL_SIZE = 8;
    public static final int CELL_VOLUME = 512;
    private static final int REFRESH_TICKS = 100;
    private static final List<Offset> NEAR = offsets(8, true);
    private static final List<Offset> OUTER = offsets(11, false);

    /** Precompute distance tables during client initialization, outside the first ability tick. */
    public static void prepare() { }

    public interface Source {
        boolean loaded(CellPos cell);
        boolean empty(CellPos cell);
        OreGeometry read(int x, int y, int z);
    }

    public record CellPos(int x, int y, int z) {
        public static CellPos at(int x, int y, int z) {
            return new CellPos(x & -8, y & -8, z & -8);
        }

        public long distanceSquared(int px, int py, int pz) {
            long dx = Math.max(Math.max((long) x - px, 0), (long) px - (x + 7));
            long dy = Math.max(Math.max((long) y - py, 0), (long) py - (y + 7));
            long dz = Math.max(Math.max((long) z - pz, 0), (long) pz - (z + 7));
            return dx * dx + dy * dy + dz * dz;
        }

        public boolean fullyInside(int px, int py, int pz, int radius) {
            long dx = Math.max(Math.abs((long) x - px), Math.abs((long) x + 7 - px));
            long dy = Math.max(Math.abs((long) y - py), Math.abs((long) y + 7 - py));
            long dz = Math.max(Math.abs((long) z - pz), Math.abs((long) z + 7 - pz));
            return dx * dx + dy * dy + dz * dz <= (long) radius * radius;
        }
    }

    public static final class Cell {
        public final CellPos pos;
        private OreGeometry[] ores;
        private final BitSet known = new BitSet(CELL_VOLUME);
        private int cursor;
        private long scannedAt = Long.MIN_VALUE / 2;
        public long revision;
        public OreMesh mesh;
        public long meshRevision;

        private Cell(CellPos pos) { this.pos = pos; }

        public OreGeometry[] snapshot() {
            return ores == null ? new OreGeometry[CELL_VOLUME] : ores.clone();
        }

        public int count() {
            if (ores == null) return 0;
            int count = 0;
            for (var ore : ores) if (ore != null) count++;
            return count;
        }

        private void put(int index, OreGeometry ore) {
            known.set(index);
            if (ores == null && ore == null) return;
            if (ores == null) ores = new OreGeometry[CELL_VOLUME];
            if (Objects.equals(ores[index], ore)) return;
            ores[index] = ore;
            revision++;
        }

        public boolean accept(long version, OreMesh result) {
            if (revision != version) return false;
            mesh = result;
            meshRevision = version;
            return true;
        }
    }

    private record Offset(int x, int y, int z) {
        int distance() { return x * x + y * y + z * z; }
    }

    private final Map<CellPos, Cell> cells = new HashMap<>();
    private int x, y, z, radius, nearCursor, outerCursor;
    private CellPos anchor;
    private Cell current;
    private int currentFlip;
    private long sweepAfter;
    private int maintenanceCursor;
    private final List<CellPos> maintenance = new ArrayList<>();
    private final Set<CellPos> maintenanceKeys = new HashSet<>();

    public Collection<Cell> cells() { return cells.values(); }
    public boolean contains(Cell cell) { return cells.get(cell.pos) == cell; }
    public int x() { return x; }
    public int y() { return y; }
    public int z() { return z; }
    public int radius() { return radius; }

    public OreGeometry oreAt(int bx, int by, int bz) {
        var cell = cells.get(CellPos.at(bx, by, bz));
        return cell == null || cell.ores == null ? null : cell.ores[index(bx, by, bz)];
    }

    public void moveTo(int px, int py, int pz, int range) {
        boolean rangeChanged = radius != range;
        if (anchor == null || x != px || y != py || z != pz || radius != range) nearCursor = 0;
        x = px; y = py; z = pz; radius = range;
        var nextAnchor = CellPos.at(x, y, z);
        if (!nextAnchor.equals(anchor) || rangeChanged) {
            anchor = nextAnchor;
            outerCursor = 0;
            sweepAfter = 0;
            // Finish a partly scanned cell; frequent movement must not discard work.
        }
    }

    public void scan(Source source, long tick, long deadline, LongSupplier clock) {
        int steps = 0;
        // Reserve part of every tick for the outer sweep, even while walking.
        while (nearCursor < NEAR.size() && steps < 2048 && clock.getAsLong() < deadline) {
            var offset = NEAR.get(nearCursor++);
            int bx = x + offset.x, by = y + offset.y, bz = z + offset.z;
            var pos = CellPos.at(bx, by, bz);
            if (source.loaded(pos)) {
                var cell = cell(pos);
                int index = index(bx, by, bz);
                if (!cell.known.get(index)) cell.put(index, source.read(bx, by, bz));
            }
            steps++;
            if ((steps & 63) == 0 && clock.getAsLong() + 250_000 >= deadline) break;
        }
        while (clock.getAsLong() < deadline && steps++ < 16384) {
            if (current == null) {
                if (outerCursor == OUTER.size()) {
                    outerCursor = 0;
                    sweepAfter = tick + 20;
                    break;
                }
                if (tick < sweepAfter) break;
                var offset = OUTER.get(outerCursor++);
                var pos = new CellPos(anchor.x + offset.x * 8, anchor.y + offset.y * 8, anchor.z + offset.z * 8);
                if (pos.distanceSquared(x, y, z) > radius * radius || !source.loaded(pos)) continue;
                var cell = cell(pos);
                if (tick - cell.scannedAt < REFRESH_TICKS) continue;
                if (source.empty(pos)) {
                    for (int i = 0; i < CELL_VOLUME; i++) cell.put(i, null);
                    cell.scannedAt = tick;
                    continue;
                }
                current = cell;
                currentFlip = (x >= pos.x + 4 ? 7 : 0) | (y >= pos.y + 4 ? 56 : 0) | (z >= pos.z + 4 ? 448 : 0);
            }
            var pos = current.pos;
            if (!source.loaded(pos) || pos.distanceSquared(x, y, z) > radius * radius) {
                cells.remove(pos);
                current = null;
                continue;
            }
            // Walk each small cube from its player-facing corner. The separate exact near pass
            // handles the player's own cube and immediate neighbours first, including their Y axis.
            for (int n = 0; n < 64 && current.cursor < CELL_VOLUME; n++) {
                int cursor = current.cursor++ ^ currentFlip;
                int lx = cursor & 7, ly = cursor >> 3 & 7, lz = cursor >> 6;
                current.put(lx | ly << 3 | lz << 6, source.read(pos.x + lx, pos.y + ly, pos.z + lz));
            }
            if (current.cursor == CELL_VOLUME) {
                current.cursor = 0;
                current.scannedAt = tick;
                current = null;
            }
        }
        // A bounded rolling sweep also catches unloads without relying on mod-specific events.
        for (int n = 0; n < 32 && !maintenance.isEmpty(); n++) {
            if (maintenanceCursor >= maintenance.size()) maintenanceCursor = 0;
            var pos = maintenance.get(maintenanceCursor);
            if (!cells.containsKey(pos) || pos.distanceSquared(x, y, z) > radius * radius || !source.loaded(pos)) {
                cells.remove(pos);
                maintenanceKeys.remove(pos);
                var last = maintenance.removeLast();
                if (maintenanceCursor < maintenance.size()) maintenance.set(maintenanceCursor, last);
                if (maintenanceCursor == maintenance.size()) maintenanceCursor = 0;
            } else maintenanceCursor++;
        }
    }

    private Cell cell(CellPos pos) {
        return cells.computeIfAbsent(pos, key -> {
            if (maintenanceKeys.add(key)) maintenance.add(key);
            return new Cell(key);
        });
    }

    public void update(int bx, int by, int bz, OreGeometry ore) {
        var pos = CellPos.at(bx, by, bz);
        if (pos.distanceSquared(x, y, z) <= radius * radius) cell(pos).put(index(bx, by, bz), ore);
    }

    public void invalidateChunk(int chunkX, int chunkZ) {
        cells.values().removeIf(cell -> cell.pos.x >> 4 == chunkX && cell.pos.z >> 4 == chunkZ);
        if (current != null && !contains(current)) current = null;
        nearCursor = outerCursor = 0;
        sweepAfter = 0;
    }

    public static int index(int x, int y, int z) { return (x & 7) | (y & 7) << 3 | (z & 7) << 6; }

    private static List<Offset> offsets(int radius, boolean sphere) {
        var offsets = new ArrayList<Offset>();
        for (int x = -radius; x <= radius; x++) for (int y = -radius; y <= radius; y++)
            for (int z = -radius; z <= radius; z++) {
                var offset = new Offset(x, y, z);
                if (!sphere || offset.distance() <= radius * radius) offsets.add(offset);
            }
        offsets.sort(Comparator.comparingInt(Offset::distance));
        return List.copyOf(offsets);
    }
}
