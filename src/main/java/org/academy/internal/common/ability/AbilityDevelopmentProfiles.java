package org.academy.internal.common.ability;

import org.academy.api.common.ability.AbilityFactorProfile;

import java.util.List;

/**
 * Core and reserved P.R.O.P.S profiles, ordered as neural, muscle, endurance, dexterity, perception.
 */
public final class AbilityDevelopmentProfiles {
    public static final AbilityFactorProfile ACCELERATOR = profile(6, 0, 0, 2, 2);
    public static final AbilityFactorProfile ELECTROMASTER = profile(4, 3, 1, 3, 3);
    public static final AbilityFactorProfile TELEPORT = profile(3, 0, 1, 5, 3);
    public static final AbilityFactorProfile MELTDOWNER = profile(3, 3, 5, 1, 0);
    public static final AbilityFactorProfile AEROMANIP = profile(1, 3, 3, 4, 3);
    public static final AbilityFactorProfile DARKMATTER = profile(5, 1, 3, 0, 3);
    public static final AbilityFactorProfile MENTALOUT = profile(4, 1, 1, 1, 5);

    /**
     * Reserved for a future physically focused ability. Not attached to a registered category.
     */
    public static final AbilityFactorProfile RESERVED_A = profile(2, 6, 0, 2, 0);
    /**
     * Reserved for a future endurance/perception focused ability. Not attached to a registered category.
     */
    public static final AbilityFactorProfile RESERVED_B = profile(1, 1, 5, 1, 4);
    /**
     * Reserved for a future perception focused ability. Not attached to a registered category.
     */
    public static final AbilityFactorProfile RESERVED_C = profile(0, 0, 2, 2, 6);

    private static final List<AbilityFactorProfile> ACTIVE = List.of(
            ACCELERATOR,
            ELECTROMASTER,
            TELEPORT,
            MELTDOWNER,
            AEROMANIP,
            DARKMATTER,
            MENTALOUT
    );
    private static final List<AbilityFactorProfile> RESERVED = List.of(
            RESERVED_A,
            RESERVED_B,
            RESERVED_C
    );

    private AbilityDevelopmentProfiles() {
    }

    public static List<AbilityFactorProfile> activeProfiles() {
        return ACTIVE;
    }

    public static List<AbilityFactorProfile> reservedProfiles() {
        return RESERVED;
    }

    public static List<AbilityFactorProfile> allProfiles() {
        return List.of(
                ACCELERATOR,
                ELECTROMASTER,
                TELEPORT,
                MELTDOWNER,
                AEROMANIP,
                DARKMATTER,
                MENTALOUT,
                RESERVED_A,
                RESERVED_B,
                RESERVED_C
        );
    }

    private static AbilityFactorProfile profile(
            double neuralActivity,
            double muscleStrength,
            double endurance,
            double dexterity,
            double perception
    ) {
        return new AbilityFactorProfile(
                neuralActivity,
                muscleStrength,
                endurance,
                dexterity,
                perception
        );
    }
}
