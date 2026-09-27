package org.academy.api.common.entitycontrol;

public enum ControlRejectionReason {
    SUPPORTED,
    IMMUNE_TAG,
    PROTECTED_PLAYER,
    NO_ADAPTER,
    UNSUPPORTED_CAPABILITY,
    TEMPORARILY_UNAVAILABLE,
    AMBIGUOUS_ADAPTER,
    INVALID_DIRECTIVE,
    ADAPTER_ERROR
}
