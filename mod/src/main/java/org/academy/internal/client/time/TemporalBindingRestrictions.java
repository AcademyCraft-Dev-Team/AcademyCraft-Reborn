package org.academy.internal.client.time;

import java.util.HashMap;
import java.util.Map;

/**
 * Retains legacy external disable requests separately from their immune effective state.
 */
public final class TemporalBindingRestrictions {
    private final Map<String, Boolean> previous = new HashMap<>();

    public boolean externalWrite(String key, boolean enabled, boolean current, boolean immune) {
        if (enabled) {
            var original = previous.remove(key);
            return original == null || original;
        }
        var original = previous.computeIfAbsent(key, ignored -> current);
        return immune && original;
    }

    public void ownedWrite(String key) {
        previous.remove(key);
    }

    public Map<String, Boolean> effectiveStates(boolean immune) {
        var result = new HashMap<String, Boolean>();
        previous.forEach((key, original) -> result.put(key, immune && original));
        return result;
    }

    public Map<String, Boolean> clear() {
        var restored = Map.copyOf(previous);
        previous.clear();
        return restored;
    }
}
