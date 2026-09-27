package org.academy.api.client.input;

import org.jspecify.annotations.Nullable;

import java.util.Map;
import java.util.Objects;

/**
 * Public access to server-owned temporal input restrictions. The implementation registers the
 * bridge once; until then, no restrictions are applied.
 */
public final class TemporalInputAccess {
    /**
     * Implementation-provided bridge to the temporal input runtime.
     */
    public interface Bridge {
        boolean isLocalExternallyImmune();

        boolean isExternalControl();

        void ownedWrite(String keyName);

        boolean externalWrite(String keyName, boolean enabled, boolean current, boolean immune);

        Map<String, Boolean> effectiveStates(boolean immune);

        Map<String, Boolean> clear();
    }

    private static volatile @Nullable Bridge bridge;

    private TemporalInputAccess() {
    }

    public static void registerBridge(Bridge bridge) {
        TemporalInputAccess.bridge = Objects.requireNonNull(bridge);
    }

    public static boolean isLocalExternallyImmune() {
        var current = bridge;
        return current != null && current.isLocalExternallyImmune();
    }

    public static boolean isExternalControl() {
        var current = bridge;
        return current != null && current.isExternalControl();
    }

    public static void ownedWrite(String keyName) {
        var current = bridge;
        if (current != null) current.ownedWrite(keyName);
    }

    public static boolean externalWrite(String keyName, boolean enabled, boolean current, boolean immune) {
        var active = bridge;
        return active != null && active.externalWrite(keyName, enabled, current, immune);
    }

    public static Map<String, Boolean> effectiveStates(boolean immune) {
        var current = bridge;
        return current == null ? Map.of() : current.effectiveStates(immune);
    }

    public static Map<String, Boolean> clear() {
        var current = bridge;
        return current == null ? Map.of() : current.clear();
    }
}
