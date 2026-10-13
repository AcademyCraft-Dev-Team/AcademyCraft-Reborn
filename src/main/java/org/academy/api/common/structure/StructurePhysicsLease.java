package org.academy.api.common.structure;

import java.util.Optional;
import java.util.UUID;

/**
 * An exclusive, bounded lease on one exact attachment's transient physics control slot.
 * Every method, including reads and close, requires the owning server thread; mutations
 * must not reenter the region's simulation. The lease is a runtime capability, not a saved
 * association, a player permission check or a guarantee that a physics step can succeed.
 * Controller IDs are descriptive labels: presenting the same ID does not confer ownership.
 *
 * <p>Durations are 1 through {@link #MAX_DURATION_TICKS} server ticks. One tick is consumed
 * at the end of each server tick, independently of physics substeps. Updating a control
 * does not renew its lease. A Force is consumed after the next successful outer physics
 * tick, leaving an active lease with no command; that empty slot remains exclusively owned.
 * An Anchor persists until replaced, released, expired or invalidated.</p>
 *
 * <p>Region pause, attachment/content invalidation, unload, failure, close and server shutdown
 * invalidate live leases. No lease or command is saved or restored. A terminal lease never
 * becomes active again and cannot affect a later lease, even if the controller ID is reused.</p>
 */
public interface StructurePhysicsLease extends AutoCloseable {
    int MAX_DURATION_TICKS = 200;

    enum State {
        ACTIVE,
        RELEASED,
        EXPIRED,
        INVALIDATED
    }

    /** Persistent structure identity and generation; the lease itself binds this runtime attachment. */
    StructureRef reference();

    /** Descriptive controller label; ownership belongs to this lease instance. */
    UUID controllerId();

    State state();

    /** Remaining server tick boundaries before expiry, or zero in every terminal state. */
    int remainingTicks();

    /** Last accepted command; empty after Force consumption or any terminal transition. */
    Optional<StructurePhysicsControl> control();

    /**
     * Replaces this lease's command without extending its lifetime. Requires an active lease
     * and the region's current, unpaused geometry; point and target limits match
     * {@link StructurePhysicsRegion#setControl}. Invalid input leaves the old command intact.
     * A released, expired or invalidated lease rejects updates.
     */
    void setControl(StructurePhysicsControl control);

    /**
     * Replaces the remaining duration with 1 through MAX_DURATION_TICKS; does not add to it
     * or recreate a consumed command. Requires an active lease and current, unpaused geometry.
     * An invalid duration leaves the current deadline intact. Terminal leases reject renewal.
     */
    void renew(int durationTicks);

    /**
     * Releases this lease and clears its command without changing velocity. Idempotent after
     * any terminal transition; an old lease cannot release a newer owner's control slot.
     */
    @Override
    void close();
}
