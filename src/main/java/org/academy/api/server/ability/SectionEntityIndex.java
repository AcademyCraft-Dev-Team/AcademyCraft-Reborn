package org.academy.api.server.ability;

import it.unimi.dsi.fastutil.ints.Int2LongOpenHashMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import net.minecraft.core.SectionPos;

import java.util.TreeMap;

/** Main-thread spatial index with resumable cursors that tolerate removal during callbacks. */
public final class SectionEntityIndex<T> {
    private final Int2LongOpenHashMap locations = new Int2LongOpenHashMap();
    private final it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap<T> entities = new it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap<>();
    private final Long2ObjectOpenHashMap<TreeMap<Integer, T>> sections = new Long2ObjectOpenHashMap<>();

    public void update(int id, double x, double y, double z, T value) {
        long key = SectionPos.asLong(section(x), section(y), section(z));
        if (locations.containsKey(id) && locations.get(id) == key && entities.get(id) == value) return;
        remove(id);
        locations.put(id, key);
        entities.put(id, value);
        sections.computeIfAbsent(key, _ -> new TreeMap<>()).put(id, value);
    }

    public void remove(int id) {
        if (!locations.containsKey(id)) return;
        long key = locations.remove(id);
        entities.remove(id);
        var bucket = sections.get(key);
        bucket.remove(id);
        if (bucket.isEmpty()) sections.remove(key);
    }

    public int size() { return locations.size(); }
    /** Short, local protection queries; callers must keep the box small. */
    public boolean anyInBox(double x, double y, double z, double radius, java.util.function.Predicate<T> predicate) {
        if (!Double.isFinite(radius) || radius < 0 || radius > 16) throw new IllegalArgumentException("Protection radius");
        for (int sx = section(x - radius); sx <= section(x + radius); sx++)
            for (int sy = section(y - radius); sy <= section(y + radius); sy++)
                for (int sz = section(z - radius); sz <= section(z + radius); sz++) {
                    var bucket = sections.get(SectionPos.asLong(sx, sy, sz));
                    if (bucket == null) continue;
                    var entry = bucket.firstEntry();
                    while (entry != null) {
                        if (predicate.test(entry.getValue())) return true;
                        entry = bucket.higherEntry(entry.getKey());
                    }
                }
        return false;
    }
    private static int section(double position) { return (int) Math.floor(position / 16.0); }

    public Cursor cursor(double x, double y, double z, double radius) {
        if (!Double.isFinite(radius) || radius <= 0 || radius > 512) throw new IllegalArgumentException("Invalid query radius");
        return new Cursor(section(x), section(y), section(z), (int) Math.ceil(radius / 16) + 1);
    }

    public final class Cursor {
        private final int x, y, z, maximum;
        private int shell, offset, lastId = Integer.MIN_VALUE;
        private Cursor(int x, int y, int z, int maximum) {
            this.x = x; this.y = y; this.z = z; this.maximum = maximum;
        }
        public boolean finished() { return shell > maximum; }

        /** At most one bucket lookup and one ordered entity lookup; null also represents an empty cell. */
        public T next() {
            if (finished()) return null;
            int dx = 0, dy = 0, dz = 0;
            if (shell > 0) {
                int side = shell * 2 + 1, inner = side - 2, index = offset;
                int face = side * side;
                if (index < face * 2) {
                    dx = index < face ? -shell : shell;
                    index %= face;
                    dy = index / side - shell; dz = index % side - shell;
                } else if ((index -= face * 2) < inner * side * 2) {
                    face = inner * side;
                    dy = index < face ? -shell : shell;
                    index %= face;
                    dx = index / side - shell + 1; dz = index % side - shell;
                } else {
                    index -= inner * side * 2;
                    face = inner * inner;
                    dz = index < face ? -shell : shell;
                    index %= face;
                    dx = index / inner - shell + 1; dy = index % inner - shell + 1;
                }
            }
            var bucket = sections.get(SectionPos.asLong(x + dx, y + dy, z + dz));
            var next = bucket == null ? null : bucket.higherEntry(lastId);
            if (next != null) {
                lastId = next.getKey();
                return next.getValue();
            }
            lastId = Integer.MIN_VALUE;
            if (++offset >= (shell == 0 ? 1 : 24 * shell * shell + 2)) { shell++; offset = 0; }
            return null;
        }
    }
}
