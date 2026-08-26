package org.academy.api.common.ability.darkmatter;

import java.util.*;

/**
 * Stable extension surface for shaped-item modifier metadata.
 */
public final class DarkmatterShapingRegistries {
    private static final Map<String, DarkmatterModifierType> MODIFIERS = new LinkedHashMap<>();

    private DarkmatterShapingRegistries() {
    }

    public static synchronized DarkmatterModifierType register(DarkmatterModifierType type) {
        if (MODIFIERS.putIfAbsent(type.id(), type) != null) {
            throw new IllegalArgumentException("Duplicate dark-matter modifier: " + type.id());
        }
        return type;
    }

    public static Optional<DarkmatterModifierType> modifier(String id) {
        DarkmatterModifiers.bootstrap();
        return Optional.ofNullable(MODIFIERS.get(id));
    }

    public static Collection<DarkmatterModifierType> modifiers() {
        DarkmatterModifiers.bootstrap();
        return List.copyOf(MODIFIERS.values());
    }
}
