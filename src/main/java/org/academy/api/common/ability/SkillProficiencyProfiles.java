package org.academy.api.common.ability;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import static org.academy.api.common.ability.SkillProficiencyProfile.CostKind.*;

/**
 * Scalar proficiency declarations for every category-scoped Academy skill.
 */
public final class SkillProficiencyProfiles {
    private static final String PREFIX = "academy:";
    private static final Set<String> DECLARED_SKILLS = Set.of(
            "airflow_jet", "laminar_buffer", "flow_sense", "atmosphere_shield", "breathing_bubble",
            "pneumatic_grasp", "tailwind_field", "laminar_cutter", "rejecting_wind", "vortex_pull",
            "high_speed_jet", "turbulent_cavitation", "flight", "vacuum_domain", "adiabatic_compression",
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
            "darkmatter_generation", "darkmatter_shaping", "darkmatter_phase_tuning",
            "darkmatter_disassemble", "darkmatter_cut", "darkmatter_interference",
            "darkmatter_repair", "darkmatter_creation", "darkmatter_six_wings",
            "mental_intervention", "target_misidentification", "mental_stupor", "impression_manipulation",
            "mental_intrusion", "mental_takeover", "sensory_distortion", "command_positioning",
            "mind_destruction", "precision_operation"
    );
    private static final Map<String, String> CUSTOM_PROFILE_REASONS = Map.ofEntries(
            Map.entry("airflow_jet", "release-tier damage, movement, and duration milestones are resolved together"),
            Map.entry("laminar_buffer", "sharing, hover duration, and platform lifetime milestones are resolved together"),
            Map.entry("flow_sense", "range, cadence and synchronization budgets are resolved together"),
            Map.entry("pneumatic_grasp", "continuous costs depend on the selected entity class"),
            Map.entry("breathing_bubble", "compressed-air upkeep, sharing and active radius milestones are resolved together"),
            Map.entry("turbulent_cavitation", "damage and armor wear are derived from actual attributed displacement"),
            Map.entry("tailwind_field", "release mode, field radius, duration and force milestones are resolved together"),
            Map.entry("rejecting_wind", "release-tier force, control effects and low-drag duration are resolved together"),
            Map.entry("high_speed_jet", "nozzle count, duration and stacked acceleration milestones are resolved together"),
            Map.entry("flight", "creative-flight speed and compressed-air upkeep are resolved by its flight lease"),
            Map.entry("vacuum_domain", "compressed-air upkeep, oxygen depletion, and the final radius are resolved together"),
            Map.entry("radiation_intensify", "mark duration and damage segments are resolved by the mark runtime"),
            Map.entry("space_folding_theorem", "passive damage and refund rules have no scalar CP profile"),
            Map.entry("darkmatter_generation", "server-authoritative MP/CP ledger implements its milestones"),
            Map.entry("darkmatter_shaping", "MP cost, integrity lifetime and gamma shaping are resolved together"),
            Map.entry("darkmatter_phase_tuning", "server phase-point cadence implements its milestones"),
            Map.entry("darkmatter_disassemble", "MP cost, phase targeting and gamma field are resolved together"),
            Map.entry("darkmatter_cut", "MP cost, mark and mirror slash are resolved together"),
            Map.entry("darkmatter_interference", "continuous MP cadence, exposure and gamma blades are resolved together"),
            Map.entry("darkmatter_repair", "productive MP consumption and repair target counts are resolved together"),
            Map.entry("darkmatter_creation", "blueprint investment, reservation and module values are resolved together"),
            Map.entry("darkmatter_six_wings", "dynamic CP maintenance and gamma multipliers are resolved together")
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

        put(profiles, continuous(0.9f), "magnet_manipulation", "current_recharge",
                "mining_beam", "light_shield", "particle_wave_cannon", "spacial_excision");
        put(profiles, costs(Map.of(MAINTENANCE, 0.9f, CONTINUOUS, 0.9f, DYNAMIC, 0.9f)),
                "atmosphere_shield");
        put(profiles, maintenance(0.9f),
                "kinetic_energy_applied", "black_wing", "white_wing", "platinum_wing",
                "crossing_the_abyss", "storm_wing", "mine_detect", "magnetic_weapon",
                "current_symbiosis", "bioelectric_operation", "iron_sand_arsenal", "electrical_contact",
                "cloudroom", "spatial_synergy", "mental_intrusion",
                "mental_takeover", "sensory_distortion");
        put(profiles, continuous(0.9f), "adiabatic_compression");
        put(profiles, cast(0.9f), "laminar_cutter", "vortex_pull",
                "vector_blast", "vector_accel", "dir_strike", "bloodflow_reverse", "plasma_generation",
                "arc_generate", "thunder_lance", "railgun", "ball_lightning", "lightning_nova",
                "lightning_storm", "thunderclap", "single_high_speed_electron_beam", "scatter_bomb",
                "jet_strike", "disintegrate", "threatening_teleport", "self_teleport", "piercing_teleportation",
                "flesh_ripping", "location_teleport", "quick_location_teleport", "area_teleport_select",
                "disarm", "shackle", "mental_intervention", "target_misidentification");
        put(profiles, costs(Map.of(MAINTENANCE, 0.9f, CONTINUOUS, 0.9f)),
                "electromagnetic_shield", "flashing", "defensive_teleport");
        put(profiles, costs(Map.of(CAST, 0.9f, CONTINUOUS, 0.9f, MAINTENANCE, 0.9f)),
                "auto_cruise_beam_cannon");
        put(profiles, costs(Map.of(DYNAMIC, 0.9f)), "command_positioning", "precision_operation");
        put(profiles, costsByTier(DYNAMIC, 1.0f, 0.9f, 0.8f, 0.8f),
                "mental_stupor", "impression_manipulation");
        put(profiles, costsByTier(CAST, 1.0f, 0.9f, 0.8f, 0.7f),
                "mind_destruction");
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
