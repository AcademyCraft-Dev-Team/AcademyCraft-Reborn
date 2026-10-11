package org.academy.api.client.renderer;

import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

import java.util.List;

/** Bounds-fitted geo surfaces shared by entity targeting and volume selection. */
public final class TargetSelectionModel {
    public static final double LOOP_SECONDS = 2.0;
    public static final double SCAN_SECONDS = 0.7;
    private TargetSelectionModel() { }

    public record Layout(AABB bounds, List<Vec3> footprint, double lineWidth,
                         double cornerLength, double verticalLength, boolean region) { }

    @FunctionalInterface
    public interface SurfaceConsumer {
        void quad(Vec3 a, Vec3 b, Vec3 c, Vec3 d, float opacity);
    }

    /** Entity diamonds enclose all four AABB corners; regions retain their exact rectangular boundary. */
    public static @Nullable Layout layout(AABB target, boolean region, double seconds) {
        return layout(target, region, seconds, false);
    }

    /** Grounded entities keep their lower ring just above the support surface. */
    public static @Nullable Layout layout(AABB target, boolean region, double seconds, boolean grounded) {
        if (target == null || !finite(target) || target.getXsize() <= 0
                || target.getYsize() <= 0 || target.getZsize() <= 0) return null;
        var center = target.getCenter();
        var phase = phase(seconds);
        var breathe = region ? 1.0 : 1.0 + 0.035 * (1.0 - Math.cos(phase * Math.PI)) / 2.0;
        var x = region ? target.getXsize() / 2.0 : (target.getXsize() + 0.04) * breathe;
        var z = region ? target.getZsize() / 2.0 : (target.getZsize() + 0.04) * breathe;
        var bottom = target.minY + (region ? 0 : grounded ? 0.02 : -0.08);
        var top = target.maxY + (region ? 0 : 0.08);
        var bounds = new AABB(center.x - x, bottom, center.z - z, center.x + x, top, center.z + z);
        var footprint = region ? List.of(
                new Vec3(center.x - x, 0, center.z - z), new Vec3(center.x + x, 0, center.z - z),
                new Vec3(center.x + x, 0, center.z + z), new Vec3(center.x - x, 0, center.z + z)) : List.of(
                new Vec3(center.x + x, 0, center.z), new Vec3(center.x, 0, center.z + z),
                new Vec3(center.x - x, 0, center.z), new Vec3(center.x, 0, center.z - z));
        var shortestEdge = Double.POSITIVE_INFINITY;
        for (int i = 0; i < 4; i++) shortestEdge = Math.min(shortestEdge,
                footprint.get(i).distanceTo(footprint.get((i + 1) % 4)));
        var width = Math.min(shortestEdge * 0.10,
                Math.clamp(Math.min(target.getXsize(), target.getZsize()) * 0.06, 0.016, 0.13));
        var corner = Math.min(shortestEdge * 0.20, 0.65);
        var vertical = Math.min(bounds.getYsize() * 0.18,
                Math.clamp(bounds.getYsize() * 0.11, 0.025, 0.6));
        return new Layout(bounds, footprint, width, corner, vertical, region);
    }

    /** Uses the current collision dimensions while interpolating only the entity's position. */
    public static AABB interpolatedBounds(AABB currentBounds, Vec3 currentPosition, Vec3 renderPosition) {
        return currentBounds.move(renderPosition.subtract(currentPosition));
    }

    /** Tests block support at the current feet, including motionless entities whose onGround flag is false. */
    public static AABB supportProbe(AABB bounds) {
        return new AABB(bounds.minX, bounds.minY - 0.08, bounds.minZ,
                bounds.maxX, bounds.minY + 0.001, bounds.maxZ);
    }

    public static double scanProgress(double seconds) {
        return Math.clamp(phase(seconds) / SCAN_SECONDS, 0, 1);
    }

    /** Emits narrow flat faces, their low-opacity halo, and two nearly transparent inward scan planes. */
    public static void emit(Layout layout, double seconds, double minimumLineWidth, SurfaceConsumer consumer) {
        var width = Math.min(layout.cornerLength() * 0.50,
                Math.max(layout.lineWidth(), minimumLineWidth));
        for (int layer = 0; layer < 2; layer++) {
            var thickness = width * (layer == 0 ? 3.0 : 1.0);
            var opacity = layer == 0 ? 0.10f : 1.0f;
            for (int i = 0; i < 4; i++) {
                var corner = layout.footprint().get(i);
                for (var upper : new boolean[]{false, true}) {
                    var y = upper ? layout.bounds().maxY : layout.bounds().minY;
                    var endY = y + (upper ? -1 : 1) * layout.verticalLength();
                    for (var neighbor : new int[]{(i + 3) % 4, (i + 1) % 4}) {
                        var direction = layout.footprint().get(neighbor).subtract(corner).normalize();
                        var end = corner.add(direction.scale(layout.cornerLength()));
                        var offset = new Vec3(-direction.z, 0, direction.x).scale(thickness / 2.0);
                        consumer.quad(atY(corner.add(offset), y), atY(end.add(offset), y),
                                atY(end.subtract(offset), y), atY(corner.subtract(offset), y), opacity);
                    }
                    var half = thickness / 2.0;
                    consumer.quad(new Vec3(corner.x - half, y, corner.z), new Vec3(corner.x + half, y, corner.z),
                            new Vec3(corner.x + half, endY, corner.z), new Vec3(corner.x - half, endY, corner.z), opacity);
                    consumer.quad(new Vec3(corner.x, y, corner.z - half), new Vec3(corner.x, y, corner.z + half),
                            new Vec3(corner.x, endY, corner.z + half), new Vec3(corner.x, endY, corner.z - half), opacity);
                }
            }
        }
        var progress = scanProgress(seconds);
        var eased = progress * progress * (3 - 2 * progress);
        var opacity = (float) ((layout.region() ? 0.008 : 0.04) * (1 - progress));
        if (opacity <= 0) return;
        for (var fraction : new double[]{0.3 + eased * 0.2, 0.7 - eased * 0.2}) {
            var y = layout.bounds().minY + layout.bounds().getYsize() * fraction;
            var p = layout.footprint();
            consumer.quad(atY(p.get(0), y), atY(p.get(1), y), atY(p.get(2), y), atY(p.get(3), y), opacity);
        }
    }

    private static Vec3 atY(Vec3 point, double y) { return new Vec3(point.x, y, point.z); }
    private static double phase(double seconds) {
        return Double.isFinite(seconds) ? (seconds % LOOP_SECONDS + LOOP_SECONDS) % LOOP_SECONDS : 0;
    }
    private static boolean finite(AABB b) {
        return Double.isFinite(b.minX) && Double.isFinite(b.minY) && Double.isFinite(b.minZ)
                && Double.isFinite(b.maxX) && Double.isFinite(b.maxY) && Double.isFinite(b.maxZ);
    }
}
