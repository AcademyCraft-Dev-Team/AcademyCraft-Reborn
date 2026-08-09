package org.academy.internal.common.ability.accelerator.reflection;

import org.junit.jupiter.api.Test;

import java.nio.ByteBuffer;
import java.util.Base64;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

class ReflectionHealthRecordCodecTest {
    private static final UUID PLAYER = UUID.fromString("00112233-4455-6677-8899-aabbccddeeff");

    @Test
    void xorBase64RecordRoundTripsWithoutStoringThePlainFloatBits() {
        var encoded = ReflectionHealthRecordCodec.encode(PLAYER, 12.5f);
        var plain = Base64.getEncoder().withoutPadding().encodeToString(
                ByteBuffer.allocate(Float.BYTES).putFloat(12.5f).array());

        assertNotEquals(plain, encoded);
        assertEquals(12.5f, ReflectionHealthRecordCodec.decode(PLAYER, encoded, 0.0f));
    }

    @Test
    void lockedHealthUsesTheHigherOfRecordedAndOriginalHealth() {
        assertEquals(12.0f, ReflectionHealthRecordCodec.lockedHealth(12.0f, 5.0f));
        assertEquals(18.0f, ReflectionHealthRecordCodec.lockedHealth(12.0f, 18.0f));
        assertEquals(0.0f, ReflectionHealthRecordCodec.lockedHealth(Float.NaN, -1.0f));
    }

    @Test
    void malformedRecordFallsBackWithoutExposingInvalidHealth() {
        assertEquals(7.0f, ReflectionHealthRecordCodec.decode(PLAYER, "not-base64", 7.0f));
    }

    @Test
    void imagineBreakerOnlyLowersHealthAndClampsAtZero() {
        assertEquals(7.0f, ReflectionHealthRecordCodec.loweredHealth(12.0f, 5.0f));
        assertEquals(0.0f, ReflectionHealthRecordCodec.loweredHealth(3.0f, 5.0f));
        assertEquals(12.0f, ReflectionHealthRecordCodec.loweredHealth(12.0f, 0.0f));
        assertEquals(12.0f, ReflectionHealthRecordCodec.loweredHealth(12.0f, -2.0f));
        assertEquals(12.0f, ReflectionHealthRecordCodec.loweredHealth(12.0f, Float.NaN));
    }
}
