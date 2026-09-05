package org.academy.internal.common.world.entity.misaka;

import net.minecraft.util.RandomSource;
import org.academy.internal.common.world.entity.misaka.perception.PerceptionService;
import org.academy.internal.server.misaka.MisakaComputeContribution;
import org.academy.internal.server.world.level.storage.MisakaSisterRecord;
import org.academy.internal.server.world.level.storage.MisakaSisterRoster;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class PerceptionServiceTest {
    @Test
    void dailyDecayUsesFormula() {
        var record = awakenedRecord(40);
        PerceptionService.applyDailyDecay(record);
        assertEquals(37, record.perception);
    }

    @Test
    void highTierFloorStaysAt101() {
        var record = awakenedRecord(105);
        record.highTierUnlocked = true;
        PerceptionService.applyDailyDecay(record);
        assertEquals(101, record.perception);
    }

    @Test
    void gainRespectsCapAndUnlocksHighTierAbove101() {
        var record = awakenedRecord(95);
        record.perceptionCap = PerceptionService.PROMAX_CAP;
        // 95 + 5 = 100: still within Promax tier, no high-tier unlock
        assertEquals(5, PerceptionService.gain(null, record, 5));
        assertEquals(100, record.perception);
        assertFalse(record.highTierUnlocked);
        assertEquals(PerceptionService.PROMAX_CAP, record.perceptionCap);

        // Crossing above 101 unlocks the 200 cap
        assertEquals(2, PerceptionService.gain(null, record, 2));
        assertEquals(102, record.perception);
        assertTrue(record.highTierUnlocked);
        assertEquals(PerceptionService.HIGH_TIER_CAP, record.perceptionCap);
    }

    @Test
    void gainWithoutServerOrNodeDoesNotClamp() {
        var record = awakenedRecord(95);
        record.perceptionCap = PerceptionService.PROMAX_CAP;
        assertNull(record.networkNodePos);
        assertEquals(7, PerceptionService.gain(null, record, 7));
        assertEquals(102, record.perception);
    }

    @Test
    void rosterModifyUpdatesRecord() {
        var roster = new MisakaSisterRoster();
        var record = roster.registerRescued(RandomSource.create(), 0);
        UUID id = record.misakaUuid;
        roster.modify(id, rec -> rec.perception = 50);
        assertEquals(50, roster.get(id).get().perception);
    }

    @Test
    void awakenSetsPerceptionToOne() {
        var record = new MisakaSisterRecord(UUID.randomUUID(), 100_001, MisakaPersonality.TIMID, 0);
        PerceptionService.awakenWithTower(record, "feeder", 1000L);
        assertTrue(record.awakened);
        assertEquals(1, record.perception);
        assertEquals(1, record.favorByPlayerName.get("feeder"));
        assertEquals(1000L + PerceptionService.AWAKE_WINDOW_TICKS, record.awakeWindowEndGameTime);
    }

    @Test
    void mskPerSecondAnchorsAt100And110And200() {
        assertEquals(100f, MisakaComputeContribution.mskPerSecond(100), 0.001f);
        assertEquals(120f, MisakaComputeContribution.mskPerSecond(110), 0.001f);
        assertEquals(400f, MisakaComputeContribution.mskPerSecond(200), 0.001f);
    }

    @Test
    void dailyDecayWithoutHighTierUsesFloorOne() {
        var record = awakenedRecord(5);
        PerceptionService.applyDailyDecay(record);
        assertEquals(4, record.perception);
        record.perception = 2;
        PerceptionService.applyDailyDecay(record);
        assertEquals(1, record.perception);
    }

    @Test
    void tryBreakLimitSetsPromaxCapAndMarksUsed() {
        var record = awakenedRecord(90);
        assertTrue(PerceptionService.tryBreakLimit(null, record));
        assertTrue(record.promaxUsed);
        assertEquals(PerceptionService.PROMAX_CAP, record.perceptionCap);
        assertEquals(100, record.perception);
        assertFalse(PerceptionService.tryBreakLimit(null, record));
    }

    @Test
    void tryBreakLimitRejectedBeforeAwaken() {
        var record = new MisakaSisterRecord(UUID.randomUUID(), 100_001, MisakaPersonality.TIMID, 0);
        assertFalse(PerceptionService.tryBreakLimit(null, record));
    }

    @Test
    void capLadderStopsAtBaseUntilPromax() {
        var record = awakenedRecord(99);
        assertEquals(1, PerceptionService.gain(null, record, 5));
        assertEquals(100, record.perception);
        assertEquals(PerceptionService.BASE_CAP, record.perceptionCap);
    }

    @Test
    void rosterModifyFavorWhileEntityUnloaded() {
        var roster = new MisakaSisterRoster();
        var record = roster.registerRescued(RandomSource.create(), 0);
        record.awakened = true;
        UUID id = record.misakaUuid;
        roster.modify(id, rec -> rec.favorByPlayerName.put("offline", 8));
        var updated = roster.get(id).orElseThrow();
        assertEquals(8, updated.favorByPlayerName.get("offline"));
        assertEquals(MobRelation.BENEVOLENT, org.academy.internal.common.world.entity.misaka.favor.FavorService.relation(updated, "offline"));
    }

    private static MisakaSisterRecord awakenedRecord(int perception) {
        var record = new MisakaSisterRecord(UUID.randomUUID(), 100_001, MisakaPersonality.TIMID, 0);
        record.awakened = true;
        record.perception = perception;
        return record;
    }
}
