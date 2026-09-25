package org.academy.internal.client.render.vfx;

import org.academy.internal.common.ability.electromaster.SkyStrikeProfile;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SkyStrikeFeedbackTest {
    @AfterEach
    void clearShakeState() {
        CameraShakeManager.clear();
    }

    @Test
    void distanceAttenuationClampsAtRangeBoundaries() {
        assertEquals(1.0f, SkyStrikeVfxClient.distanceAttenuation(0.0, 32.0), 0.0001f);
        assertEquals(0.5f, SkyStrikeVfxClient.distanceAttenuation(16.0, 32.0), 0.0001f);
        assertEquals(0.0f, SkyStrikeVfxClient.distanceAttenuation(32.0, 32.0), 0.0001f);
        assertEquals(0.0f, SkyStrikeVfxClient.distanceAttenuation(80.0, 32.0), 0.0001f);
    }

    @Test
    void overlappingStormShakesRemainWithinConfiguredCap() {
        var now = 1_000_000_000L;
        for (var i = 0; i < 21; i++) {
            CameraShakeManager.add(SkyStrikeProfile.LIGHTNING_STORM, i, 1.0f, 1.0f, now);
        }

        var offset = CameraShakeManager.sample(now + 25_000_000L);

        assertTrue(Math.abs(offset.yaw()) <= SkyStrikeProfile.LIGHTNING_STORM.shakeCapDegrees());
        assertTrue(Math.abs(offset.pitch()) <= SkyStrikeProfile.LIGHTNING_STORM.shakeCapDegrees());
    }
}
