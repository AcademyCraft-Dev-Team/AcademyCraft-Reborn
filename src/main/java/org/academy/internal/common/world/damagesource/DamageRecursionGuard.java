package org.academy.internal.common.world.damagesource;

import java.util.HashSet;
import java.util.Objects;
import java.util.Set;

/**
 * Prevents a synchronous damage callback from re-entering itself through another damage system.
 */
public final class DamageRecursionGuard {
    private static final ThreadLocal<Set<Object>> ACTIVE_KEYS = new ThreadLocal<>();

    private DamageRecursionGuard() {
    }

    public static boolean isActive(Object key) {
        var activeKeys = ACTIVE_KEYS.get();
        return key != null && activeKeys != null && activeKeys.contains(key);
    }

    /**
     * Runs {@code action} unless the same key is already active on this damage call stack.
     *
     * @return {@code true} when the action ran, or {@code false} when recursive execution was blocked
     */
    public static boolean runGuarded(Object key, Runnable action) {
        Objects.requireNonNull(key, "key");
        Objects.requireNonNull(action, "action");
        var activeKeys = ACTIVE_KEYS.get();
        if (activeKeys == null) {
            activeKeys = new HashSet<>();
            ACTIVE_KEYS.set(activeKeys);
        }
        if (!activeKeys.add(key)) return false;
        try {
            action.run();
            return true;
        } finally {
            activeKeys.remove(key);
            if (activeKeys.isEmpty()) ACTIVE_KEYS.remove();
        }
    }
}
