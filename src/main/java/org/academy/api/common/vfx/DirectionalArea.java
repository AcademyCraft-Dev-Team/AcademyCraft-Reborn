package org.academy.api.common.vfx;

import net.minecraft.world.phys.Vec3;
import org.academy.api.common.util.ViewTargetScanner;

/** Two spherical sectors sharing an origin and axis. Shared by targeting and presentation. */
public record DirectionalArea(Vec3 origin, Vec3 direction, Cone first, Cone second) {
    public DirectionalArea {
        if (!Double.isFinite(origin.lengthSqr()) || !Double.isFinite(direction.lengthSqr())
                || direction.lengthSqr() < 1.0e-12) throw new IllegalArgumentException("Invalid area frame");
        direction = direction.normalize();
    }

    public record Cone(double radius, double minimumDot) {
        public Cone {
            if (!Double.isFinite(radius) || radius < 0 || !Double.isFinite(minimumDot)
                    || minimumDot < 0 || minimumDot > 1) throw new IllegalArgumentException("Invalid sector");
        }
        public ViewTargetScanner.Shape shape() { return ViewTargetScanner.cone(radius, minimumDot); }
        public boolean contains(double distance, double dot) { return distance <= radius && dot >= minimumDot; }
    }

    public double radius() { return Math.max(first.radius, second.radius); }
    public ViewTargetScanner.Shape shape() { return ViewTargetScanner.union(first.shape(), second.shape()); }
    public boolean contains(Vec3 point) {
        var delta = point.subtract(origin);
        double distance = delta.length();
        // ViewTargetScanner treats centers within 0.0001 blocks of the origin as inside the angular domain.
        double dot = delta.lengthSqr() <= 1.0e-8 ? 1 : direction.dot(delta.normalize());
        return first.contains(distance, dot) || second.contains(distance, dot);
    }

    /** Continuous camera transition over the boundary and near the sector apex. */
    public float airVisibility(Vec3 camera) {
        var delta = camera.subtract(origin);
        double distance = delta.length(), along = direction.dot(delta);
        double edge = Math.max(Math.min(first.radius - distance, along - distance * first.minimumDot),
                Math.min(second.radius - distance, along - distance * second.minimumDot));
        double inside = Math.clamp(0.5 + edge / 0.8, 0, 1);
        inside = inside * inside * (3 - 2 * inside);
        double apex = Math.clamp(1 - distance, 0, 1);
        return (float) (1 - 0.8 * Math.max(inside, apex * apex * (3 - 2 * apex)));
    }
}
