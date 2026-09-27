package org.academy.api.common.entitycontrol;

import java.util.Optional;

public interface ControlBinding extends AutoCloseable {
    ControlBinding NOOP = new ControlBinding() {
        @Override
        public void tick() {
        }

        @Override
        public void close() {
        }
    };

    static ControlBinding noop() {
        return NOOP;
    }

    void tick();

    default void beforeNavigationTick() {
    }

    default void beforeMoveControlTick() {
    }

    default void beforeLookControlTick() {
    }

    default boolean isComplete() {
        return false;
    }

    default Optional<ControlFailureReason> failureReason() {
        return Optional.empty();
    }

    @Override
    void close();
}
