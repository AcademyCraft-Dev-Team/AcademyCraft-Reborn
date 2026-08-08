package org.academy.internal.client.renderer.effect;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WorldPostEffectSubmissionTest {
    @Test
    void onlyAcceptsGeometryInsideMainWorldEntitySubmission() {
        assertFalse(WorldPostEffectSubmission.isActive());

        WorldPostEffectSubmission.begin();
        assertTrue(WorldPostEffectSubmission.isActive());

        WorldPostEffectSubmission.begin();
        WorldPostEffectSubmission.end();
        assertTrue(WorldPostEffectSubmission.isActive());

        WorldPostEffectSubmission.end();
        assertFalse(WorldPostEffectSubmission.isActive());
    }
}
