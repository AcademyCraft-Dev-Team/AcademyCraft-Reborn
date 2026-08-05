package org.academy.api.common.entitycontrol;

import java.util.UUID;

public interface ControlHandle extends AutoCloseable {
    UUID id();

    boolean isClosed();

    @Override
    void close();
}
