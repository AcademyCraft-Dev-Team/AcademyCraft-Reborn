package org.academy.api.common.ability;

import org.academy.api.common.attribute.AbilityFactor;

import java.util.function.ToDoubleFunction;

/**
 * P.R.O.P.S factor weights used to predict an initial ability category.
 */
public record AbilityFactorProfile(
        double neuralActivity,
        double muscleStrength,
        double endurance,
        double dexterity,
        double perception
) {
    public static final double TARGET_MAGNITUDE_SQUARED = 44.0;

    public AbilityFactorProfile {
        requireFiniteNonNegative(neuralActivity, "neuralActivity");
        requireFiniteNonNegative(muscleStrength, "muscleStrength");
        requireFiniteNonNegative(endurance, "endurance");
        requireFiniteNonNegative(dexterity, "dexterity");
        requireFiniteNonNegative(perception, "perception");
        if (neuralActivity == 0.0
                && muscleStrength == 0.0
                && endurance == 0.0
                && dexterity == 0.0
                && perception == 0.0) {
            throw new IllegalArgumentException("An ability factor profile cannot be empty");
        }
    }

    public double weight(AbilityFactor factor) {
        return switch (factor) {
            case MUSCLE_STRENGTH -> muscleStrength;
            case ENDURANCE -> endurance;
            case DEXTERITY -> dexterity;
            case PERCEPTION -> perception;
            case NEURAL_ACTIVITY -> neuralActivity;
        };
    }

    public double magnitudeSquared() {
        return neuralActivity * neuralActivity
                + muscleStrength * muscleStrength
                + endurance * endurance
                + dexterity * dexterity
                + perception * perception;
    }

    public double normalizedWeight(AbilityFactor factor) {
        return weight(factor) * Math.sqrt(TARGET_MAGNITUDE_SQUARED / magnitudeSquared());
    }

    public double score(ToDoubleFunction<AbilityFactor> factorValues) {
        var score = 0.0;
        for (var factor : AbilityFactor.values()) {
            score += finiteNonNegative(factorValues.applyAsDouble(factor)) * normalizedWeight(factor);
        }
        return score;
    }

    private static void requireFiniteNonNegative(double value, String name) {
        if (!Double.isFinite(value) || value < 0.0) {
            throw new IllegalArgumentException(name + " must be finite and non-negative");
        }
    }

    private static double finiteNonNegative(double value) {
        return Double.isFinite(value) ? Math.max(0.0, value) : 0.0;
    }
}
