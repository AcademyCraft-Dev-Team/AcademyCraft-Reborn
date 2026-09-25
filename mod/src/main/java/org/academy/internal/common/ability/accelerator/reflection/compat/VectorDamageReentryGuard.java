package org.academy.internal.common.ability.accelerator.reflection.compat;

import java.util.HashSet;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.function.Supplier;

/**
 * Tracks targets whose reflected damage call has not returned yet.
 *
 * <p>Third-party damage callbacks may retaliate synchronously. If that retaliation is reflected
 * back to the same target, applying it immediately would nest another damage pipeline inside the
 * target's current {@code LivingDamageEvent.Pre} dispatch.</p>
 */
final class VectorDamageReentryGuard {
    private final Set<UUID> activeTargets = new HashSet<>();

    boolean isActive(UUID targetId) {
        return activeTargets.contains(targetId);
    }

    <T> T run(UUID targetId, Supplier<T> action) {
        Objects.requireNonNull(targetId, "targetId");
        Objects.requireNonNull(action, "action");
        if (!activeTargets.add(targetId)) {
            throw new IllegalStateException("Reflected damage target is already active: " + targetId);
        }
        try {
            return action.get();
        } finally {
            activeTargets.remove(targetId);
        }
    }
}
