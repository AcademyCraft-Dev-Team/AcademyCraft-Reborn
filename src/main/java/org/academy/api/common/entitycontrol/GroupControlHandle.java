package org.academy.api.common.entitycontrol;

/** Lifecycle handle for one autonomous high-level entity order. */
public interface GroupControlHandle extends AutoCloseable {
    boolean isClosed();

    @Override
    void close();
}
