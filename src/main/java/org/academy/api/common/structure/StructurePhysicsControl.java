package org.academy.api.common.structure;

import net.minecraft.world.phys.Vec3;

/**
 * Immutable, actor-independent requests for a force at a structure-local point.
 * Coordinates use blocks and velocities use seconds. These values do not grant ownership,
 * load a structure or change its pose; the receiving region validates its current geometry
 * and controls the request's lifetime. Neither variant directly locks orientation.
 */
public sealed interface StructurePhysicsControl permits StructurePhysicsControl.Force, StructurePhysicsControl.Anchor {
    double MAX_FORCE = 10_000;
    double MAX_LOCAL_COORDINATE = 1_000_000;
    double MAX_WORLD_COORDINATE = 30_000_000;
    double MAX_TARGET_SPEED = 100;
    double MAX_GAIN = 10_000;

    Vec3 localPoint();

    /**
     * A world-space force in solver mass units times blocks/second squared, applied at localPoint.
     * Its Euclidean norm must not exceed MAX_FORCE; an excessive request is rejected, not clamped.
     * Zero force is valid. A region consumes this request over its next successful outer physics tick.
     */
    record Force(Vec3 localPoint, Vec3 force) implements StructurePhysicsControl {
        public Force {
            requireComponents(localPoint, MAX_LOCAL_COORDINATE, "localPoint");
            requireNorm(force, MAX_FORCE, "force");
        }
    }

    /**
     * A single-point spring and damper toward a host-world target, with a bounded output force.
     * Stiffness is force/block and damping is force/(block/second); maxForce limits the full
     * force vector's norm. Target velocity is host-world blocks/second and does not itself
     * advance targetPosition: callers must update a moving target explicitly.
     *
     * <p>No gravity feed-forward or orientation constraint is added. A suspended mass therefore
     * has a static position error under gravity, and an off-center point can rotate the body.
     * Collision response remains the solver's responsibility. Regions revoke controls at their
     * pause/content/lifecycle boundaries; these requests are not saved checkpoints.</p>
     */
    record Anchor(Vec3 localPoint, Vec3 targetPosition, Vec3 targetVelocity,
                  double stiffness, double damping, double maxForce) implements StructurePhysicsControl {
        public Anchor {
            requireComponents(localPoint, MAX_LOCAL_COORDINATE, "localPoint");
            requireComponents(targetPosition, MAX_WORLD_COORDINATE, "targetPosition");
            requireNorm(targetVelocity, MAX_TARGET_SPEED, "targetVelocity");
            requireRange(stiffness, MAX_GAIN, false, "stiffness");
            requireRange(damping, MAX_GAIN, true, "damping");
            requireRange(maxForce, MAX_FORCE, false, "maxForce");
        }
    }

    private static void requireComponents(Vec3 value, double maximum, String name) {
        StructurePose.requireFinite(value, name);
        if (Math.abs(value.x) > maximum || Math.abs(value.y) > maximum || Math.abs(value.z) > maximum) {
            throw new IllegalArgumentException(name + " components exceed " + maximum);
        }
    }

    private static void requireNorm(Vec3 value, double maximum, String name) {
        StructurePose.requireFinite(value, name);
        if (Math.hypot(Math.hypot(value.x, value.y), value.z) > maximum) {
            throw new IllegalArgumentException(name + " norm exceeds " + maximum);
        }
    }

    private static void requireRange(double value, double maximum, boolean zeroAllowed, String name) {
        if (!Double.isFinite(value) || value < 0 || !zeroAllowed && value == 0 || value > maximum) {
            throw new IllegalArgumentException(name + " must be " + (zeroAllowed ? "in [0, " : "in (0, ") + maximum + "]");
        }
    }
}
