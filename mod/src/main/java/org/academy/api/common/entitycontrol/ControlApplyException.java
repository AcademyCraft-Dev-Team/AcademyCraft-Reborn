package org.academy.api.common.entitycontrol;

import java.util.Objects;

public final class ControlApplyException extends IllegalArgumentException {
    private final ControlRejectionReason reason;
    private final ControlCapability capability;

    public ControlApplyException(
            ControlRejectionReason reason,
            ControlCapability capability,
            String message
    ) {
        super(message);
        this.reason = Objects.requireNonNull(reason, "reason");
        this.capability = Objects.requireNonNull(capability, "capability");
    }

    public ControlApplyException(
            ControlRejectionReason reason,
            ControlCapability capability,
            String message,
            Throwable cause
    ) {
        super(message, cause);
        this.reason = Objects.requireNonNull(reason, "reason");
        this.capability = Objects.requireNonNull(capability, "capability");
    }

    public ControlRejectionReason reason() {
        return reason;
    }

    public ControlCapability capability() {
        return capability;
    }
}
