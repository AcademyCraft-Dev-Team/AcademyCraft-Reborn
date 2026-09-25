package org.academy.api.common.entitycontrol;

public enum ControlFailureReason {
    UNREACHABLE_DESTINATION,
    TARGET_UNAVAILABLE,
    CONTROL_RESISTANCE,
    UNSUPPORTED_MOVEMENT_MODE,
    PLANNING_BUDGET_EXHAUSTED,
    CLIENT_TIMEOUT,
    ADAPTER_ERROR
}
