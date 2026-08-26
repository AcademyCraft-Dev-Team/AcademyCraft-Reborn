package org.academy.api.common.ability.darkmatter;

import java.util.Locale;

/**
 * Built-in shape catalogue. The GUI is populated from this catalogue instead of fixed buttons.
 */
public enum DarkmatterShape {
    TOOL(4.0f, 1, 1),
    SWORD(4.0f, 1, 1),
    SPEAR(4.0f, 1, 3),
    TRIDENT(6.0f, 1, 3),
    BOW(5.0f, 1, 4),
    CROSSBOW(6.0f, 1, 4),
    MACE(6.0f, 1, 5),
    ARROW(4.0f, 16, 4),
    ARMOR(16.0f, 4, 4),
    COATING(3.0f, 1, 5),
    BLOCK(8.0f, 1, 1);

    private final float baseMatterCost;
    private final int outputCount;
    private final int requiredAbilityLevel;

    DarkmatterShape(float baseMatterCost, int outputCount, int requiredAbilityLevel) {
        this.baseMatterCost = baseMatterCost;
        this.outputCount = outputCount;
        this.requiredAbilityLevel = Math.clamp(requiredAbilityLevel, 1, 5);
    }

    public String id() {
        return name().toLowerCase(Locale.ROOT);
    }

    public String translationKey() {
        return "screen.academy.darkmatter_shaping.shape." + id();
    }

    public float baseMatterCost() {
        return baseMatterCost;
    }

    public int outputCount() {
        return outputCount;
    }

    public int requiredAbilityLevel() {
        return requiredAbilityLevel;
    }

    public boolean isUnlockedAt(int abilityLevel) {
        return abilityLevel >= requiredAbilityLevel;
    }

    public boolean isArmor() {
        return this == ARMOR;
    }

    public boolean isAmmo() {
        return this == ARROW;
    }

    public boolean isRanged() {
        return this == BOW || this == CROSSBOW || this == TRIDENT || this == ARROW;
    }

    public boolean isOffensive() {
        return switch (this) {
            case TOOL, SWORD, SPEAR, TRIDENT, BOW, CROSSBOW, MACE, ARROW -> true;
            case ARMOR, COATING, BLOCK -> false;
        };
    }

    public boolean carriesActiveItemEffects() {
        return this != COATING && this != BLOCK;
    }

    public static DarkmatterShape byId(String id) {
        if (id == null) return TOOL;
        for (var value : values()) if (value.id().equals(id)) return value;
        return TOOL;
    }
}
