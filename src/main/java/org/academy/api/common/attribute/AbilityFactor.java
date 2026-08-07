package org.academy.api.common.attribute;

/** The five base factors managed by the P.R.O.P.S system. */
public enum AbilityFactor {
    MUSCLE_STRENGTH,
    ENDURANCE,
    DEXTERITY,
    PERCEPTION,
    NEURAL_ACTIVITY;

    public int bit() {
        return 1 << ordinal();
    }

    public static AbilityFactor byOrdinal(int ordinal) {
        var values = values();
        return ordinal >= 0 && ordinal < values.length ? values[ordinal] : null;
    }
}
