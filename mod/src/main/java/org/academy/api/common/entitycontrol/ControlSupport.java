package org.academy.api.common.entitycontrol;

public enum ControlSupport {
    FULL,
    BEST_EFFORT,
    UNSUPPORTED;

    public boolean isSupported() {
        return this != UNSUPPORTED;
    }
}
