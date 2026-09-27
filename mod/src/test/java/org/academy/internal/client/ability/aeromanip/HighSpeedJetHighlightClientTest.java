package org.academy.internal.client.ability.aeromanip;

import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HighSpeedJetHighlightClientTest {
    @Test
    void highlightsPreviewAndPersistentEntityTargetsOnly() {
        assertTrue(HighSpeedJetHighlightClient.containsEntityId(7, 7, Set.of()));
        assertTrue(HighSpeedJetHighlightClient.containsEntityId(7, -1, Set.of(7, 9)));
        assertFalse(HighSpeedJetHighlightClient.containsEntityId(7, -1, Set.of(9)));
        assertFalse(HighSpeedJetHighlightClient.containsEntityId(-1, -1, Set.of(-1)));
    }
}
