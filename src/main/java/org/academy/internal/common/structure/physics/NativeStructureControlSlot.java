package org.academy.internal.common.structure.physics;

import org.academy.api.common.structure.StructurePhysicsControl;
import org.academy.api.common.structure.StructurePhysicsLease;

import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/** Pure control ownership state; the region enforces thread, reentry and live geometry checks. */
final class NativeStructureControlSlot {
    private StructurePhysicsControl control;
    private Ticket lease;

    Optional<StructurePhysicsControl> control() {
        return Optional.ofNullable(control);
    }

    void setUnleased(StructurePhysicsControl replacement) {
        Objects.requireNonNull(replacement, "control");
        requireUnleased();
        control = replacement;
    }

    boolean clearUnleased() {
        requireUnleased();
        boolean changed = control != null;
        control = null;
        return changed;
    }

    void requireUnleased() {
        if (lease != null) throw new IllegalStateException("Physics control slot has an active lease");
    }

    Optional<Ticket> acquire(UUID controllerId, StructurePhysicsControl initialControl, int durationTicks) {
        Objects.requireNonNull(controllerId, "controllerId");
        Objects.requireNonNull(initialControl, "control");
        requireDuration(durationTicks);
        if (lease != null || control != null) return Optional.empty();
        var ticket = new Ticket(controllerId, durationTicks);
        control = initialControl;
        lease = ticket;
        return Optional.of(ticket);
    }

    void invalidate() {
        if (lease != null) lease.finish(StructurePhysicsLease.State.INVALIDATED);
        control = null;
    }

    /** Successful outer physics step only; consuming a Force does not release its owner. */
    void consumeForce() {
        if (control instanceof StructurePhysicsControl.Force) control = null;
    }

    /** Called once at the region's server tick end, never once per solver substep. */
    void tick() {
        if (lease != null && --lease.remainingTicks == 0) lease.finish(StructurePhysicsLease.State.EXPIRED);
    }

    private static void requireDuration(int durationTicks) {
        if (durationTicks < 1 || durationTicks > StructurePhysicsLease.MAX_DURATION_TICKS) {
            throw new IllegalArgumentException("Lease duration must be in [1, "
                    + StructurePhysicsLease.MAX_DURATION_TICKS + "] server ticks");
        }
    }

    /** Identity is minted only by this slot. A caller cannot reconstruct it from a controller ID. */
    final class Ticket {
        private final UUID controllerId;
        private StructurePhysicsLease.State state = StructurePhysicsLease.State.ACTIVE;
        private int remainingTicks;

        private Ticket(UUID controllerId, int remainingTicks) {
            this.controllerId = controllerId;
            this.remainingTicks = remainingTicks;
        }

        UUID controllerId() {
            return controllerId;
        }

        StructurePhysicsLease.State state() {
            return state;
        }

        int remainingTicks() {
            return remainingTicks;
        }

        Optional<StructurePhysicsControl> control() {
            return state == StructurePhysicsLease.State.ACTIVE && lease == this
                    ? Optional.ofNullable(control) : Optional.empty();
        }

        void setControl(StructurePhysicsControl replacement) {
            Objects.requireNonNull(replacement, "control");
            requireActive();
            control = replacement;
        }

        void renew(int durationTicks) {
            requireDuration(durationTicks);
            requireActive();
            remainingTicks = durationTicks;
        }

        void close() {
            if (state == StructurePhysicsLease.State.ACTIVE && lease == this) {
                finish(StructurePhysicsLease.State.RELEASED);
            }
        }

        private void requireActive() {
            if (state != StructurePhysicsLease.State.ACTIVE || lease != this) {
                throw new IllegalStateException("Physics control lease is no longer active");
            }
        }

        private void finish(StructurePhysicsLease.State terminalState) {
            requireActive();
            state = terminalState;
            remainingTicks = 0;
            control = null;
            lease = null;
        }
    }
}
