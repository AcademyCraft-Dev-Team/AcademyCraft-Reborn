package org.academy.api.common.entitycontrol;

public interface ControlBinding extends AutoCloseable {
    ControlBinding NOOP = new ControlBinding() {
        @Override
        public void tick() {
        }

        @Override
        public void close() {
        }
    };

    void tick();

    /**
     * Reasserts navigation after vanilla goals have selected their movement for this tick and
     * immediately before the subject navigation is advanced.
     */
    default void beforeNavigationTick() {
    }

    /**
     * Reasserts the already selected movement immediately before vanilla's move controller ticks.
     * Special mobs may replace navigation or movement state from {@code customServerAiStep}; this
     * hook must not advance retry counters or create a second path in the same game tick.
     */
    default void beforeMoveControlTick() {
    }

    /** Reasserts an active view directive immediately before the subject applies its look AI. */
    default void beforeLookControlTick() {
    }

    /**
     * Returns whether this binding has reached its natural terminal state. The runtime removes the
     * owning lease after the current tick when every active binding for that lease is complete.
     */
    default boolean isComplete() {
        return false;
    }

    /**
     * Returns the reason for an abnormal terminal state. An empty value means natural completion
     * or explicit cancellation.
     */
    default java.util.Optional<ControlFailureReason> failureReason() {
        return java.util.Optional.empty();
    }

    @Override
    void close();

    static ControlBinding noop() {
        return NOOP;
    }
}
