package org.academy.api.common.entitycontrol;

import java.util.UUID;
import java.util.Optional;

public interface ControlHandle extends AutoCloseable {
    UUID id();

    boolean isClosed();

    default Optional<ControlFailureReason> failureReason() {
        return Optional.empty();
    }

    @Override
    void close();
}
