package org.academy.api.common.damage;

import net.minecraft.resources.ResourceKey;
import net.minecraft.world.damagesource.DamageType;
import java.util.Objects;

/** Immutable code registration; the referenced DamageType must also exist in the world's data pack. */
public record AbilityDamageProfile(ResourceKey<DamageType> damageType, DamageSettlement settlement,
                                   boolean bypassAbsorption, int compatibilityVersion) {
    public AbilityDamageProfile {
        Objects.requireNonNull(damageType, "damageType");
        Objects.requireNonNull(settlement, "settlement");
        if (compatibilityVersion < 1) throw new IllegalArgumentException("Compatibility version must be positive");
    }

    public static Builder builder(ResourceKey<DamageType> damageType) {
        return new Builder(damageType);
    }

    public static final class Builder {
        private final ResourceKey<DamageType> type;
        private DamageSettlement settlement = DamageSettlement.STANDARD;
        private boolean bypassAbsorption;
        private int version = 1;

        private Builder(ResourceKey<DamageType> type) { this.type = Objects.requireNonNull(type); }
        public Builder settlement(DamageSettlement value) { settlement = Objects.requireNonNull(value); return this; }
        public Builder bypassAbsorption(boolean value) { bypassAbsorption = value; return this; }
        public Builder compatibilityVersion(int value) { version = value; return this; }
        public AbilityDamageProfile build() { return new AbilityDamageProfile(type, settlement, bypassAbsorption, version); }
    }
}
