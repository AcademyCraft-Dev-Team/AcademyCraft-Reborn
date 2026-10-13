package org.academy.api.common.structure;

import net.minecraft.world.entity.Entity;
import org.academy.internal.common.structure.physics.NativeStructureTraction;

import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.function.BiPredicate;

/** Shared server-side point traction for players, ordinary entities and trusted programs. */
public final class StructureTraction {
    private StructureTraction() {}

    public enum Status {
        ACTIVE, OCCUPIED, NOT_AVAILABLE, NOT_VISIBLE, OUT_OF_RANGE, OUT_OF_BOUNDS,
        TOO_HEAVY, DENIED, INVALID_TARGET, CLOSED
    }

    /**
     * Fixed physical budgets, never multiplied by body mass. Range uses blocks; stiffness,
     * damping and force use the same second-based solver units as StructurePhysicsControl.
     * Successful updates renew a short lease; stopping updates cannot retain it indefinitely.
     */
    public record Settings(double maxRange, double maxMass, double stiffness, double damping,
                           double maxForce, int leaseTicks) {
        public Settings {
            positive(maxRange, StructureQueries.MAX_RAY_LENGTH, "maxRange");
            positive(maxMass, 10_000, "maxMass");
            positive(stiffness, StructurePhysicsControl.MAX_GAIN, "stiffness");
            if (!Double.isFinite(damping) || damping < 0 || damping > StructurePhysicsControl.MAX_GAIN) {
                throw new IllegalArgumentException("damping must be finite and in [0, MAX_GAIN]");
            }
            positive(maxForce, StructurePhysicsControl.MAX_FORCE, "maxForce");
            if (leaseTicks < 1 || leaseTicks > 20) {
                throw new IllegalArgumentException("Traction lease must last 1 through 20 server ticks");
            }
        }

        private static void positive(double value, double maximum, String name) {
            if (!Double.isFinite(value) || value <= 0 || value > maximum) {
                throw new IllegalArgumentException(name + " must be finite and in (0, " + maximum + "]");
            }
        }
    }

    public record Acquisition(Status status, Optional<StructureTractionSession> session) {
        public Acquisition {
            Objects.requireNonNull(status, "status");
            Objects.requireNonNull(session, "session");
            if ((status == Status.ACTIVE) != session.isPresent()) {
                throw new IllegalArgumentException("Only active traction acquisitions carry a session");
            }
        }
    }

    /**
     * Revalidates a proposed hit against this actor's current eye/look and configured reach,
     * then acquires the already active native region's entity-bound control slot. Never loads
     * chunks, activates structures, opens physics regions or initializes a missing backend.
     * Expected contention/unavailability is reported as a status instead of an exception.
     *
     * <p>The permission predicate is mandatory and runs before acquisition and each update.
     * It is a trusted server policy, not permission supplied by a client. Ability eligibility,
     * resource payment and player packet validation remain the caller's responsibility.
     * A presentation/projection alone does not grant permission. Initial target is the exact
     * clicked world point; call update in the same tick to select a different destination.</p>
     *
     * @throws IllegalStateException for calls outside the owning server thread
     */
    public static Acquisition tryAcquire(Entity actor, UUID controllerId, StructureHit hit,
                                         Settings settings, BiPredicate<Entity, StructureHandle> permission) {
        return NativeStructureTraction.acquire(actor, controllerId, hit, settings, permission);
    }
}
