package org.academy.api.server.ability;

/** Server-owned effect session. tick returns false on completion; stop must be idempotent. */
public interface AbilityExecutionHandle {
    boolean tick();
    void stop();
}
