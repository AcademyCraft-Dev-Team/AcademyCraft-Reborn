package org.academy.internal.common.structure.physics;

import net.minecraft.resources.Identifier;
import net.minecraft.world.phys.AABB;
import org.academy.api.common.structure.StructurePhysics;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/** Plans a whole failed cohort without removing its reservation or moving any structure. */
final class NativeStructureRecoveryBounds {
    private static final double HORIZONTAL_MARGIN = 3.0;

    private NativeStructureRecoveryBounds() {}

    static AABB plan(UUID id, Identifier host, AABB previous, List<AABB> occupied,
                     Collection<NativeStructurePhysicsRegionLayout.Region> reservations, AABB limits) {
        Objects.requireNonNull(id);
        Objects.requireNonNull(host);
        Objects.requireNonNull(previous);
        Objects.requireNonNull(occupied);
        Objects.requireNonNull(reservations);
        Objects.requireNonNull(limits);
        AABB required = null;
        for (var box : occupied) {
            if (!finite(box) || !contains(limits, box)) {
                throw new IllegalArgumentException("Recovered geometry lies outside the host");
            }
            required = required == null ? box : required.minmax(box);
        }
        var candidates = new ArrayList<AABB>();
        if (required != null) {
            // Leave room for rotation without consuming every free horizontal block around the
            // cohort. Neighboring independent regions need room for their own bodies to settle.
            double width = Math.min(StructurePhysics.MAX_REGION_AXIS,
                    required.getXsize() + 2 * HORIZONTAL_MARGIN);
            double depth = Math.min(StructurePhysics.MAX_REGION_AXIS,
                    required.getZsize() + 2 * HORIZONTAL_MARGIN);
            double minX = (required.minX + required.maxX - width) / 2;
            double minZ = (required.minZ + required.maxZ - depth) / 2;
            for (double y : starts(required.minY, required.maxY, previous.minY, previous.maxY,
                    limits.minY, limits.maxY)) {
                candidates.add(fit(new AABB(minX, y, minZ, minX + width,
                        y + StructurePhysics.MAX_REGION_AXIS, minZ + depth), required, limits));
            }
            // Preserve the full vertical activity volume so suspended bodies can fall to support.
            // Full cubes remain a fallback when the preferred compact footprint is unavailable.
            for (double x : starts(required.minX, required.maxX, previous.minX, previous.maxX, limits.minX, limits.maxX)) {
                for (double y : starts(required.minY, required.maxY, previous.minY, previous.maxY, limits.minY, limits.maxY)) {
                    for (double z : starts(required.minZ, required.maxZ, previous.minZ, previous.maxZ, limits.minZ, limits.maxZ)) {
                        candidates.add(new AABB(x, y, z, x + StructurePhysics.MAX_REGION_AXIS,
                                y + StructurePhysics.MAX_REGION_AXIS, z + StructurePhysics.MAX_REGION_AXIS));
                    }
                }
            }
        }
        candidates.add(previous);
        if (required != null) {
            candidates.add(fit(previous, required, limits));
            // A smaller reservation can avoid a neighbor that prevents translating the old box.
            var padding = Math.min(1.0, Math.max(0.0, (StructurePhysics.MAX_REGION_AXIS
                    - Math.max(required.getXsize(), Math.max(required.getYsize(), required.getZsize()))) / 2));
            candidates.add(fit(required.inflate(padding), required, limits));
            candidates.add(required);
        }
        for (var candidate : candidates) {
            if (candidate == null || !finite(candidate) || !contains(limits, candidate)
                    || required != null && !contains(candidate, required)) continue;
            var layout = new ArrayList<NativeStructurePhysicsRegionLayout.Region>();
            for (var reservation : reservations) {
                if (!reservation.id().equals(id)) layout.add(reservation);
                else if (!reservation.host().equals(host)) {
                    throw new IllegalArgumentException("Recovery region belongs to another host");
                }
            }
            try {
                layout.add(new NativeStructurePhysicsRegionLayout.Region(id, host, candidate));
                NativeStructurePhysicsRegionLayout.validate(layout);
                return candidate;
            } catch (IllegalArgumentException unavailable) {
                // Try only layouts that contain every member; never drop a neighboring reservation.
            }
        }
        throw new IllegalStateException("The complete failed cohort has no legal physics reservation");
    }

    private static List<Double> starts(double minimum, double maximum, double oldMinimum, double oldMaximum,
                                       double limitMinimum, double limitMaximum) {
        double size = StructurePhysics.MAX_REGION_AXIS;
        double lower = Math.max(limitMinimum, maximum - size);
        double upper = Math.min(limitMaximum - size, minimum);
        if (lower > upper) return List.of();
        return List.of(Math.clamp((minimum + maximum - size) / 2, lower, upper),
                Math.clamp(oldMinimum, lower, upper), Math.clamp(oldMaximum - size, lower, upper));
    }

    private static AABB fit(AABB preferred, AABB required, AABB limits) {
        double x = Math.max(preferred.getXsize(), required.getXsize());
        double y = Math.max(preferred.getYsize(), required.getYsize());
        double z = Math.max(preferred.getZsize(), required.getZsize());
        if (Math.max(x, Math.max(y, z)) > StructurePhysics.MAX_REGION_AXIS
                || x > limits.getXsize() || y > limits.getYsize() || z > limits.getZsize()) return null;
        double minX = Math.clamp(preferred.minX, required.maxX - x, required.minX);
        double minY = Math.clamp(preferred.minY, required.maxY - y, required.minY);
        double minZ = Math.clamp(preferred.minZ, required.maxZ - z, required.minZ);
        minX = Math.clamp(minX, limits.minX, limits.maxX - x);
        minY = Math.clamp(minY, limits.minY, limits.maxY - y);
        minZ = Math.clamp(minZ, limits.minZ, limits.maxZ - z);
        return new AABB(minX, minY, minZ, minX + x, minY + y, minZ + z);
    }

    private static boolean finite(AABB box) {
        return Double.isFinite(box.minX) && Double.isFinite(box.minY) && Double.isFinite(box.minZ)
                && Double.isFinite(box.maxX) && Double.isFinite(box.maxY) && Double.isFinite(box.maxZ);
    }

    private static boolean contains(AABB outer, AABB inner) {
        return outer.minX <= inner.minX && outer.minY <= inner.minY && outer.minZ <= inner.minZ
                && outer.maxX >= inner.maxX && outer.maxY >= inner.maxY && outer.maxZ >= inner.maxZ;
    }
}
