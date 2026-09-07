package org.academy.api.client.render.vfxgraph.runtime;

/** Conservative pre-simulation bounds; counts both wings and all fourfold branches. */
public final class VortexRenderBudget {
    public static final int MAX_VERTICES = 120_000;
    public static final long MAX_UPLOAD_BYTES = 8L * 1024 * 1024;
    public record Detail(int filaments, int segments, int rings, int flecks) {
        public int maxVertices(boolean fourfold) {
            int points = 2 * (filaments + 1) * (segments + 1) + 24 * 13 + 2 * flecks * 3;
            return points * rings * (fourfold ? 2 : 1);
        }
        public long maxBytes(boolean fourfold) { return (long) maxVertices(fourfold) * 72; }
    }
    public static final java.util.List<Detail> DETAILS = java.util.List.of(
            new Detail(18, 100, 8, 24), new Detail(12, 64, 6, 12), new Detail(8, 40, 4, 6),
            new Detail(4, 24, 3, 0), new Detail(3, 24, 3, 0)
    );
    private int vertices;
    private int remainingReservations;
    private long bytes;
    public void beginFrame() { beginFrame(0); }
    public void beginFrame(int candidates) {
        vertices = 0; bytes = 0;
        remainingReservations = Math.clamp(candidates, 0, 16);
    }
    public int allocate(int preferred, boolean fourfold) {
        int remaining = Math.max(0, remainingReservations - 1);
        remainingReservations = remaining;
        var minimum = DETAILS.getLast();
        int reservedVertices = remaining * minimum.maxVertices(true);
        long reservedBytes = remaining * minimum.maxBytes(true);
        for (int tier = Math.clamp(preferred, 0, DETAILS.size() - 1); tier < DETAILS.size(); tier++) {
            var detail = DETAILS.get(tier);
            if (vertices + detail.maxVertices(fourfold) + reservedVertices > MAX_VERTICES
                    || bytes + detail.maxBytes(fourfold) + reservedBytes > MAX_UPLOAD_BYTES) continue;
            vertices += detail.maxVertices(fourfold); bytes += detail.maxBytes(fourfold);
            return tier;
        }
        return -1;
    }
    public int vertices() { return vertices; }
    public long bytes() { return bytes; }

    public static int preferred(double distance, int previous) {
        int requested = distance < 16 ? 1 : distance < 40 ? 2 : 3;
        if (previous >= 1 && previous <= 3) {
            if (requested > previous && distance < (previous == 1 ? 16 : 40) * 1.15) return previous;
            if (requested < previous && distance > (requested == 1 ? 16 : 40) * 0.85) return previous;
        }
        return requested;
    }
}
