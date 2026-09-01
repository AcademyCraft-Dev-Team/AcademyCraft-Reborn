package org.academy.api.server.time;

import java.util.UUID;

/**
 * An owned, non-transferable temporal-field contribution.
 * Closing one lease never changes another field.
 */
public interface TemporalFieldLease extends AutoCloseable {
    UUID fieldId();

    TemporalField field();

    boolean isActive();

    @Override
    void close();
}
