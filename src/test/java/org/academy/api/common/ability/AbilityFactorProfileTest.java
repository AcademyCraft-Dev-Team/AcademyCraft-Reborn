package org.academy.api.common.ability;

import org.academy.api.common.attribute.AbilityFactor;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class AbilityFactorProfileTest {
    @Test
    void exposesWeightsByNamedFactorOrder() {
        var profile = new AbilityFactorProfile(1, 2, 3, 4, 5);

        assertEquals(1.0, profile.weight(AbilityFactor.NEURAL_ACTIVITY));
        assertEquals(2.0, profile.weight(AbilityFactor.MUSCLE_STRENGTH));
        assertEquals(3.0, profile.weight(AbilityFactor.ENDURANCE));
        assertEquals(4.0, profile.weight(AbilityFactor.DEXTERITY));
        assertEquals(5.0, profile.weight(AbilityFactor.PERCEPTION));
    }

    @Test
    void normalizesExtensionProfilesBeforeScoring() {
        var profile = new AbilityFactorProfile(3, 0, 0, 1, 1);
        var scale = Math.sqrt(AbilityFactorProfile.TARGET_MAGNITUDE_SQUARED / 11.0);

        assertEquals(3.0 * scale, profile.normalizedWeight(AbilityFactor.NEURAL_ACTIVITY), 1.0E-9);
        assertEquals(Math.sqrt(AbilityFactorProfile.TARGET_MAGNITUDE_SQUARED),
                Math.sqrt(profile.score(profile::normalizedWeight)), 1.0E-9);
    }

    @Test
    void rejectsInvalidProfiles() {
        assertThrows(IllegalArgumentException.class, () -> new AbilityFactorProfile(0, 0, 0, 0, 0));
        assertThrows(IllegalArgumentException.class, () -> new AbilityFactorProfile(-1, 0, 0, 0, 1));
        assertThrows(IllegalArgumentException.class, () ->
                new AbilityFactorProfile(Double.NaN, 0, 0, 0, 1));
    }
}
