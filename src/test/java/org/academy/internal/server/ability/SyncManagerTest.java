package org.academy.internal.server.ability;

import org.academy.api.common.ability.SyncTypes;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SyncManagerTest {
    @Test
    void ignoresLateSyncRequestsAfterPlayerLogout() {
        var manager = new SyncManager(null);
        var playerId = UUID.randomUUID();

        assertFalse(manager.schedulePlayerSync(playerId, SyncTypes.SKILL_DATA));

        manager.registerPlayer(playerId);
        assertTrue(manager.schedulePlayerSync(playerId, SyncTypes.SKILL_DATA));

        manager.unregisterPlayer(playerId);
        assertFalse(manager.schedulePlayerSync(playerId, SyncTypes.SKILL_DATA));
    }

    @Test
    void rejectsInvalidSyncRequestsWithoutCreatingQueues() {
        var manager = new SyncManager(null);
        var playerId = UUID.randomUUID();

        manager.registerPlayer(playerId);
        assertFalse(manager.schedulePlayerSync(null, SyncTypes.SKILL_DATA));
        assertFalse(manager.schedulePlayerSync(playerId, null));
    }
}
