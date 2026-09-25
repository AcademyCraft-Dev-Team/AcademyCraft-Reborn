package org.academy.internal.server.config;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonParser;
import org.academy.api.server.ability.SkillTuning;
import org.academy.internal.server.ability.SkillTuningCore;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class AbilityConfigTest {
    @Test
    void paralysisDamageIsConfigurableAndUsesTheLargerTerm() {
        var settings = new AbilityConfig.ElectromasterSettings();
        assertEquals(2.0f, settings.paralysisDamage(20));
        assertEquals(2.0f, settings.paralysisDamage(200));
        assertEquals(10.0f, settings.paralysisDamage(1000));
        settings.paralysisMinimumDamage = 3;
        settings.paralysisMaxHealthFraction = 0.02f;
        assertEquals(3.0f, settings.paralysisDamage(100));
        assertEquals(20.0f, settings.paralysisDamage(1000));
        settings.paralysisDamageEnabled = false;
        assertEquals(0.0f, settings.paralysisDamage(1000));
    }

    @Test
    void paralysisSettingsRoundTripAndOldFilesReceiveDefaults() {
        var gson = new GsonBuilder().create();
        var adapter = AbilityConfig.Action.INSTANCE.getAdapter(gson);
        var defaults = adapter.fromJsonTree(JsonParser.parseString("{}"));
        assertTrue(defaults.electromaster.paralysisDamageEnabled);
        assertEquals(10.0f, defaults.electromaster.paralysisDamage(1000));
        defaults.electromaster.paralysisMinimumDamage = 5;
        defaults.electromaster.paralysisMaxHealthFraction = 0.03f;
        defaults.electromaster.paralysisDamageEnabled = false;
        var tree = adapter.toJsonTree(defaults);
        assertTrue(tree.getAsJsonObject().has("electromaster"));
        var restored = adapter.fromJsonTree(tree).electromaster;
        assertFalse(restored.paralysisDamageEnabled);
        assertEquals(5, restored.paralysisMinimumDamage);
        assertEquals(0.03f, restored.paralysisMaxHealthFraction);
    }

    @Test
    void invalidParalysisNumbersCannotProduceInvalidDamage() {
        var settings = new AbilityConfig.ElectromasterSettings();
        settings.paralysisMinimumDamage = Float.NaN;
        settings.paralysisMaxHealthFraction = Float.POSITIVE_INFINITY;
        assertEquals(10.0f, settings.paralysisDamage(1000));
        settings.paralysisMinimumDamage = -2;
        settings.paralysisMaxHealthFraction = -1;
        assertEquals(0.0f, settings.paralysisDamage(1000));
    }

    @Test
    void defaultsSingleBeamAttackDelayToReferenceTiming() {
        var config = AbilityConfig.Action.INSTANCE.getDefault();
        var settings = config.skillSettings("single_high_speed_electron_beam");

        assertEquals(10.0f, settings.floatMap.get("attackDelayTicks"));
    }

    @Test
    void mentaloutPlayerControlDefaultsRemainBackwardCompatible() {
        var settings = AbilityConfig.Action.INSTANCE.getDefault().mentalout;

        assertTrue(settings.allowPlayerRoster);
        assertTrue(settings.allowMentalTakeover);
        assertEquals(100.0f, settings.mentalTakeoverOccupation);
        assertEquals(3.0f, settings.playerControlCostMultiplier);
    }

    @Test
    void aeromanipResourceDefaultsMatchEffectBasedConsumption() {
        var config = AbilityConfig.Action.INSTANCE.getDefault();

        assertEquals(2.0f, config.skillSettings("pneumatic_grasp")
                .floatMap.get("compressedAirPerInterval"));
        assertEquals(8.0f, config.skillSettings("atmosphere_shield")
                .floatMap.get("compressedAirPerEffect"));
    }

    @Test
    void everySkillDefaultsToUnchangedNumbers() {
        var config = AbilityConfig.Action.INSTANCE.getDefault();

        assertEquals(1.0f, config.damageMultiplier);
        assertTrue(SkillTuningCore.globalDamageMultiplier(config) == 1.0f);
        // A skill with no entry must still resolve to neutral values.
        assertEquals(1.0f, SkillTuningCore.damageMultiplier(config, "vector_blast"));
        assertEquals(1.0f, SkillTuningCore.rangeMultiplier(config, "vector_blast"));
        assertEquals(1.0f, SkillTuningCore.costMultiplier(config, "vector_blast"));
        assertEquals(-1, SkillTuningCore.iterationTicks(config, "vector_blast", -1));
        assertEquals(-1, SkillTuningCore.maxStacks(config, "vector_blast", -1));
        assertTrue(SkillTuningCore.isSkillEnabled(config, "vector_blast"));
        assertTrue(SkillTuningCore.allowBlockDestruction(config, "vector_blast"));
    }

    @Test
    void defaultsGroupSkillsByAbilityCategory() {
        var config = AbilityConfig.Action.INSTANCE.getDefault();

        // Pre-seeded skills land in the group named after their ability category.
        assertTrue(config.group("aeromanip").containsKey("flight"));
        assertTrue(config.group("teleport").containsKey("disarm"));
        assertTrue(config.group("meltdowner").containsKey("single_high_speed_electron_beam"));
        assertNotNull(config.skillSettings("flight"));
        assertNotNull(config.skillSettings("disarm"));
    }

    @Test
    void seededSkillIsReusedNotDuplicated() {
        var config = new AbilityConfig();
        var first = config.seed("disarm");
        var second = config.seed("disarm");

        assertTrue(first == second);
        assertEquals(1, config.group("teleport").size());
    }

    @Test
    void perSkillTuningMultipliesGlobalDamage() {
        var config = AbilityConfig.Action.INSTANCE.getDefault();
        var settings = new AbilityConfig.SkillSettings();
        settings.damageMultiplier = 2.0f;
        config.ensureSkill("electromaster", "railgun").damageMultiplier = 2.0f;
        config.damageMultiplier = 1.5f;

        assertEquals(3.0f, SkillTuningCore.damageMultiplier(config, "railgun"));
        // Global scalar still applies to skills without their own entry.
        assertEquals(1.5f, SkillTuningCore.damageMultiplier(config, "vector_blast"));
    }

    @Test
    void perSkillIterationAndStacksOverrideBuilderDefaults() {
        var config = AbilityConfig.Action.INSTANCE.getDefault();
        var settings = config.ensureSkill("electromaster", "railgun");
        settings.iterationTicks = 7;
        settings.maxStacks = 4;

        assertEquals(7, SkillTuningCore.iterationTicks(config, "railgun", 20));
        assertEquals(4, SkillTuningCore.maxStacks(config, "railgun", 20));
        // Unset entries keep the skill's own value.
        assertEquals(20, SkillTuningCore.iterationTicks(config, "vector_blast", 20));
    }

    @Test
    void legacyAeromanipIdStillFindsItsRenamedEntry() {
        var config = AbilityConfig.Action.INSTANCE.getDefault();
        config.seed("laminar_buffer").rangeMultiplier = 1.5f;

        assertEquals(1.5f, SkillTuningCore.rangeMultiplier(config, "air_cushion"));
    }

    @Test
    void advancedBlockDestructionDefaultsOnAndCanBeDisabled() {
        var config = AbilityConfig.Action.INSTANCE.getDefault();
        assertTrue(SkillTuningCore.allowBlockDestruction(config, "railgun"));

        config.seed("railgun").advanced.blockDestruction = false;
        assertFalse(SkillTuningCore.allowBlockDestruction(config, "railgun"));
    }

    @Test
    void disarmPlayersDefaultsOffAndCanBeEnabled() {
        var config = AbilityConfig.Action.INSTANCE.getDefault();

        // Player disarm is server opt-in; the default must be off.
        assertFalse(SkillTuningCore.allowDisarmPlayers(config, "disarm"));

        config.seed("disarm").advanced.disarmPlayers = true;
        assertTrue(SkillTuningCore.allowDisarmPlayers(config, "disarm"));
    }

    @Test
    void maxHealthDamageIsIndependentOfThePlainDamageMultiplier() {
        var config = AbilityConfig.Action.INSTANCE.getDefault();
        var settings = config.seed("thunderclap");
        settings.damageMultiplier = 4.0f;
        settings.maxHealthDamageMultiplier = 2.0f;
        config.damageMultiplier = 3.0f;

        // Plain damage gets both the skill and global multiplier...
        assertEquals(12.0f, SkillTuningCore.damageMultiplier(config, "thunderclap"));
        // ...but percentage max-health damage only gets its dedicated multiplier.
        assertEquals(2.0f, SkillTuningCore.maxHealthDamageMultiplier(config, "thunderclap"));
        assertEquals(20.0f, SkillTuningCore.scaleMaxHealthDamage(config, "thunderclap", 10.0f));
    }

    @Test
    void maxHealthDamageGateZeroesOnlyThePercentageTerm() {
        var config = AbilityConfig.Action.INSTANCE.getDefault();
        var settings = config.seed("mind_destruction");
        settings.advanced.maxHealthDamage = false;
        settings.maxHealthDamageMultiplier = 2.0f;

        assertFalse(SkillTuningCore.allowsMaxHealthDamage(config, "mind_destruction"));
        assertEquals(0.0f, SkillTuningCore.scaleMaxHealthDamage(config, "mind_destruction", 10.0f));
    }

    @Test
    void maxHealthDefaultsAreNeutralAndSeeded() {
        var config = AbilityConfig.Action.INSTANCE.getDefault();

        assertEquals(1.0f, SkillTuningCore.maxHealthDamageMultiplier(config, "thunderclap"));
        assertTrue(SkillTuningCore.allowsMaxHealthDamage(config, "thunderclap"));
        assertEquals(7.5f, SkillTuningCore.scaleMaxHealthDamage(config, "thunderclap", 7.5f));

        // Skills with percentage damage are surfaced in the defaults for discoverability.
        for (var path : SkillTuning.maxHealthDamageSkillIds()) {
            assertNotNull(config.skillSettings(path), "Missing seeded max-health entry: " + path);
        }
    }

    @Test
    void legacyFlatSkillsMapMigratesIntoCategoryGroups() {
        var json = """
                {
                  "skills": {
                    "flight": { "floatMap": { "compressedAirPerInterval": 2.0 } },
                    "railgun": { "damageMultiplier": 1.5 }
                  }
                }
                """;
        var adapter = AbilityConfig.Action.INSTANCE.getAdapter(new GsonBuilder().create());
        var config = adapter.fromJsonTree(JsonParser.parseString(json));

        // Flat entries are regrouped by category and remain resolvable.
        assertNotNull(config.skillSettings("flight"));
        assertNotNull(config.skillSettings("railgun"));
        assertEquals(2.0f, config.skillSettings("flight")
                .floatMap.get("compressedAirPerInterval"));
        assertEquals(1.5f, config.skillSettings("railgun").damageMultiplier);
    }

    @Test
    void legacyFloatMapValuesMigrateIntoTypedFields() {
        var json = """
                {
                  "skills": {
                    "aeromanip": {
                      "airflow_jet": {
                        "floatMap": {
                          "damageMultiplier": 1.75,
                          "rangeMultiplier": 0.5,
                          "cpMultiplier": 2.0
                        }
                      }
                    }
                  }
                }
                """;
        var adapter = AbilityConfig.Action.INSTANCE.getAdapter(new GsonBuilder().create());
        var config = adapter.fromJsonTree(JsonParser.parseString(json));

        var settings = config.skillSettings("airflow_jet");
        assertEquals(1.75f, settings.damageMultiplier);
        assertEquals(0.5f, settings.rangeMultiplier);
        assertEquals(2.0f, settings.costMultiplier);
        assertFalse(settings.floatMap.containsKey("damageMultiplier"));
        assertFalse(settings.floatMap.containsKey("cpMultiplier"));
    }

    @Test
    void serializedDefaultsOmitDeadLegacySections() {
        var config = AbilityConfig.Action.INSTANCE.getDefault();
        var json = new GsonBuilder().create().toJson(config);
        var root = JsonParser.parseString(json).getAsJsonObject();

        assertFalse(root.has("brainDevelopment"));
        assertTrue(root.has("damageMultiplier"));
        assertTrue(root.has("skills"));
    }
}
