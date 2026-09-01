package org.academy.api.common.entitycontrol;

import java.util.Optional;
import java.util.Set;
import java.util.UUID;

public interface ControlHandle extends AutoCloseable {
    UUID id();

    boolean isClosed();

    /**
     * Returns the current lifecycle state. A live but overridden handle is {@link ControlState#PREEMPTED}.
     */
    default ControlState state() {
        return isClosed() ? ControlState.CANCELLED : ControlState.ACTIVE;
    }

    /**
     * Returns the domains this handle currently owns after atomic arbitration.
     */
    default Set<ControlDomain> effectiveDomains() {
        return Set.of();
    }

    default boolean isEffective() {
        return state() == ControlState.ACTIVE;
    }

    default Optional<ControlFailureReason> failureReason() {
        return Optional.empty();
    }

    @Override
    void close();
}
