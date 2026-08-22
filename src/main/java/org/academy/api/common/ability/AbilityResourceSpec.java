package org.academy.api.common.ability;

import net.minecraft.util.Mth;

/**
 * Describes an optional category-specific resource derived from a player's CP limit.
 */
public record AbilityResourceSpec(float capacityPerMaxCp, float occupiedCpPerUnit) {
    public AbilityResourceSpec {
        if (!Float.isFinite(capacityPerMaxCp) || capacityPerMaxCp < 0.0f) {
            throw new IllegalArgumentException("capacityPerMaxCp must be finite and non-negative");
        }
        if (!Float.isFinite(occupiedCpPerUnit) || occupiedCpPerUnit <= 0.0f) {
            throw new IllegalArgumentException("occupiedCpPerUnit must be finite and positive");
        }
    }

    public int capacity(float maxCp) {
        if (!Float.isFinite(maxCp) || maxCp <= 0.0f) return 0;
        return Math.max(0, Mth.floor(maxCp * capacityPerMaxCp));
    }

    public float occupiedCp(float units) {
        if (!Float.isFinite(units) || units <= 0.0f) return 0.0f;
        return units * occupiedCpPerUnit;
    }
}
