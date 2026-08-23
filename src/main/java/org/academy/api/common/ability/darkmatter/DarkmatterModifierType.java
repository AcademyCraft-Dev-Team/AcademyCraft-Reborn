package org.academy.api.common.ability.darkmatter;

import java.util.Set;

/** Public metadata for one configurable dark-matter shaping modifier. */
public record DarkmatterModifierType(
        String id,
        int maxLevel,
        int pointCost,
        int requiredAbilityLevel,
        Set<DarkmatterShape> compatibleShapes,
        Set<String> conflicts
) {
    public DarkmatterModifierType {
        maxLevel = Math.max(1, maxLevel);
        pointCost = Math.max(1, pointCost);
        requiredAbilityLevel = Math.clamp(requiredAbilityLevel, 1, 5);
        compatibleShapes = Set.copyOf(compatibleShapes);
        conflicts = Set.copyOf(conflicts);
    }

    /** Source-compatible constructor for extensions whose modifiers are available from level 1. */
    public DarkmatterModifierType(String id, int maxLevel, int pointCost,
                                  Set<DarkmatterShape> compatibleShapes,
                                  Set<String> conflicts) {
        this(id, maxLevel, pointCost, 1, compatibleShapes, conflicts);
    }

    public boolean supports(DarkmatterShape shape) {
        return compatibleShapes.contains(shape);
    }

    public boolean isUnlockedAt(int abilityLevel) {
        return abilityLevel >= requiredAbilityLevel;
    }

    public String nameKey() {
        return "modifier.academy.darkmatter_shaping." + id;
    }

    public String descriptionKey() {
        return nameKey() + ".description";
    }
}
