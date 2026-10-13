package org.academy.internal.common.structure.physics;

import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.academy.api.common.structure.StructurePhysics;
import org.jspecify.annotations.Nullable;

import java.util.Objects;

/**
 * Persisted intent and last coherent motion observation, bound to its manifest entry's pose.
 * This value never loads native code or authorizes automatic activation after a restart.
 */
public record NativeStructurePhysicsAttachment(AABB bounds, @Nullable Motion motion, boolean resumeAllowed) {
    public NativeStructurePhysicsAttachment {
        Objects.requireNonNull(bounds, "bounds");
        for (double value : new double[]{bounds.minX, bounds.minY, bounds.minZ, bounds.maxX, bounds.maxY, bounds.maxZ}) {
            PhysicsLimits.range(value, -29_999_980, 29_999_980, "saved region coordinate");
        }
        for (double size : new double[]{bounds.getXsize(), bounds.getYsize(), bounds.getZsize()}) {
            if (!Double.isFinite(size) || size <= 0 || size > StructurePhysics.MAX_REGION_AXIS) {
                throw new IllegalArgumentException("Saved physics region axes must be in (0, 24]");
            }
        }
    }

    /** Preserves the checkpoint when a failure forbids resuming it. */
    public NativeStructurePhysicsAttachment withResumeAllowed(boolean allowed) {
        return new NativeStructurePhysicsAttachment(bounds, motion, allowed);
    }

    /**
     * World-space COM velocity in blocks/second and angular velocity in radians/second.
     * The local COM belongs to the saved geometry, not the host-world grid origin.
     * Sleeping is an observation only: restoration rebuilds contacts and wakes the body.
     * Runtime handles, geometry epochs and content revision counters are intentionally absent.
     */
    public record Motion(Vec3 linearVelocity, Vec3 angularVelocity, Vec3 localCenterOfMass,
                         boolean sleeping, String geometryDigest) {
        public Motion {
            requireVector(linearVelocity, 1.0E4, "saved linear velocity");
            requireVector(angularVelocity, 1.0E4, "saved angular velocity");
            requireVector(localCenterOfMass, 1.0E6, "saved local center of mass");
            Objects.requireNonNull(geometryDigest, "geometryDigest");
            if (!geometryDigest.matches("[0-9a-f]{64}")) {
                throw new IllegalArgumentException("Saved geometry digest must be a lowercase SHA-256 value");
            }
        }

        private static void requireVector(Vec3 value, double maximum, String field) {
            Objects.requireNonNull(value, field);
            PhysicsLimits.range(value.x, -maximum, maximum, field);
            PhysicsLimits.range(value.y, -maximum, maximum, field);
            PhysicsLimits.range(value.z, -maximum, maximum, field);
        }
    }
}
