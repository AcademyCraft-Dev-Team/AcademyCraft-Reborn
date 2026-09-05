package org.academy.internal.common.world.entity.misaka;

import org.academy.internal.common.world.entity.misaka.favor.FavorService;
import org.academy.internal.common.world.entity.misaka.perception.PerceptionService;
import org.academy.internal.server.world.level.storage.MisakaSisterRecord;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class InteractionGateTest {
    @Test
    void unawakenedOnlyAllowsLeashAndTowerAwaken() {
        var record = new MisakaSisterRecord(UUID.randomUUID(), 100_001, MisakaPersonality.TIMID, 0);
        assertTrue(InteractionGate.allow(record, "p", InteractionGate.Intent.LEASH));
        assertTrue(InteractionGate.allow(record, "p", InteractionGate.Intent.FEED_TOWER_AWAKEN));
        assertFalse(InteractionGate.allow(record, "p", InteractionGate.Intent.FEED_FOOD));
        assertFalse(InteractionGate.allow(record, "p", InteractionGate.Intent.PET));
        assertFalse(InteractionGate.allow(record, "p", InteractionGate.Intent.PANEL));
        assertFalse(InteractionGate.allow(record, "p", InteractionGate.Intent.STATE));
        assertFalse(InteractionGate.allow(record, "p", InteractionGate.Intent.PICKUP));
    }

    @Test
    void defaultCanPetPanelFeedButNotPrivilegeActions() {
        var record = awakened();
        record.favorByPlayerName.put("alice", 3);
        record.favorByPlayerName.put("bob", 5);
        assertTrue(InteractionGate.allow(record, "alice", InteractionGate.Intent.PET));
        assertTrue(InteractionGate.allow(record, "alice", InteractionGate.Intent.PANEL));
        assertTrue(InteractionGate.allow(record, "alice", InteractionGate.Intent.FEED_FOOD));
        assertFalse(InteractionGate.allow(record, "alice", InteractionGate.Intent.STATE));
        assertFalse(InteractionGate.allow(record, "alice", InteractionGate.Intent.PICKUP));
    }

    @Test
    void privilegeNeedsBenevolentAndLastInteracted() {
        var record = awakened();
        record.favorByPlayerName.put("alice", 5);
        record.favorByPlayerName.put("bob", 5);
        record.lastInteractedBenevolentPlayerName = "bob";
        assertTrue(InteractionGate.allow(record, "bob", InteractionGate.Intent.STATE));
        assertTrue(InteractionGate.allow(record, "bob", InteractionGate.Intent.PICKUP));
        assertFalse(InteractionGate.allow(record, "alice", InteractionGate.Intent.STATE));
    }

    @Test
    void touchBenevolentNoOpForNonBenevolent() {
        var record = awakened();
        record.favorByPlayerName.put("alice", 3);
        record.favorByPlayerName.put("bob", 5);
        record.lastInteractedBenevolentPlayerName = "bob";
        InteractionGate.touchBenevolent(record, "alice");
        assertEquals("bob", record.lastInteractedBenevolentPlayerName);
        assertFalse(FavorService.isPrivilegePlayer(record, "alice"));
    }

    @Test
    void touchBenevolentUpdatesPrivilegeName() {
        var record = awakened();
        record.favorByPlayerName.put("alice", 5);
        record.favorByPlayerName.put("bob", 5);
        record.lastInteractedBenevolentPlayerName = "bob";
        InteractionGate.touchBenevolent(record, "alice");
        assertEquals("alice", record.lastInteractedBenevolentPlayerName);
        assertTrue(FavorService.isPrivilegePlayer(record, "alice"));
        assertFalse(FavorService.isPrivilegePlayer(record, "bob"));
    }

    @Test
    void awakenGrantsFavorAndTouchSetsPrivilege() {
        var record = new MisakaSisterRecord(UUID.randomUUID(), 100_001, MisakaPersonality.TIMID, 0);
        PerceptionService.awakenWithTower(record, "feeder", 2000L);
        InteractionGate.touchBenevolent(record, "feeder");
        assertEquals(MobRelation.BENEVOLENT, FavorService.relation(record, "feeder"));
        assertTrue(FavorService.isPrivilegePlayer(record, "feeder"));
        assertEquals(1, record.perception);
        assertEquals(2000L + PerceptionService.AWAKE_WINDOW_TICKS, record.awakeWindowEndGameTime);
    }

    private static MisakaSisterRecord awakened() {
        var record = new MisakaSisterRecord(UUID.randomUUID(), 100_001, MisakaPersonality.TIMID, 0);
        record.awakened = true;
        return record;
    }
}
