package org.academy.api.common.entitycontrol;

/**
 * Observable lifecycle state of a mental-control session.
 */
public enum ControlState {
    PENDING,
    ACTIVE,
    PREEMPTED,
    COMPLETED,
    FAILED,
    CANCELLED,
    EXPIRED;

    public boolean isTerminal() {
        return this == COMPLETED || this == FAILED || this == CANCELLED || this == EXPIRED;
    }
}
