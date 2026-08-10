package org.academy.internal.server.config;

import com.google.gson.annotations.SerializedName;
import org.academy.api.common.gson.TypeHandler;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class AbilityConfig {
    public static final String KEY = "ability";

    @SerializedName("metalEntities")
    public final Map<String, List<String>> metalEntities = new HashMap<>();
    @SerializedName("metalBlocks")
    public final Map<String, List<String>> metalBlocks = new HashMap<>();
    @SerializedName("skills")
    public final Map<String, SkillSettings> skills = new HashMap<>();

    @SerializedName("damageMultiplier")
    public float damageMultiplier = 1.0f;
    @SerializedName("cpRatingOffset")
    public float cpRatingOffset = 0.0f;
    @SerializedName("brainDevelopment")
    public final BrainDevelopmentSettings brainDevelopment = new BrainDevelopmentSettings();
    @SerializedName("aeromanip")
    public final AeromanipSettings aeromanip = new AeromanipSettings();
    @SerializedName("mentalout")
    public final MentaloutSettings mentalout = new MentaloutSettings();
    @SerializedName("proficiency")
    public final ProficiencySettings proficiency = new ProficiencySettings();

    public static class ProficiencySettings {
        @SerializedName("enabled")
        public boolean enabled = true;
        @SerializedName("allowMiningBeamSmelting")
        public boolean allowMiningBeamSmelting = true;
        @SerializedName("allowAreaTeleportTransforms")
        public boolean allowAreaTeleportTransforms = true;
        @SerializedName("allowAreaTeleportSwap")
        public boolean allowAreaTeleportSwap = true;
        @SerializedName("allowMentalTakeoverExtendedControls")
        public boolean allowMentalTakeoverExtendedControls = true;
        @SerializedName("maxAreaTeleportAxis")
        public int maxAreaTeleportAxis = 40;
        @SerializedName("maxCapturedProjectiles")
        public int maxCapturedProjectiles = 16;
        @SerializedName("maxBonusEntitiesPerTick")
        public int maxBonusEntitiesPerTick = 96;
    }

    public static class AeromanipSettings {
        @SerializedName("pvpForceMultiplier")
        public float pvpForceMultiplier = 0.5f;
        @SerializedName("pvpControlDurationMultiplier")
        public float pvpControlDurationMultiplier = 0.4f;
        @SerializedName("maxPlacedFieldsPerPlayer")
        public int maxPlacedFieldsPerPlayer = 1;
        @SerializedName("allowSoftBlockInteraction")
        public boolean allowSoftBlockInteraction = true;
    }

    public static class MentaloutSettings {
        @SerializedName("allowPlayerRoster")
        public boolean allowPlayerRoster = true;
        @SerializedName("allowMentalTakeover")
        public boolean allowMentalTakeover = true;
        @SerializedName("mentalInterventionCost")
        public float mentalInterventionCost = 10.0f;
        @SerializedName("targetMisidentificationCost")
        public float targetMisidentificationCost = 40.0f;
        @SerializedName("mentalStuporCostPerTarget")
        public float mentalStuporCostPerTarget = 30.0f;
        @SerializedName("impressionManipulationCostPerTarget")
        public float impressionManipulationCostPerTarget = 20.0f;
        @SerializedName("precisionPathCostPerTarget")
        public float precisionPathCostPerTarget = 10.0f;
        @SerializedName("precisionViewCostPerTarget")
        public float precisionViewCostPerTarget = 8.0f;
        @SerializedName("precisionGuardCostPerTarget")
        public float precisionGuardCostPerTarget = 20.0f;
        @SerializedName("bossCostMultiplier")
        public float bossCostMultiplier = 2.0f;
        @SerializedName("playerControlCostMultiplier")
        public float playerControlCostMultiplier = 3.0f;
        @SerializedName("mentalTakeoverOccupation")
        public float mentalTakeoverOccupation = 100.0f;
        @SerializedName("playerControlResistanceTicks")
        public int playerControlResistanceTicks = 400;
        @SerializedName("mentalIntrusionMaintenanceCost")
        public float mentalIntrusionMaintenanceCost = 20.0f;
        @SerializedName("sensoryDistortionMaintenanceCost")
        public float sensoryDistortionMaintenanceCost = 30.0f;
        @SerializedName("mentalIntrusionRange")
        public float mentalIntrusionRange = 16.0f;
        @SerializedName("mentalIntrusionMaxDistance")
        public float mentalIntrusionMaxDistance = 96.0f;
        @SerializedName("playerIntrusionMaxTicks")
        public int playerIntrusionMaxTicks = 100;
        @SerializedName("playerIntrusionCooldownTicks")
        public int playerIntrusionCooldownTicks = 200;
    }

    public static class BrainDevelopmentSettings {
        @SerializedName("level1MaxCpBonus")
        public float level1MaxCpBonus = 20.0f;
        @SerializedName("level1RecoveryBonus")
        public float level1RecoveryBonus = 1.0f;
        @SerializedName("level2EfficiencyBonus")
        public float level2EfficiencyBonus = 0.05f;
        @SerializedName("level3MaxCpBonus")
        public float level3MaxCpBonus = 100.0f;
        @SerializedName("level3RecoveryBonus")
        public float level3RecoveryBonus = 5.0f;
        @SerializedName("level4EfficiencyBonus")
        public float level4EfficiencyBonus = 0.15f;
        @SerializedName("level5MaxCpBonus")
        public float level5MaxCpBonus = 500.0f;
        @SerializedName("level5RecoveryBonus")
        public float level5RecoveryBonus = 25.0f;
        @SerializedName("level5EfficiencyBonus")
        public float level5EfficiencyBonus = 0.30f;
    }

    public static class SkillSettings {
        @SerializedName("booleanMap")
        public final Map<String, Boolean> booleanMap = new HashMap<>();

        @SerializedName("floatMap")
        public final Map<String, Float> floatMap = new HashMap<>();
    }

    public static class Action implements TypeHandler<AbilityConfig> {
        public static final TypeHandler<AbilityConfig> INSTANCE = new Action();

        private Action() {
        }

        @Override
        public AbilityConfig getDefault() {
            var defaultConfig = new AbilityConfig();

            List<String> minecraftMetalBlocks = new ArrayList<>();
            minecraftMetalBlocks.add("iron_block");
            minecraftMetalBlocks.add("iron_bars");
            minecraftMetalBlocks.add("iron_trapdoor");
            minecraftMetalBlocks.add("gold_block");
            List<String> academyMetalBlocks = new ArrayList<>();
            academyMetalBlocks.add("machine_frame");
            defaultConfig.metalBlocks.put("minecraft", minecraftMetalBlocks);
            defaultConfig.metalBlocks.put("academy", academyMetalBlocks);

            List<String> minecraftMetalEntities = new ArrayList<>();
            minecraftMetalEntities.add("iron_golem");
            List<String> academyMetalEntities = new ArrayList<>();
            academyMetalEntities.add("mag_hook");
            defaultConfig.metalEntities.put("minecraft", minecraftMetalEntities);
            defaultConfig.metalEntities.put("academy", academyMetalEntities);

            var railgunSettings = new SkillSettings();
            railgunSettings.booleanMap.put("enabled", true);
            railgunSettings.booleanMap.put("destroyBlock", true);
            railgunSettings.floatMap.put("damageScale", 1.0f);
            railgunSettings.floatMap.put("cpConsumeSpeed", 1.0f);
            railgunSettings.floatMap.put("overloadConsumeSpeed", 1.0f);
            railgunSettings.floatMap.put("exp_incr_speed", 1.0f);
            defaultConfig.skills.put("railgun", railgunSettings);

            var singleBeamSettings = new SkillSettings();
            singleBeamSettings.floatMap.put("attackDelayTicks", 10.0f);
            defaultConfig.skills.put("single_high_speed_electron_beam", singleBeamSettings);

            for (var skillId : List.of(
                    "airflow_jet", "air_cushion", "flow_sense", "breathing_film", "pneumatic_grasp",
                    "tailwind_field", "atmosphere_shield", "laminar_cutter", "vortex_pull", "atmosphere_blast_gun",
                    "wind_corridor", "pressure_lock", "flight", "vacuum_domain", "atmospheric_dominion")) {
                var settings = new SkillSettings();
                settings.floatMap.put("damageMultiplier", 1.0f);
                settings.floatMap.put("rangeMultiplier", 1.0f);
                settings.floatMap.put("durationMultiplier", 1.0f);
                settings.floatMap.put("cpMultiplier", 1.0f);
                defaultConfig.skills.put(skillId, settings);
            }

            return defaultConfig;
        }

        @Override
        public Class<AbilityConfig> getTypeClass() {
            return AbilityConfig.class;
        }
    }
}
