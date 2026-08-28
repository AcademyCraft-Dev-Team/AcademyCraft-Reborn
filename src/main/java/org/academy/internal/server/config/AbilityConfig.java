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
    @SerializedName("brainDevelopment")
    public final BrainDevelopmentSettings brainDevelopment = new BrainDevelopmentSettings();
    @SerializedName("aeromanip")
    public final AeromanipSettings aeromanip = new AeromanipSettings();
    @SerializedName("mentalout")
    public final MentaloutSettings mentalout = new MentaloutSettings();
    @SerializedName("proficiency")
    public final ProficiencySettings proficiency = new ProficiencySettings();
    @SerializedName("damageMultiplier")
    public float damageMultiplier = 1.0f;
    @SerializedName("cpRatingOffset")
    public float cpRatingOffset = 0.0f;

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
        @SerializedName("compressedAirCapacity")
        public int compressedAirCapacity = 128;
        @SerializedName("compressedAirRecoveryPerTick")
        public float compressedAirRecoveryPerTick = 4.0f;
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
        public float mentalStuporCostPerTarget = 10.0f;
        @SerializedName("impressionManipulationCostPerTarget")
        public float impressionManipulationCostPerTarget = 10.0f;
        @SerializedName("commandPositioningCostPerTarget")
        public float commandPositioningCostPerTarget = 10.0f;
        @SerializedName("precisionStuporCostPerTarget")
        public float precisionStuporCostPerTarget = 10.0f;
        @SerializedName("precisionImpressionCostPerTarget")
        public float precisionImpressionCostPerTarget = 10.0f;
        @SerializedName("precisionMisidentificationCostPerTarget")
        public float precisionMisidentificationCostPerTarget = 20.0f;
        @SerializedName("precisionPathCostPerTarget")
        public float precisionPathCostPerTarget = 5.0f;
        @SerializedName("precisionViewCostPerTarget")
        public float precisionViewCostPerTarget = 5.0f;
        @SerializedName("precisionGuardCostPerTarget")
        public float precisionGuardCostPerTarget = 10.0f;
        @SerializedName("precisionSensoryCostLevel0")
        public float precisionSensoryCostLevel0 = 20.0f;
        @SerializedName("precisionSensoryCostLevel1")
        public float precisionSensoryCostLevel1 = 15.0f;
        @SerializedName("precisionSensoryCostLevel2")
        public float precisionSensoryCostLevel2 = 10.0f;
        @SerializedName("precisionIntrusionCostLevel0")
        public float precisionIntrusionCostLevel0 = 20.0f;
        @SerializedName("precisionIntrusionCostLevel1")
        public float precisionIntrusionCostLevel1 = 15.0f;
        @SerializedName("precisionIntrusionCostLevel2")
        public float precisionIntrusionCostLevel2 = 10.0f;
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
        public float mentalIntrusionRange = 32.0f;
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
                    "airflow_jet", "laminar_buffer", "flow_sense", "breathing_bubble", "pneumatic_grasp",
                    "tailwind_field", "atmosphere_shield", "laminar_cutter", "rejecting_wind", "vortex_pull",
                    "high_speed_jet", "turbulent_cavitation", "flight", "vacuum_domain", "adiabatic_compression")) {
                var settings = new SkillSettings();
                settings.floatMap.put("damageMultiplier", 1.0f);
                settings.floatMap.put("rangeMultiplier", 1.0f);
                settings.floatMap.put("durationMultiplier", 1.0f);
                settings.floatMap.put("cpMultiplier", 1.0f);
                defaultConfig.skills.put(skillId, settings);
            }
            defaultConfig.skills.get("breathing_bubble").floatMap.put("activeCompressedAirCost", 24.0f);
            defaultConfig.skills.get("pneumatic_grasp").floatMap.put("compressedAirPerInterval", 2.0f);
            defaultConfig.skills.get("atmosphere_shield").floatMap.put("compressedAirPerEffect", 8.0f);
            defaultConfig.skills.get("high_speed_jet").floatMap.put("maximumNozzles", 8.0f);
            defaultConfig.skills.get("flight").floatMap.put("compressedAirPerInterval", 2.0f);
            defaultConfig.skills.get("flight").floatMap.put("compressedAirIntervalTicks", 20.0f);
            defaultConfig.skills.get("vacuum_domain").floatMap.put("compressedAirPerInterval", 8.0f);
            defaultConfig.skills.get("vacuum_domain").floatMap.put("compressedAirIntervalTicks", 10.0f);
            defaultConfig.skills.get("adiabatic_compression").floatMap.put("compressedAirPerInterval", 8.0f);
            defaultConfig.skills.get("adiabatic_compression").floatMap.put("compressedAirIntervalTicks", 10.0f);
            defaultConfig.skills.get("adiabatic_compression").floatMap.put("damagePerStack", 0.5f);

            return defaultConfig;
        }

        @Override
        public Class<AbilityConfig> getTypeClass() {
            return AbilityConfig.class;
        }
    }
}
