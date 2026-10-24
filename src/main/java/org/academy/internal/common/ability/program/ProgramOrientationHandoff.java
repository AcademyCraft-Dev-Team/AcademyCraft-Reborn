package org.academy.internal.common.ability.program;

import java.util.Objects;
import java.util.UUID;

/**
 * A token-scoped bridge between solver completion and the program's next scheduled poll.
 * It only retains an already valid lease. It never authorizes force, revives ownership, or
 * extends the original move/hover command. Inactive polls cannot renew this finite window.
 */
public final class ProgramOrientationHandoff {
    public static final int WINDOW_TICKS = 20;

    private final UUID controlId;
    private long until = Long.MIN_VALUE;

    public ProgramOrientationHandoff(UUID controlId) {
        this.controlId = Objects.requireNonNull(controlId, "controlId");
    }

    public boolean retain(long gameTime, UUID token, boolean programControl,
                          boolean exactLeaseActive, boolean angularOperationActive) {
        if (!controlId.equals(token) || !programControl || !exactLeaseActive) {
            until = Long.MIN_VALUE;
            return false;
        }
        if (angularOperationActive) {
            until = gameTime > Long.MAX_VALUE - WINDOW_TICKS ? Long.MAX_VALUE : gameTime + WINDOW_TICKS;
            return true;
        }
        return until != Long.MIN_VALUE && gameTime <= until;
    }
}
