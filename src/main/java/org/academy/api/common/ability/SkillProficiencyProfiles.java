package org.academy.api.common.ability;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import static org.academy.api.common.ability.SkillProficiencyProfile.CostKind.*;

/** Scalar proficiency declarations for every category-scoped Academy skill. */
public final class SkillProficiencyProfiles {
    private static final String PREFIX = "academy:";
    private static final Set<String> DECLARED_SKILLS = Set.of(
            "airflow_jet", "air_cushion", "flow_sense", "atmosphere_shield", "breathing_film",
            "pneumatic_grasp", "tailwind_field", "laminar_cutter", "vortex_pull", "atmosphere_blast_gun",
            "wind_corridor", "pressure_lock", "flight", "vacuum_domain", "atmospheric_dominion",
            "vector_reflection", "reflection_filter", "vector_blast", "vector_accel", "vector_deviation",
            "kinetic_energy_applied", "dir_strike", "bloodflow_reverse", "black_wing", "white_wing",
            "platinum_wing", "crossing_the_abyss", "storm_wing", "plasma_generation",
            "arc_generate", "magnet_manipulation", "mine_detect", "magnetic_weapon", "current_symbiosis",
            "bioelectric_operation", "electromagnetic_shield", "iron_sand_arsenal", "thunder_lance",
            "railgun", "ball_lightning", "current_recharge", "electrical_contact", "lightning_nova",
            "lightning_storm", "thunderclap",
            "single_high_speed_electron_beam", "scatter_bomb", "radiation_intensify", "mining_beam",
            "light_shield", "cloudroom", "particle_wave_cannon", "jet_strike", "disintegrate",
            "auto_cruise_beam_cannon",
            "threatening_teleport", "space_folding_theorem", "self_teleport", "spatial_synergy",
            "piercing_teleportation", "flesh_ripping", "location_teleport", "quick_location_teleport",
            "area_teleport_select", "flashing",
            "defensive_teleport", "spacial_excision", "disarm", "shackle",
            "darkmatter_shaping", "darkmatter_disassemble", "darkmatter_cut", "darkmatter_radiation",
            "darkmatter_repair", "darkmatter_creation", "darkmatter_six_wings",
            "mental_intervention", "target_misidentification", "mental_stupor", "impression_manipulation",
            "mental_intrusion", "mental_takeover", "sensory_distortion", "command_positioning",
            "precision_operation"
    );
    private static final Map<String, String> CUSTOM_PROFILE_REASONS = Map.of(
            "flow_sense", "range, cadence and synchronization budgets are resolved together",
            "pneumatic_grasp", "continuous costs depend on the selected entity class",
            "radiation_intensify", "mark duration and damage segments are resolved by the mark runtime",
            "space_folding_theorem", "passive damage and refund rules have no scalar CP profile"
    );
    private static final Map<String, SkillProficiencyProfile> PROFILES = createProfiles();

    private SkillProficiencyProfiles() {
    }

    public static SkillProficiencyProfile forSkill(String skillId) {
        if (skillId == null || !skillId.startsWith(PREFIX)) return SkillProficiencyProfile.NONE;
        return PROFILES.getOrDefault(skillId.substring(PREFIX.length()), SkillProficiencyProfile.NONE);
    }

    public static boolean isDeclared(String skillId) {
        return skillId != null && skillId.startsWith(PREFIX)
                && DECLARED_SKILLS.contains(skillId.substring(PREFIX.length()));
    }

    public static Set<String> declaredSkillPaths() {
        return DECLARED_SKILLS;
    }

    public static String customProfileReason(String skillId) {
        if (skillId == null || !skillId.startsWith(PREFIX)) return null;
        return CUSTOM_PROFILE_REASONS.get(skillId.substring(PREFIX.length()));
    }

    private static Map<String, SkillProficiencyProfile> createProfiles() {
        var profiles = new HashMap<String, SkillProficiencyProfile>();

        put(profiles, continuous(0.9f), "airflow_jet", "magnet_manipulation", "current_recharge",
                "mining_beam", "light_shield", "particle_wave_cannon", "spacial_excision",
                "darkmatter_radiation", "darkmatter_repair");
        put(profiles, cast(5.0f / 6.0f), "air_cushion");
        put(profiles, costs(Map.of(MAINTENANCE, 0.9f, CONTINUOUS, 0.9f)), "atmosphere_shield");
        put(profiles, maintenance(0.9f), "breathing_film", "tailwind_field", "flight",
                "kinetic_energy_applied", "black_wing", "white_wing", "platinum_wing",
                "crossing_the_abyss", "storm_wing", "mine_detect", "magnetic_weapon",
                "current_symbiosis", "bioelectric_operation", "iron_sand_arsenal", "electrical_contact",
                "cloudroom", "spatial_synergy", "darkmatter_six_wings", "mental_intrusion",
                "mental_takeover", "sensory_distortion");
        put(profiles, cast(0.9f), "laminar_cutter", "vortex_pull", "atmosphere_blast_gun",
                "wind_corridor", "pressure_lock", "vacuum_domain", "atmospheric_dominion",
                "vector_blast", "vector_accel", "dir_strike", "bloodflow_reverse", "plasma_generation",
                "arc_generate", "thunder_lance", "railgun", "ball_lightning", "lightning_nova",
                "lightning_storm", "thunderclap", "single_high_speed_electron_beam", "scatter_bomb",
                "jet_strike", "disintegrate", "threatening_teleport", "self_teleport", "piercing_teleportation",
                "flesh_ripping", "location_teleport", "quick_location_teleport", "area_teleport_select",
                "disarm", "shackle", "darkmatter_shaping", "darkmatter_disassemble", "darkmatter_cut",
                "mental_intervention", "target_misidentification");
        put(profiles, costs(Map.of(MAINTENANCE, 0.9f, CONTINUOUS, 0.9f)),
                "electromagnetic_shield", "flashing", "defensive_teleport");
        put(profiles, costs(Map.of(CAST, 0.9f, CONTINUOUS, 0.9f, MAINTENANCE, 0.9f)),
                "auto_cruise_beam_cannon", "darkmatter_creation");
        put(profiles, costs(Map.of(DYNAMIC, 0.9f)), "command_positioning", "precision_operation");
        put(profiles, costsByTier(DYNAMIC, 1.0f, 0.9f, 0.8f, 0.8f),
                "mental_stupor", "impression_manipulation");
        put(profiles, costs(Map.of(DYNAMIC, 0.85f)), "reflection_filter");
        put(profiles, SkillProficiencyProfile.builder()
                        .iterationTicks(10, 10, 10, 5)
                        .build(),
                "vector_reflection", "vector_deviation");
        return Map.copyOf(profiles);
    }

    private static SkillProficiencyProfile cast(float at1000) {
        return costs(Map.of(CAST, at1000));
    }

    private static SkillProficiencyProfile continuous(float at1000) {
        return costs(Map.of(CONTINUOUS, at1000));
    }

    private static SkillProficiencyProfile maintenance(float at1000) {
        return costs(Map.of(MAINTENANCE, at1000));
    }

    private static SkillProficiencyProfile costs(Map<SkillProficiencyProfile.CostKind, Float> values) {
        var builder = SkillProficiencyProfile.builder();
        values.forEach((kind, value) -> builder.costs(kind, 1.0f, value, value, value));
        return builder.build();
    }

    private static SkillProficiencyProfile costsByTier(
            SkillProficiencyProfile.CostKind kind,
            float base,
            float at1000,
            float at2000,
            float at3000
    ) {
        return SkillProficiencyProfile.builder().costs(kind, base, at1000, at2000, at3000).build();
    }

    private static void put(
            Map<String, SkillProficiencyProfile> profiles,
            SkillProficiencyProfile profile,
            String... skillPaths
    ) {
        for (var skillPath : skillPaths) {
            if (profiles.put(skillPath, profile) != null) {
                throw new IllegalStateException("Duplicate proficiency profile: " + skillPath);
            }
        }
    }
}
