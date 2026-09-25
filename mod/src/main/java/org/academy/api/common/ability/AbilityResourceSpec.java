package org.academy.api.common.ability;

import net.minecraft.util.Mth;

/**
 * Describes an optional category-specific resource with proportional or fixed capacity.
 */
public record AbilityResourceSpec(float capacityPerMaxCp, float occupiedCpPerUnit, int fixedCapacity) {
    private static final int PROPORTIONAL_CAPACITY = -1;

    public AbilityResourceSpec(float capacityPerMaxCp, float occupiedCpPerUnit) {
        this(capacityPerMaxCp, occupiedCpPerUnit, PROPORTIONAL_CAPACITY);
    }

    public AbilityResourceSpec {
        if (!Float.isFinite(capacityPerMaxCp) || capacityPerMaxCp < 0.0f) {
            throw new IllegalArgumentException("capacityPerMaxCp must be finite and non-negative");
        }
        if (!Float.isFinite(occupiedCpPerUnit) || occupiedCpPerUnit <= 0.0f) {
            throw new IllegalArgumentException("occupiedCpPerUnit must be finite and positive");
        }
        if (fixedCapacity < PROPORTIONAL_CAPACITY) {
            throw new IllegalArgumentException("fixedCapacity must be non-negative or proportional");
        }
    }

    /**
     * Creates a resource with a category-defined capacity that does not reserve CP per unit.
     */
    public static AbilityResourceSpec fixed(int capacity) {
        if (capacity < 0) throw new IllegalArgumentException("capacity must be non-negative");
        return new AbilityResourceSpec(0.0f, 1.0f, capacity);
    }

    public boolean hasFixedCapacity() {
        return fixedCapacity >= 0;
    }

    public int capacity(float maxCp) {
        if (hasFixedCapacity()) return fixedCapacity;
        if (!Float.isFinite(maxCp) || maxCp <= 0.0f) return 0;
        return Math.max(0, Mth.floor(maxCp * capacityPerMaxCp));
    }

    public float occupiedCp(float units) {
        if (!Float.isFinite(units) || units <= 0.0f) return 0.0f;
        return units * occupiedCpPerUnit;
    }
}
