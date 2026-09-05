package org.academy.api.server.ability;

import org.academy.internal.server.config.DimensionEffectsConfig;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.academy.api.server.ability.AbilityEffectPolicy.Decision.*;
import static org.junit.jupiter.api.Assertions.assertEquals;

class AbilityEffectPolicyTest {
    @Test
    void disabledPolicyPreservesLegacyBehaviorRegardlessOfLists() {
        var config = new DimensionEffectsConfig();
        config.pvp.mode = DimensionEffectsConfig.Mode.WHITELIST;
        config.blockDestruction.dimensions.add("minecraft:overworld");
        assertEquals(DEFAULT, AbilityEffectPolicy.decide(config, "minecraft:overworld", true));
        assertEquals(DEFAULT, AbilityEffectPolicy.decide(config, "minecraft:overworld", false));
    }

    @Test
    void independentModesHandleVanillaAndModdedDimensions() {
        var config = new DimensionEffectsConfig();
        config.enabled = true;
        config.pvp.mode = DimensionEffectsConfig.Mode.WHITELIST;
        config.pvp.dimensions = Set.of("example:arena");
        config.blockDestruction.mode = DimensionEffectsConfig.Mode.BLACKLIST;
        config.blockDestruction.dimensions = Set.of("minecraft:overworld");
        assertEquals(ALLOW, AbilityEffectPolicy.decide(config, "example:arena", true));
        assertEquals(DENY, AbilityEffectPolicy.decide(config, "minecraft:the_nether", true));
        assertEquals(DENY, AbilityEffectPolicy.decide(config, "minecraft:overworld", false));
        assertEquals(ALLOW, AbilityEffectPolicy.decide(config, "minecraft:the_nether", false));
    }

    @Test
    void emptyWhitelistDeniesAllAndEmptyBlacklistAllowsAll() {
        var config = new DimensionEffectsConfig();
        config.enabled = true;
        config.pvp.mode = DimensionEffectsConfig.Mode.WHITELIST;
        assertEquals(DENY, AbilityEffectPolicy.decide(config, "minecraft:overworld", true));
        assertEquals(ALLOW, AbilityEffectPolicy.decide(config, "minecraft:overworld", false));
    }
}
