package org.academy.internal.common.structure.upgrade.damage;

import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.Comparator;
import java.util.PriorityQueue;

/** Near-first binary subdivision: long diagonal beams do not scan their entire bounding cube. */
public final class DamagePathRegion {
    private DamagePathRegion() {}

    public static AABB bounds(Vec3 start, Vec3 end, double radius) {
        return new AABB(start, end).inflate(radius + 1.0e-6);
    }

    /** Entry fraction in [0,1], or positive infinity. A zero-length path still selects its origin. */
    public static double entry(Vec3 start, Vec3 end, AABB box) {
        double lo = 0, hi = 1;
        for (int axis = 0; axis < 3; axis++) {
            double origin = axis == 0 ? start.x : axis == 1 ? start.y : start.z;
            double delta = (axis == 0 ? end.x : axis == 1 ? end.y : end.z) - origin;
            double min = axis == 0 ? box.minX : axis == 1 ? box.minY : box.minZ;
            double max = axis == 0 ? box.maxX : axis == 1 ? box.maxY : box.maxZ;
            if (Math.abs(delta) < 1.0e-12) {
                if (origin < min || origin > max) return Double.POSITIVE_INFINITY;
            } else {
                double a = (min - origin) / delta;
                double b = (max - origin) / delta;
                lo = Math.max(lo, Math.min(a, b)); hi = Math.min(hi, Math.max(a, b));
                if (lo > hi) return Double.POSITIVE_INFINITY;
            }
        }
        return lo;
    }

    public record Cell(BlockPos position, double entry) {}
    private record Node(int x0, int y0, int z0, int x1, int y1, int z1, double entry) {}

    public static final class Cursor {
        private final Vec3 start, end;
        private final double radius;
        private final PriorityQueue<Node> pending = new PriorityQueue<>(Comparator.comparingDouble(Node::entry));

        public Cursor(Vec3 start, Vec3 end, double radius, AABB envelope) {
            this.start = start; this.end = end; this.radius = radius;
            var region = bounds(start, end, radius);
            if (!region.intersects(envelope)) return;
            var area = region.intersect(envelope);
            offer(Mth.floor(area.minX), Mth.floor(area.minY), Mth.floor(area.minZ),
                    Math.min(Mth.floor(area.maxX), Mth.ceil(envelope.maxX) - 1),
                    Math.min(Mth.floor(area.maxY), Mth.ceil(envelope.maxY) - 1),
                    Math.min(Mth.floor(area.maxZ), Mth.ceil(envelope.maxZ) - 1));
        }

        public boolean hasNext() { return !pending.isEmpty(); }
        public double frontier() { return pending.isEmpty() ? Double.POSITIVE_INFINITY : pending.peek().entry; }

        /** One bounded scan unit; an internal node returns null after splitting into at most two. */
        public @org.jspecify.annotations.Nullable Cell step() {
            var n = pending.remove();
            int dx = n.x1 - n.x0, dy = n.y1 - n.y0, dz = n.z1 - n.z0;
            if (dx == 0 && dy == 0 && dz == 0) return new Cell(new BlockPos(n.x0, n.y0, n.z0), n.entry);
            if (dx >= dy && dx >= dz) {
                int m = n.x0 + dx / 2;
                offer(n.x0, n.y0, n.z0, m, n.y1, n.z1); offer(m + 1, n.y0, n.z0, n.x1, n.y1, n.z1);
            } else if (dy >= dz) {
                int m = n.y0 + dy / 2;
                offer(n.x0, n.y0, n.z0, n.x1, m, n.z1); offer(n.x0, m + 1, n.z0, n.x1, n.y1, n.z1);
            } else {
                int m = n.z0 + dz / 2;
                offer(n.x0, n.y0, n.z0, n.x1, n.y1, m); offer(n.x0, n.y0, m + 1, n.x1, n.y1, n.z1);
            }
            return null;
        }

        private void offer(int x0, int y0, int z0, int x1, int y1, int z1) {
            if (x0 > x1 || y0 > y1 || z0 > z1) return;
            double t = entry(start, end, new AABB(x0, y0, z0, x1 + 1.0, y1 + 1.0, z1 + 1.0).inflate(radius));
            if (Double.isFinite(t)) pending.add(new Node(x0, y0, z0, x1, y1, z1, t));
        }
    }
}
