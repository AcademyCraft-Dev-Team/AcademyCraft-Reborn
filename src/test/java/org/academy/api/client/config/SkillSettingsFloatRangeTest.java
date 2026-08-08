package org.academy.api.client.config;

import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class SkillSettingsFloatRangeTest {
    @Test
    void quantizesAndClampsReusableFloatSettings() {
        var value = new AtomicReference<>(1.0f);
        var entry = new SkillSettingsRegistry.FloatRange(
                "intensity",
                "test.intensity",
                0.0f,
                1.0f,
                0.05f,
                value::get,
                value::set,
                () -> { }
        );

        assertEquals(0.55f, entry.quantize(0.53f), 0.0001f);
        assertEquals(0.0f, entry.quantize(-4.0f), 0.0001f);
        assertEquals(1.0f, entry.quantize(4.0f), 0.0001f);
    }

    @Test
    void rejectsInvalidStep() {
        assertThrows(IllegalArgumentException.class, () -> new SkillSettingsRegistry.FloatRange(
                "intensity",
                "test.intensity",
                0.0f,
                1.0f,
                0.0f,
                () -> 1.0f,
                _ -> { },
                () -> { }
        ));
    }
}
