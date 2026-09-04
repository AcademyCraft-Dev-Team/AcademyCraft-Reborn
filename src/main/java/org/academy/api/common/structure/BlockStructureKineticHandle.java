package org.academy.api.common.structure;

/** Cancellable ownership handle for one transient structure propulsion session. */
public interface BlockStructureKineticHandle extends AutoCloseable {
    boolean active();

    @Override
    void close();
}
