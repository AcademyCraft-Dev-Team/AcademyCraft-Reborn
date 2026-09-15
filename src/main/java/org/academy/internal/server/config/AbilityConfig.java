package org.academy.internal.server.config;

import com.google.gson.annotations.SerializedName;
import org.academy.api.common.gson.TypeHandler;
import org.academy.api.common.registries.Registries;
import org.academy.api.server.ability.SkillTuning;
import org.academy.internal.common.world.damagesource.DestroyBlocksSetting;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

/**
 * Server-side ability tuning ({@code academy-server.json} 的 "ability" 段)。
 *
 * <p>布局（便于按能力分类查找）：</p>
 * <pre>
 * "ability": {
 *   "skills": {                       // 技能调参，先按能力分类分组，再按技能名排列
 *     "aeromanip":    { "flight": {...}, "vacuum_domain": {...} },
 *     "electromaster":{ "railgun": {...} },
 *     "teleport":     { "disarm": {...} }
 *   },
 *   "aeromanip": { ... },             // 气动操控分类的通用设置（非单技能）
 *   "mentalout": { ... },             // 心理掌握分类的通用设置（非单技能）
 *   "proficiency": { ... },           // 熟练度策略（跨技能）
 *   "damageMultiplier": 1.0           // 全局技能伤害总倍率
 * }
 * </pre>
 */
public class AbilityConfig {
    public static final String KEY = "ability";
    /** Bucket used when a skill's category cannot be resolved (e.g. registries not yet bound). */
    private static final String UNCATEGORIZED = "uncategorized";

    /**
     * Per-skill tuning grouped by ability category, then by skill path. Categories and skills are
     * written alphabetically (sorted at write time) so the file stays easy to scan.
     */
    @SerializedName("skills")
    public final Map<String, Map<String, SkillSettings>> skills = new TreeMap<>();
    @SerializedName("aeromanip")
    public final AeromanipSettings aeromanip = new AeromanipSettings();
    @SerializedName("electromaster")
    public final ElectromasterSettings electromaster = new ElectromasterSettings();
    @SerializedName("mentalout")
    public final MentaloutSettings mentalout = new MentaloutSettings();
    @SerializedName("proficiency")
    public final ProficiencySettings proficiency = new ProficiencySettings();
    /** Global damage scalar for every AcademyCraft skill, applied on top of each skill's own tuning. */
    @SerializedName("damageMultiplier")
    public float damageMultiplier = 1.0f;

    /**
     * Old aeromanip ids that were renamed during the staged skill-tree replacement. Kept here so a
     * server file written before the rename still finds its per-skill entry, without the public
     * tuning API depending on any one ability category.
     */
    private static final Map<String, String> LEGACY_SKILL_IDS = Map.of(
            "air_cushion", "laminar_buffer",
            "breathing_film", "breathing_bubble",
            "pressure_lock", "turbulent_cavitation",
            "atmosphere_blast_gun", "rejecting_wind",
            "wind_corridor", "high_speed_jet",
            "atmospheric_dominion", "adiabatic_compression"
    );

    /**
     * Category for the core skills that are pre-seeded into the defaults. Only a safety net for when
     * the skill registry is not bound (unit tests / very early startup); runtime resolves the real
     * category from the registry so renamed or addon skills still land in the right group.
     */
    private static final Map<String, String> CORE_CATEGORY_FALLBACK = Map.ofEntries(
            entry("airflow_jet", "aeromanip"), entry("laminar_buffer", "aeromanip"),
            entry("flow_sense", "aeromanip"), entry("breathing_bubble", "aeromanip"),
            entry("pneumatic_grasp", "aeromanip"), entry("tailwind_field", "aeromanip"),
            entry("atmosphere_shield", "aeromanip"), entry("laminar_cutter", "aeromanip"),
            entry("rejecting_wind", "aeromanip"), entry("vortex_pull", "aeromanip"),
            entry("high_speed_jet", "aeromanip"), entry("turbulent_cavitation", "aeromanip"),
            entry("flight", "aeromanip"), entry("vacuum_domain", "aeromanip"),
            entry("adiabatic_compression", "aeromanip"),
            entry("kinetic_energy_applied", "accelerator"), entry("plasma_generation", "accelerator"),
            entry("bloodflow_reverse", "accelerator"), entry("black_wing", "accelerator"),
            entry("white_wing", "accelerator"), entry("platinum_wing", "accelerator"),
            entry("railgun", "electromaster"), entry("ball_lightning", "electromaster"),
            entry("thunderclap", "electromaster"), entry("lightning_storm", "electromaster"),
            entry("mining_beam", "meltdowner"), entry("scatter_bomb", "meltdowner"),
            entry("particle_wave_cannon", "meltdowner"),
            entry("single_high_speed_electron_beam", "meltdowner"),
            entry("disintegrate", "meltdowner"), entry("auto_cruise_beam_cannon", "meltdowner"),
            entry("darkmatter_disassemble", "darkmatter"),
            entry("mind_destruction", "mentalout"),
            entry("self_teleport", "teleport"), entry("disarm", "teleport")
    );

    private static Map.Entry<String, String> entry(String skill, String category) {
        return Map.entry(skill, category);
    }

    /** Resolves a skill's settings, following the legacy-id rename for older server files. */
    public SkillSettings skillSettings(String skillId) {
        if (skillId == null) return null;
        var settings = findSkill(skillId);
        return settings != null ? settings : findSkill(LEGACY_SKILL_IDS.get(skillId));
    }

    private SkillSettings findSkill(String skillId) {
        if (skillId == null) return null;
        for (var group : skills.values()) {
            var settings = group.get(skillId);
            if (settings != null) return settings;
        }
        return null;
    }

    /** Returns the group for a category, creating it on demand. */
    public Map<String, SkillSettings> group(String category) {
        return skills.computeIfAbsent(category == null ? UNCATEGORIZED : category, _ -> new TreeMap<>());
    }

    /** Returns the settings for one skill under a category, creating both on demand. */
    public SkillSettings ensureSkill(String category, String skillPath) {
        return group(category).computeIfAbsent(skillPath, _ -> new SkillSettings());
    }

    /**
     * Seeds (or reuses) the entry for a skill in its registry-resolved category. Reuses an existing
     * entry so repeated seeding never creates duplicates under two groups.
     */
    public SkillSettings seed(String skillPath) {
        var existing = skillSettings(skillPath);
        return existing != null ? existing : ensureSkill(categoryOf(skillPath), skillPath);
    }

    /** Skill paths whose block-destruction gate is worth showing even at its default value. */
    static Set<String> blockDestructionGatePaths() {
        var paths = new HashSet<String>();
        for (var id : DestroyBlocksSetting.destructiveSkillIds()) {
            paths.add(id.substring(id.indexOf(':') + 1));
        }
        return paths;
    }

    /** Skill paths whose max-health-damage gate is worth showing even at its default value. */
    static Set<String> maxHealthDamageGatePaths() {
        return SkillTuning.maxHealthDamageSkillIds();
    }

    /** Skill paths whose player-disarm gate is worth showing even at its default value. */
    static Set<String> disarmGatePaths() {
        return Set.of("disarm");
    }

    /** Category a skill belongs to, from the registry when available. */
    static String categoryOf(String skillPath) {
        var resolved = categoryFromRegistry(skillPath);
        return resolved != null
                ? resolved
                : CORE_CATEGORY_FALLBACK.getOrDefault(skillPath, UNCATEGORIZED);
    }

    private static String categoryFromRegistry(String skillPath) {
        try {
            for (var skill : Registries.SKILLS) {
                if (skill.getKey().getPath().equals(skillPath)) {
                    var categoryKey = Registries.ABILITY_CATEGORIES.getKey(skill.getCategory());
                    if (categoryKey != null) return categoryKey.getPath();
                }
            }
        } catch (Throwable ignored) {
            // Registries not bound yet; fall back to the static table.
        }
        return null;
    }

    /**
     * Normalizes a loaded file. Handles two legacy shapes: a flat {@code skills} map (now grouped by
     * category) and loose {@code floatMap} keys ({@code damageMultiplier}/{@code rangeMultiplier}/
     * {@code cpMultiplier}) that are now typed fields. Explicit typed values always win.
     */
    void migrateLegacySkillKeys() {
        for (var groupEntry : List.copyOf(skills.entrySet())) {
            var groupName = groupEntry.getKey();
            var group = groupEntry.getValue();
            if (group == null) {
                skills.remove(groupName);
                continue;
            }
            for (var skillEntry : List.copyOf(group.entrySet())) {
                migrateFloatMap(skillEntry.getValue());
            }
        }
    }

    private static boolean looksLikeSkillSettings(com.google.gson.JsonObject object) {
        return object.has("floatMap") || object.has("booleanMap")
                || object.has("advanced") || object.has("damageMultiplier")
                || object.has("rangeMultiplier") || object.has("costMultiplier")
                || object.has("maxHealthDamageMultiplier") || object.has("iterationTicks")
                || object.has("maxStacks") || object.has("enabled");
    }

    private static void migrateFloatMap(SkillSettings settings) {
        if (settings == null) return;
        if (settings.damageMultiplier == 1.0f) {
            settings.damageMultiplier = settings.floatMap.getOrDefault(
                    "damageMultiplier", settings.damageMultiplier);
        }
        if (settings.rangeMultiplier == 1.0f) {
            settings.rangeMultiplier = settings.floatMap.getOrDefault(
                    "rangeMultiplier", settings.rangeMultiplier);
        }
        if (settings.costMultiplier == 1.0f) {
            settings.costMultiplier = settings.floatMap.getOrDefault(
                    "cpMultiplier", settings.costMultiplier);
        }
        settings.floatMap.remove("damageMultiplier");
        settings.floatMap.remove("rangeMultiplier");
        settings.floatMap.remove("cpMultiplier");
    }

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

    /** 电击使分类的麻痹引爆设置；关闭伤害仍保留电荷与行动中断。 */
    public static class ElectromasterSettings {
        @SerializedName("paralysisDamageEnabled")
        public boolean paralysisDamageEnabled = true;
        @SerializedName("paralysisMinimumDamage")
        public float paralysisMinimumDamage = 2.0f;
        /** 最大生命值的比例：0.01 表示 1%。 */
        @SerializedName("paralysisMaxHealthFraction")
        public float paralysisMaxHealthFraction = 0.01f;

        /** 悬浮时脚底到地面参照的目标间隙。 */
        @SerializedName("levitationRestClearance")
        public float levitationRestClearance = 1.5f;
        /** 垂直输入可把目标间隙推高的最大幅度。 */
        @SerializedName("levitationInputClearanceOffset")
        public float levitationInputClearanceOffset = 4.0f;
        /** 间隙误差转换为垂直速度的比例。 */
        @SerializedName("levitationFollowGain")
        public float levitationFollowGain = 0.40f;
        /** 地形抬升时允许的最大上行速度。 */
        @SerializedName("levitationMaxClimbSpeed")
        public float levitationMaxClimbSpeed = 0.50f;
        /** 地形下降时允许的最大下行速度。 */
        @SerializedName("levitationMaxDescentSpeed")
        public float levitationMaxDescentSpeed = 0.30f;
        /** 缺少地面参照时垂直输入直给的最大速度。 */
        @SerializedName("levitationInputVerticalSpeed")
        public float levitationInputVerticalSpeed = 0.30f;
        /** 失去支撑后的受控下沉速度。 */
        @SerializedName("levitationSinkSpeed")
        public float levitationSinkSpeed = 0.25f;
        /** 失去支撑期间保留的水平操控比例。 */
        @SerializedName("levitationGraceSpeedFactor")
        public float levitationGraceSpeedFactor = 0.60f;
        /** 单次支撑查询的碰撞形状读取上限；低于 1152 时最大支撑距离可能误判为无支撑。 */
        @SerializedName("levitationMaxSupportSamples")
        public int levitationMaxSupportSamples = 1152;
        /** 失去支撑后仍保留重力租约并尝试恢复的 tick 数。 */
        @SerializedName("levitationGraceTicks")
        public int levitationGraceTicks = 6;

        public float paralysisDamage(float maximumHealth) {
            if (!paralysisDamageEnabled) return 0.0f;
            var minimum = Float.isFinite(paralysisMinimumDamage)
                    ? Math.max(0.0f, paralysisMinimumDamage) : 2.0f;
            var fraction = Float.isFinite(paralysisMaxHealthFraction)
                    ? Math.clamp(paralysisMaxHealthFraction, 0.0f, 1.0f) : 0.01f;
            return Math.max(minimum, Float.isFinite(maximumHealth)
                    ? Math.max(0.0f, maximumHealth) * fraction : 0.0f);
        }

        /** Resolved levitation tuning; out-of-range or non-finite entries fall back per field. */
        public org.academy.api.common.ability.electromaster.LevitationTuning levitation() {
            return new org.academy.api.common.ability.electromaster.LevitationTuning(
                    levitationRestClearance, levitationInputClearanceOffset, levitationFollowGain,
                    levitationMaxClimbSpeed, levitationMaxDescentSpeed, levitationInputVerticalSpeed,
                    levitationSinkSpeed, levitationGraceSpeedFactor,
                    levitationMaxSupportSamples, levitationGraceTicks);
        }
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

    /**
     * Per-skill server tuning. Every registered skill is tunable; a skill without an entry simply
     * uses its builder-defined defaults, so the canonical numeric effects exist for all skills
     * without hand-written entries. Numbers are multipliers (1.0 = unchanged) except
     * {@code iterationTicks}/{@code maxStacks}, which use {@link #USE_DEFAULT} to keep the skill's
     * own value.
     */
    public static class SkillSettings {
        public static final int USE_DEFAULT = -1;

        @SerializedName("enabled")
        public boolean enabled = true;
        @SerializedName("damageMultiplier")
        public float damageMultiplier = 1.0f;
        @SerializedName("rangeMultiplier")
        public float rangeMultiplier = 1.0f;
        @SerializedName("costMultiplier")
        public float costMultiplier = 1.0f;
        /**
         * Separate multiplier for the percentage max-health damage term. {@code damageMultiplier} and
         * this skill's tuning never scale that term; use this field and {@code advanced.maxHealthDamage}
         * to tune it on its own.
         */
        @SerializedName("maxHealthDamageMultiplier")
        public float maxHealthDamageMultiplier = 1.0f;
        @SerializedName("iterationTicks")
        public int iterationTicks = USE_DEFAULT;
        @SerializedName("maxStacks")
        public int maxStacks = USE_DEFAULT;
        @SerializedName("advanced")
        public final AdvancedSettings advanced = new AdvancedSettings();

        /** Skill-specific extras (aeromanip air costs, beam timings) and legacy values. */
        @SerializedName("booleanMap")
        public final Map<String, Boolean> booleanMap = new HashMap<>();

        @SerializedName("floatMap")
        public final Map<String, Float> floatMap = new HashMap<>();

        /**
         * Server-owned gates for skill features the client may only narrow. A gated feature that is
         * disabled here cannot be re-enabled by the client.
         */
        public static class AdvancedSettings {
            @SerializedName("blockDestruction")
            public boolean blockDestruction = true;
            /** Whether this skill's percentage max-health damage is applied at all. */
            @SerializedName("maxHealthDamage")
            public boolean maxHealthDamage = true;
            /** Server-only policy: whether this skill may disarm a player target. Default off. */
            @SerializedName("disarmPlayers")
            public boolean disarmPlayers = false;
        }
    }

    public static class Action implements TypeHandler<AbilityConfig> {
        public static final TypeHandler<AbilityConfig> INSTANCE = new Action();

        private Action() {
        }

        @Override
        public AbilityConfig getDefault() {
            var defaultConfig = new AbilityConfig();

            // Seed only the skills that carry a non-neutral default or a gate worth discovering.
            // Every other skill stays out of the file and keeps its builder defaults; it can still be
            // added by hand and will be honored. Skills are placed in their ability category.
            defaultConfig.seed("single_high_speed_electron_beam")
                    .floatMap.put("attackDelayTicks", 10.0f);

            defaultConfig.seed("pneumatic_grasp").floatMap.put("compressedAirPerInterval", 2.0f);
            defaultConfig.seed("atmosphere_shield").floatMap.put("compressedAirPerEffect", 8.0f);
            defaultConfig.seed("high_speed_jet").floatMap.put("maximumNozzles", 8.0f);
            defaultConfig.seed("flight").floatMap.put("compressedAirPerInterval", 2.0f);
            defaultConfig.seed("flight").floatMap.put("compressedAirIntervalTicks", 20.0f);
            defaultConfig.seed("vacuum_domain").floatMap.put("compressedAirPerInterval", 8.0f);
            defaultConfig.seed("vacuum_domain").floatMap.put("compressedAirIntervalTicks", 10.0f);
            defaultConfig.seed("adiabatic_compression").floatMap.put("compressedAirPerInterval", 8.0f);
            defaultConfig.seed("adiabatic_compression").floatMap.put("compressedAirIntervalTicks", 10.0f);
            defaultConfig.seed("adiabatic_compression").floatMap.put("damagePerStack", 0.5f);

            // Skills that expose the block-destruction advanced setting.
            for (var skillId : DestroyBlocksSetting.destructiveSkillIds()) {
                defaultConfig.seed(skillId.substring(skillId.indexOf(':') + 1));
            }
            // Skills dealing percentage max-health damage: its multiplier and on/off gate.
            for (var path : SkillTuning.maxHealthDamageSkillIds()) {
                defaultConfig.seed(path);
            }
            // Disarm: its player-target gate.
            defaultConfig.seed("disarm");

            return defaultConfig;
        }

        @Override
        public com.google.gson.TypeAdapter<AbilityConfig> getAdapter(com.google.gson.Gson gson) {
            var delegate = gson.getAdapter(AbilityConfig.class);
            return new com.google.gson.TypeAdapter<>() {
                @Override
                public void write(com.google.gson.stream.JsonWriter out, AbilityConfig value)
                        throws java.io.IOException {
                    if (value == null) {
                        out.nullValue();
                        return;
                    }
                    // Write a trimmed tree so the file stays scannable: neutral values are omitted and
                    // only gates that matter for a skill are shown.
                    gson.toJson(toTrimmedTree(gson, value), out);
                }

                @Override
                public AbilityConfig read(com.google.gson.stream.JsonReader in)
                        throws java.io.IOException {
                    var element = com.google.gson.JsonParser.parseReader(in);
                    // Convert a legacy flat "skills" object into the grouped layout before Gson reads
                    // it, so old files still load and then round-trip in the new shape.
                    if (element.isJsonObject() && element.getAsJsonObject().has("skills")
                            && element.getAsJsonObject().get("skills").isJsonObject()) {
                        regroupFlatSkills(element.getAsJsonObject());
                    }
                    var config = delegate.fromJsonTree(element);
                    if (config != null) config.migrateLegacySkillKeys();
                    return config;
                }
            };
        }

        // ------------------------------------------------------------------
        // Compact output
        // ------------------------------------------------------------------

        private static com.google.gson.JsonObject toTrimmedTree(
                com.google.gson.Gson gson, AbilityConfig config) {
            var root = new com.google.gson.JsonObject();
            // Top-level sections are always written: they are the only place those settings live, so
            // hiding them when default would make them undiscoverable.
            root.addProperty("damageMultiplier", config.damageMultiplier);
            root.add("aeromanip", gson.toJsonTree(config.aeromanip));
            root.add("electromaster", gson.toJsonTree(config.electromaster));
            root.add("mentalout", gson.toJsonTree(config.mentalout));
            root.add("proficiency", gson.toJsonTree(config.proficiency));

            var blockGates = blockDestructionGatePaths();
            var maxHealthGates = maxHealthDamageGatePaths();
            var disarmGates = disarmGatePaths();

            var skills = new com.google.gson.JsonObject();
            // Sort at write time so the file is stable and scannable regardless of how it was loaded
            // (Gson constructs its own map on read, so the field's TreeMap order is not preserved).
            for (var groupName : new TreeSet<>(config.skills.keySet())) {
                var group = config.skills.get(groupName);
                if (group == null) continue;
                var groupJson = new com.google.gson.JsonObject();
                for (var skillPath : new TreeSet<>(group.keySet())) {
                    var settings = group.get(skillPath);
                    if (settings == null) continue;
                    var settingsJson = skillToTrimmedTree(gson, settings,
                            blockGates.contains(skillPath),
                            maxHealthGates.contains(skillPath),
                            disarmGates.contains(skillPath));
                    if (settingsJson.size() > 0) groupJson.add(skillPath, settingsJson);
                }
                if (groupJson.size() > 0) skills.add(groupName, groupJson);
            }
            if (skills.size() > 0) root.add("skills", skills);
            return root;
        }

        private static com.google.gson.JsonObject skillToTrimmedTree(
                com.google.gson.Gson gson, SkillSettings settings,
                boolean blockGate, boolean maxHealthGate, boolean disarmGate) {
            var json = new com.google.gson.JsonObject();
            if (!settings.enabled) json.addProperty("enabled", false);
            if (settings.damageMultiplier != 1.0f) json.addProperty("damageMultiplier", settings.damageMultiplier);
            if (settings.rangeMultiplier != 1.0f) json.addProperty("rangeMultiplier", settings.rangeMultiplier);
            if (settings.costMultiplier != 1.0f) json.addProperty("costMultiplier", settings.costMultiplier);
            if (settings.maxHealthDamageMultiplier != 1.0f) {
                json.addProperty("maxHealthDamageMultiplier", settings.maxHealthDamageMultiplier);
            }
            if (settings.iterationTicks != SkillSettings.USE_DEFAULT) {
                json.addProperty("iterationTicks", settings.iterationTicks);
            }
            if (settings.maxStacks != SkillSettings.USE_DEFAULT) {
                json.addProperty("maxStacks", settings.maxStacks);
            }

            var advanced = new com.google.gson.JsonObject();
            // A gate is shown when it deviates from its default, or when it is meaningful for this
            // skill (so administrators can discover the switch even while it sits at the default).
            if (blockGate || !settings.advanced.blockDestruction) {
                advanced.addProperty("blockDestruction", settings.advanced.blockDestruction);
            }
            if (maxHealthGate || !settings.advanced.maxHealthDamage) {
                advanced.addProperty("maxHealthDamage", settings.advanced.maxHealthDamage);
            }
            if (disarmGate || settings.advanced.disarmPlayers) {
                advanced.addProperty("disarmPlayers", settings.advanced.disarmPlayers);
            }
            if (advanced.size() > 0) json.add("advanced", advanced);

            if (!settings.booleanMap.isEmpty()) json.add("booleanMap", gson.toJsonTree(settings.booleanMap));
            if (!settings.floatMap.isEmpty()) json.add("floatMap", gson.toJsonTree(settings.floatMap));
            return json;
        }

        /**
         * Rewrites a flat {@code skills} object (skill name to settings) into the grouped layout (category
         * to skill to settings), leaving an already-grouped object untouched.
         */
        private static void regroupFlatSkills(com.google.gson.JsonObject root) {
            var flat = root.getAsJsonObject("skills");
            if (!hasFlatSkillEntries(flat)) return;
            var grouped = new com.google.gson.JsonObject();
            for (var entry : flat.entrySet()) {
                if (!entry.getValue().isJsonObject()
                        || !looksLikeSkillSettings(entry.getValue().getAsJsonObject())) continue;
                var category = categoryOf(entry.getKey());
                var group = grouped.has(category)
                        ? grouped.getAsJsonObject(category) : new com.google.gson.JsonObject();
                group.add(entry.getKey(), entry.getValue());
                grouped.add(category, group);
            }
            root.add("skills", grouped);
        }

        private static boolean hasFlatSkillEntries(com.google.gson.JsonObject skills) {
            for (var entry : skills.entrySet()) {
                if (entry.getValue().isJsonObject() && looksLikeSkillSettings(entry.getValue().getAsJsonObject())) {
                    return true;
                }
            }
            return false;
        }

        @Override
        public Class<AbilityConfig> getTypeClass() {
            return AbilityConfig.class;
        }
    }
}
