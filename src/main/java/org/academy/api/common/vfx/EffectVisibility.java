package org.academy.api.common.vfx;

/** Allocation-free geometry shared by observer selection and visual bounds tests. */
public final class EffectVisibility {
    private EffectVisibility() {}

    public static double distanceToSegmentSquared(double x, double y, double z,
            double ax, double ay, double az, double bx, double by, double bz) {
        double dx = bx - ax, dy = by - ay, dz = bz - az;
        double length = dx * dx + dy * dy + dz * dz;
        double t = length > 1.0e-12
                ? Math.clamp(((x - ax) * dx + (y - ay) * dy + (z - az) * dz) / length, 0.0, 1.0) : 0.0;
        double px = x - ax - t * dx, py = y - ay - t * dy, pz = z - az - t * dz;
        return px * px + py * py + pz * pz;
    }
}
