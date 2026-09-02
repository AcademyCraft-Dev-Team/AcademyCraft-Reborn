package org.academy.api.server.entity;

import org.junit.jupiter.api.Test;

import java.util.EnumSet;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SurvivalDefenseProfileTest {
    @Test
    void absoluteProfileCoversEverySurvivalAspect() {
        var profile = SurvivalDefenseProfile.absolute(1.0f);

        assertEquals(SurvivalDefenseProfile.ABSOLUTE_STRENGTH, profile.strength());
        assertEquals(1.0f, profile.minimumHealth());
        assertEquals(EnumSet.allOf(SurvivalDefenseAspect.class), profile.aspects());
    }

    @Test
    void copiedAspectSetCannotBeMutatedThroughItsInput() {
        var aspects = EnumSet.of(SurvivalDefenseAspect.DEATH_STATE);
        var profile = new SurvivalDefenseProfile(20, 0.0f, aspects);
        aspects.add(SurvivalDefenseAspect.REMOVAL);

        assertEquals(EnumSet.of(SurvivalDefenseAspect.DEATH_STATE), profile.aspects());
        assertThrows(UnsupportedOperationException.class,
                () -> profile.aspects().add(SurvivalDefenseAspect.REMOVAL));
    }

    @Test
    void healthFloorRequiresPositiveFiniteValue() {
        var aspects = EnumSet.of(SurvivalDefenseAspect.HEALTH_FLOOR);

        assertThrows(IllegalArgumentException.class,
                () -> new SurvivalDefenseProfile(1, 0.0f, aspects));
        assertThrows(IllegalArgumentException.class,
                () -> new SurvivalDefenseProfile(1, Float.NaN, aspects));
        assertTrue(new SurvivalDefenseProfile(1, 0.5f, aspects).minimumHealth() > 0.0f);
    }
}
