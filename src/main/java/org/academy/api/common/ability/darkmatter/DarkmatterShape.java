package org.academy.api.common.ability.darkmatter;

import java.util.Locale;

/** Built-in shape catalogue. The GUI is populated from this catalogue instead of fixed buttons. */
public enum DarkmatterShape {
    TOOL(4.0f, 1),
    SWORD(4.0f, 1),
    SPEAR(4.0f, 1),
    TRIDENT(6.0f, 1),
    BOW(5.0f, 1),
    CROSSBOW(6.0f, 1),
    MACE(6.0f, 1),
    ARROW(4.0f, 16),
    ARMOR(16.0f, 4);

    private final float baseMatterCost;
    private final int outputCount;

    DarkmatterShape(float baseMatterCost, int outputCount) {
        this.baseMatterCost = baseMatterCost;
        this.outputCount = outputCount;
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
        return this != ARMOR;
    }

    public static DarkmatterShape byId(String id) {
        if (id == null) return TOOL;
        for (var value : values()) if (value.id().equals(id)) return value;
        return TOOL;
    }
}
