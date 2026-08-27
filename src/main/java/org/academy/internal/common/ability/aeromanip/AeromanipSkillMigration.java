package org.academy.internal.common.ability.aeromanip;

import java.util.Map;
import java.util.Optional;

/** Compatibility names used while the Aeromanipulation skill tree is replaced in stages. */
public final class AeromanipSkillMigration {
    public static final Map<String, String> LEGACY_TO_REPLACEMENT = Map.of(
            "air_cushion", "laminar_buffer",
            "breathing_film", "breathing_bubble",
            "pressure_lock", "turbulent_cavitation",
            "atmosphere_blast_gun", "rejecting_wind",
            "wind_corridor", "high_speed_jet",
            "atmospheric_dominion", "adiabatic_compression"
    );
    private static final Map<String, String> REPLACEMENT_TO_LEGACY = Map.of(
            "laminar_buffer", "air_cushion",
            "breathing_bubble", "breathing_film",
            "turbulent_cavitation", "pressure_lock",
            "rejecting_wind", "atmosphere_blast_gun",
            "high_speed_jet", "wind_corridor",
            "adiabatic_compression", "atmospheric_dominion"
    );

    private AeromanipSkillMigration() {
    }

    public static Optional<String> replacementForLegacy(String skillId) {
        return Optional.ofNullable(LEGACY_TO_REPLACEMENT.get(skillId));
    }

    public static Optional<String> legacyForReplacement(String skillId) {
        return Optional.ofNullable(REPLACEMENT_TO_LEGACY.get(skillId));
    }
}
