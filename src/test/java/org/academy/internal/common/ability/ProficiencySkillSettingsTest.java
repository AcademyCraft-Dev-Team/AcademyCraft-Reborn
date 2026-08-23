package org.academy.internal.common.ability;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ProficiencySkillSettingsTest {
    @Test
    void miningBeamHarvestModeIsRestrictedToThreeChoices() {
        var option = ProficiencySkillSettings.MINING_BEAM_HARVEST_MODE;
        assertEquals(0, ProficiencySkillSettings.sanitizeMode(option, -1));
        assertEquals(1, ProficiencySkillSettings.sanitizeMode(option, 1));
        assertEquals(2, ProficiencySkillSettings.sanitizeMode(option, 3));
    }

    @Test
    void unknownModesFallBackToTheirDefault() {
        assertEquals(0, ProficiencySkillSettings.sanitizeMode("unknown", 2));
    }
}
