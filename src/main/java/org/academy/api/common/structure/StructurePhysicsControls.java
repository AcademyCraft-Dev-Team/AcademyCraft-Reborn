package org.academy.api.common.structure;

import net.minecraft.world.phys.Vec3;

import java.util.Objects;

/** Pure force calculations shared by player skills, programs and non-player controllers. */
public final class StructurePhysicsControls {
    private StructurePhysicsControls() {}

    /**
     * Computes a world-space force from one coherent motion observation; never mutates it.
     * A solver integrates this value as impulse = force * substepSeconds at the current world
     * position of localPoint. It must still solve contacts and publish the resulting pose normally.
     * No implicit per-tick unit conversion, gravity compensation or mass normalization is used.
     */
    public static Vec3 force(StructurePhysicsControl control, StructureMotionState motion) {
        Objects.requireNonNull(control, "control");
        Objects.requireNonNull(motion, "motion");
        return switch (control) {
            case StructurePhysicsControl.Force applied -> applied.force();
            case StructurePhysicsControl.Anchor anchor -> {
                var point = motion.pose().localToWorldPoint(anchor.localPoint());
                var error = StructurePose.requireFinite(anchor.targetPosition().subtract(point), "position error");
                var velocityError = StructurePose.requireFinite(anchor.targetVelocity().subtract(
                        pointVelocity(anchor.localPoint(), motion)), "point velocity error");
                var requested = StructurePose.requireFinite(error.scale(anchor.stiffness())
                        .add(velocityError.scale(anchor.damping())), "PD force");
                yield clampNorm(requested, anchor.maxForce());
            }
        };
    }

    /** World-space blocks/second at a local point, including rotation about the actual local COM. */
    public static Vec3 pointVelocity(Vec3 localPoint, StructureMotionState motion) {
        Objects.requireNonNull(motion, "motion");
        return motion.pose().surfaceVelocity(localPoint, motion.localCenterOfMass(),
                motion.linearVelocity(), motion.angularVelocity());
    }

    private static Vec3 clampNorm(Vec3 force, double maximum) {
        double scale = Math.max(Math.max(Math.abs(force.x), Math.abs(force.y)), Math.abs(force.z));
        if (scale == 0) return Vec3.ZERO;
        // Normalize before measuring length: a finite input vector may have a norm above MAX_VALUE.
        double x = force.x / scale, y = force.y / scale, z = force.z / scale;
        double length = Math.hypot(Math.hypot(x, y), z);
        if (scale <= maximum / length) return force;
        double cappedLength = maximum / length;
        return new Vec3(x * cappedLength, y * cappedLength, z * cappedLength);
    }
}
