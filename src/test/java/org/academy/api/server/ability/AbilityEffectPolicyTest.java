package org.academy.api.server.ability;

import org.academy.internal.server.config.DimensionEffectsConfig;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;

import static org.academy.api.server.ability.AbilityEffectPolicy.Decision.*;
import static org.junit.jupiter.api.Assertions.assertEquals;

class AbilityEffectPolicyTest {
    @Test
    void disabledPolicyPreservesLegacyBehaviorRegardlessOfLists() {
        var config = new DimensionEffectsConfig();
        config.pvp.whitelist.add("minecraft:overworld");
        config.blockDestruction.blacklist.add("minecraft:overworld");
        assertEquals(DEFAULT, AbilityEffectPolicy.decide(config, "minecraft:overworld", true));
        assertEquals(DEFAULT, AbilityEffectPolicy.decide(config, "minecraft:overworld", false));
    }

    @Test
    void simultaneousListsResolveEachEffectIndependently() {
        var config = new DimensionEffectsConfig();
        config.enabled = true;
        config.pvp.whitelist = Set.of("example:arena");
        config.pvp.blacklist = Set.of("minecraft:overworld");
        config.blockDestruction.whitelist = Set.of("minecraft:overworld");
        config.blockDestruction.blacklist = Set.of("example:arena");
        assertEquals(ALLOW, AbilityEffectPolicy.decide(config, "example:arena", true));
        assertEquals(DENY, AbilityEffectPolicy.decide(config, "minecraft:overworld", true));
        assertEquals(DENY, AbilityEffectPolicy.decide(config, "example:arena", false));
        assertEquals(ALLOW, AbilityEffectPolicy.decide(config, "minecraft:overworld", false));
        assertEquals(DEFAULT, AbilityEffectPolicy.decide(config, "minecraft:the_nether", true));
        assertEquals(DEFAULT, AbilityEffectPolicy.decide(config, "minecraft:the_nether", false));
    }

    @Test
    void blacklistWinsWhenADimensionAppearsInBothLists() {
        var config = new DimensionEffectsConfig();
        config.enabled = true;
        config.pvp.whitelist = config.pvp.blacklist = Set.of("example:arena");
        config.blockDestruction.whitelist = config.blockDestruction.blacklist = Set.of("example:arena");
        assertEquals(DENY, AbilityEffectPolicy.decide(config, "example:arena", true));
        assertEquals(DENY, AbilityEffectPolicy.decide(config, "example:arena", false));
    }

    @Test
    void emptyListsPreservePlayerSwitches() {
        var config = new DimensionEffectsConfig();
        config.enabled = true;
        assertEquals(DEFAULT, AbilityEffectPolicy.decide(config, "minecraft:overworld", true));
        assertEquals(DEFAULT, AbilityEffectPolicy.decide(config, "minecraft:overworld", false));
    }

    @Test
    void pvpOnlyConfigLeavesBlockSwitchesIndependent(@TempDir Path directory) throws Exception {
        var file = directory.resolve(DimensionEffectsConfig.FILE_NAME);
        for (var blockRule : new String[]{
                "",
                ", \"blockDestruction\": {}",
                ", \"blockDestruction\": {\"whitelist\": [], \"blacklist\": []}"
        }) {
            Files.writeString(file, """
                    {"enabled": true, "pvp": {"whitelist": ["example:arena"]}
                    """ + blockRule + "}");
            var config = DimensionEffectsConfig.load(file);
            assertEquals(ALLOW, AbilityEffectPolicy.decide(config, "example:arena", true));
            assertEquals(DEFAULT, AbilityEffectPolicy.decide(config, "minecraft:overworld", true));
            assertEquals(DEFAULT, AbilityEffectPolicy.decide(config, "example:arena", false));
            assertEquals(DEFAULT, AbilityEffectPolicy.decide(config, "minecraft:overworld", false));
        }
    }

    @Test
    void blockOnlyConfigLeavesPvpSwitchesIndependent(@TempDir Path directory) throws Exception {
        var file = directory.resolve(DimensionEffectsConfig.FILE_NAME);
        Files.writeString(file, """
                {"enabled": true, "blockDestruction": {"blacklist": ["minecraft:overworld"]}}
                """);
        var config = DimensionEffectsConfig.load(file);
        assertEquals(DENY, AbilityEffectPolicy.decide(config, "minecraft:overworld", false));
        assertEquals(DEFAULT, AbilityEffectPolicy.decide(config, "example:arena", false));
        assertEquals(DEFAULT, AbilityEffectPolicy.decide(config, "minecraft:overworld", true));
        assertEquals(DEFAULT, AbilityEffectPolicy.decide(config, "example:arena", true));
    }

    @Test
    void legacyPvpWhitelistDoesNotForceOtherDimensionsOrBlockDestruction(@TempDir Path directory) throws Exception {
        var file = directory.resolve(DimensionEffectsConfig.FILE_NAME);
        Files.writeString(file, """
                {
                  "enabled": true,
                  "pvp": {"mode": "WHITELIST", "dimensions": ["example:arena"]},
                  "blockDestruction": {"mode": "BLACKLIST", "dimensions": []}
                }
                """);
        var config = DimensionEffectsConfig.load(file);
        assertEquals(ALLOW, AbilityEffectPolicy.decide(config, "example:arena", true));
        assertEquals(DEFAULT, AbilityEffectPolicy.decide(config, "minecraft:overworld", true));
        assertEquals(DEFAULT, AbilityEffectPolicy.decide(config, "example:arena", false));
        assertEquals(DEFAULT, AbilityEffectPolicy.decide(config, "minecraft:overworld", false));
    }
}
