package org.academy.internal.client.ability.teleport;

import org.academy.api.client.config.SkillSettingsRegistry;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class TeleportDistanceDisplayTest {
    @Test
    void distanceUsesBlockCountWhileExistingSlidersKeepPercentages() {
        var percentage = new SkillSettingsRegistry.FloatRange(
                "intensity", "", 0, 1, 0.05f, () -> 0.5f, _ -> {}, () -> {});
        var distance = new SkillSettingsRegistry.FloatRange(
                "distance", "", 0, 64, 1, () -> 12, _ -> {}, () -> {},
                value -> Integer.toString(Math.round(value)));
        assertEquals("50%", percentage.formatValue(0.5f));
        assertEquals("12", distance.formatValue(12));
        assertEquals("57", distance.formatValue(57));
        assertEquals(12, distance.quantize(12.3f));
    }
}
