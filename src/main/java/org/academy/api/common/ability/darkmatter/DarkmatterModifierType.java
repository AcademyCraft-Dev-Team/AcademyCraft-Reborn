package org.academy.api.common.ability.darkmatter;

import java.util.Set;

/** Public metadata for one Tinkers-style dark-matter modifier. */
public record DarkmatterModifierType(
        String id,
        int maxLevel,
        int pointCost,
        Set<DarkmatterShape> compatibleShapes,
        Set<String> conflicts
) {
    public DarkmatterModifierType {
        maxLevel = Math.max(1, maxLevel);
        pointCost = Math.max(1, pointCost);
        compatibleShapes = Set.copyOf(compatibleShapes);
        conflicts = Set.copyOf(conflicts);
    }

    public boolean supports(DarkmatterShape shape) {
        return compatibleShapes.contains(shape);
    }

    public String nameKey() {
        return "modifier.academy.darkmatter_shaping." + id;
    }

    public String descriptionKey() {
        return nameKey() + ".description";
    }
}
