package org.academy.internal.server.ability;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class AeromanipResourceManagerTest {
    @Test
    void compressedAirRecoversFourPerTickAndStopsAtCapacity() {
        assertEquals(68.0f, AeromanipResourceManager.recover(64.0f, 128.0f, 4.0f, true));
        assertEquals(128.0f, AeromanipResourceManager.recover(127.0f, 128.0f, 4.0f, true));
    }

    @Test
    void compressedAirDoesNotRecoverWhileUseIsActiveOrAirIsUnavailable() {
        assertEquals(64.0f, AeromanipResourceManager.recover(64.0f, 128.0f, 4.0f, false));
    }

    @Test
    void resourceValuesAreClampedToSafeRanges() {
        assertEquals(0, AeromanipResourceManager.normalizeCapacity(-5));
        assertEquals(0.0f, AeromanipResourceManager.normalizeRecovery(-5.0f));
        assertEquals(4.0f, AeromanipResourceManager.normalizeRecovery(Float.NaN));
    }
}
