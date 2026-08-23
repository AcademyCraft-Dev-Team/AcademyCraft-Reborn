package org.academy.api.common.ability.darkmatter;

import io.netty.buffer.Unpooled;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DarkmatterBlockProfileTest {
    @Test
    void constructorClampsUntrustedPhysicalParameters() {
        var maximum = new DarkmatterBlockProfile(
                Float.POSITIVE_INFINITY, 9_999.0f, true);
        assertEquals(DarkmatterBlockProfile.DEFAULT.hardness(), maximum.hardness(), 0.0f);
        assertEquals(DarkmatterBlockProfile.MAX_EXPLOSION_RESISTANCE,
                maximum.explosionResistance(), 0.0f);
        assertTrue(maximum.gravity());

        var minimum = new DarkmatterBlockProfile(-10.0f, -1.0f, false);
        assertEquals(DarkmatterBlockProfile.MIN_HARDNESS, minimum.hardness(), 0.0f);
        assertEquals(DarkmatterBlockProfile.MIN_EXPLOSION_RESISTANCE,
                minimum.explosionResistance(), 0.0f);
        assertFalse(minimum.gravity());
    }

    @Test
    void networkProfileRoundTripsEveryConfigurableField() {
        var expected = new DarkmatterBlockProfile(17.5f, 640.0f, true);
        var buffer = Unpooled.buffer();
        DarkmatterBlockProfile.STREAM_CODEC.encode(buffer, expected);
        assertEquals(expected, DarkmatterBlockProfile.STREAM_CODEC.decode(buffer));
    }
}
