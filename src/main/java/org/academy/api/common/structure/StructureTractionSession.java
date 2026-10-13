package org.academy.api.common.structure;

import net.minecraft.world.phys.Vec3;

import java.util.UUID;

/**
 * One exact actor, runtime handle and local point. Every method requires the server thread.
 * Physics pose changes move the world point without changing its local identity. A terminal
 * session never reacquires a lease, even if the same structure/entity UUID becomes available.
 * Resource ownership belongs to the caller; close the session when that action ends.
 */
public interface StructureTractionSession extends AutoCloseable {
    StructureRef reference();
    UUID runtimeId();
    Vec3 localPoint();

    /** Current point while the exact runtime remains active, otherwise the last observation. */
    Vec3 worldPoint();

    /** Last accepted target; targets are never silently clamped. */
    Vec3 targetPosition();

    /** Reads the session/lease state without renewing or acquiring control. */
    StructureTraction.Status status();

    /**
     * Checks actor, runtime, permission, mass, current-point reach, loaded visibility and
     * region bounds, then replaces the PD target and renews its short lease. A nearer face
     * of this same structure is allowed; host blocks or another structure occlude control.
     * The current rotated body AABB translated to the requested target, and to its next-tick
     * velocity prediction, must fit the region with a 0.25-block margin. This does not predict
     * future body rotation, stop residual velocity or add a hard positional constraint.
     * Failure releases this session and is terminal. Velocity is blocks per second.
     */
    StructureTraction.Status update(Vec3 targetPosition, Vec3 targetVelocity);

    /** Idempotently releases control without changing physical velocity. */
    @Override void close();
}
