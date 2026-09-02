package org.academy.api.server.entity;

import java.util.EnumSet;
import java.util.Objects;
import java.util.Set;

/**
 * Strength and state coverage requested by one survival-defense owner.
 *
 * <p>Profiles compose by taking the strongest contribution for every aspect and the highest
 * requested health floor. A strength of {@link #ABSOLUTE_STRENGTH} is reserved for defenses
 * whose owner must explicitly release its lease before an authoritative death may proceed.</p>
 */
public record SurvivalDefenseProfile(
        int strength,
        float minimumHealth,
        Set<SurvivalDefenseAspect> aspects
) {
    public static final int ABSOLUTE_STRENGTH = Integer.MAX_VALUE;

    public SurvivalDefenseProfile {
        if (strength <= 0) {
            throw new IllegalArgumentException("Survival-defense strength must be positive.");
        }
        if (!Float.isFinite(minimumHealth) || minimumHealth < 0.0f) {
            throw new IllegalArgumentException("Minimum health must be finite and non-negative.");
        }
        Objects.requireNonNull(aspects, "aspects");
        aspects = Set.copyOf(aspects);
        if (aspects.isEmpty()) {
            throw new IllegalArgumentException("At least one survival-defense aspect is required.");
        }
        if (aspects.contains(SurvivalDefenseAspect.HEALTH_FLOOR) && minimumHealth <= 0.0f) {
            throw new IllegalArgumentException("Health-floor defense requires positive minimum health.");
        }
    }

    public static SurvivalDefenseProfile absolute(float minimumHealth) {
        return new SurvivalDefenseProfile(
                ABSOLUTE_STRENGTH,
                minimumHealth,
                EnumSet.allOf(SurvivalDefenseAspect.class)
        );
    }
}
