package org.academy.api.server.time;

import java.util.Set;
import java.util.UUID;

/**
 * An owned, non-transferable contribution to an entity's time-stop immunity.
 * Closing one lease never removes immunity contributed by another lease.
 */
public interface TemporalImmunityLease extends AutoCloseable {
    UUID entityId();

    Set<TemporalPauseSource> sources();

    boolean isActive();

    @Override
    void close();
}
