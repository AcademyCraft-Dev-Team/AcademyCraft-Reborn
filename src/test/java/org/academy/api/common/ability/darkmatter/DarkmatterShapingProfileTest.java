package org.academy.api.common.ability.darkmatter;

import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class DarkmatterShapingProfileTest {
    @Test
    void clampsLevelAndRestoresTheFixedPhasePool() {
        var profile = new DarkmatterShapingProfile(8, 999, 999,
                Map.of(DarkmatterModifiers.LUCKY, 3));

        assertEquals(5, profile.abilityLevel());
        assertEquals(250, profile.alphaPoints());
        assertEquals(0, profile.betaPoints());
        assertEquals(5.0f, profile.alphaPower(), 0.0001f);
        assertEquals(0.0f, profile.betaPower(), 0.0001f);
        assertEquals(3, profile.modifierLevel(DarkmatterModifiers.LUCKY));
    }

    @Test
    void profileKeepsItsOwnPhaseAndDropsInvalidModifierRows() {
        var source = new LinkedHashMap<String, Integer>();
        source.put(DarkmatterModifiers.HARVEST, 2);
        source.put("", 3);
        source.put("zero", 0);
        var profile = new DarkmatterShapingProfile(3, 50, 100, source);

        assertEquals(1.0f, profile.alphaPower(), 0.0001f);
        assertEquals(2.0f, profile.betaPower(), 0.0001f);
        assertEquals(Map.of(DarkmatterModifiers.HARVEST, 2), profile.modifiers());
    }
}
