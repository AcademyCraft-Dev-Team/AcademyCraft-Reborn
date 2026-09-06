package org.academy.internal.common.world.entity.misaka;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.ChunkPos;
import org.academy.internal.common.world.entity.misaka.favor.FavorService;
import org.academy.internal.server.world.level.storage.MisakaSisterRecord;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class FavorServiceTest {
    @Test
    void unawakenedRelationIsIndifferentAndFavorLocked() {
        var record = new MisakaSisterRecord(UUID.randomUUID(), 100_001, MisakaPersonality.TIMID, 0);
        FavorService.modifyFavor(record, "player", 5);
        assertTrue(record.favorByPlayerName.isEmpty());
        assertEquals(MobRelation.INDIFFERENT, FavorService.relation(record, "player"));
    }

    @Test
    void privilegeRequiresLastInteractedBenevolent() {
        var record = awakenedRecord(100_001);
        record.favorByPlayerName.put("alice", 5);
        record.favorByPlayerName.put("bob", 5);
        record.lastInteractedBenevolentPlayerName = "bob";
        assertEquals(MobRelation.BENEVOLENT, FavorService.relation(record, "alice"));
        assertEquals(MobRelation.BENEVOLENT, FavorService.relation(record, "bob"));
        assertFalse(FavorService.isPrivilegePlayer(record, "alice"));
        assertTrue(FavorService.isPrivilegePlayer(record, "bob"));
    }

    @Test
    void favorDeadlocksAtNegativeTwenty() {
        var record = awakenedRecord(100_001);
        record.favorByPlayerName.put("player", -20);
        FavorService.modifyFavor(record, "player", -5);
        assertEquals(-20, record.favorByPlayerName.get("player"));
        FavorService.modifyFavor(record, "player", 10);
        assertEquals(-20, record.favorByPlayerName.get("player"));
        assertEquals(MobRelation.DEADLY_ENEMY, FavorService.relation(record, "player"));
    }

    @Test
    void defaultVersusBenevolentWhenNotMaxFavor() {
        var record = awakenedRecord(100_001);
        record.favorByPlayerName.put("alice", 3);
        record.favorByPlayerName.put("bob", 5);
        assertEquals(MobRelation.BENEVOLENT, FavorService.relation(record, "bob"));
        assertEquals(MobRelation.DEFAULT, FavorService.relation(record, "alice"));
    }

    @Test
    void lanIncludesSameNetworkRegardlessOfDistance() {
        var a = awakenedRecord(100_001);
        var b = awakenedRecord(100_002);
        var c = awakenedRecord(100_003);
        var node = new BlockPos(0, 64, 0);
        a.networkNodePos = node;
        b.networkNodePos = node;
        a.lastKnownChunk = new ChunkPos(0, 0);
        b.lastKnownChunk = new ChunkPos(100, 100);
        c.lastKnownChunk = new ChunkPos(0, 0);

        var component = FavorService.resolveLanComponent(
                a,
                List.of(a, b, c),
                record -> record.networkNodePos,
                record -> record.lastKnownChunk
        );
        assertTrue(component.contains(a));
        assertTrue(component.contains(b));
        assertTrue(component.contains(c), "unbound sister within 4 chunks of a should join via proximity");
    }

    @Test
    void lanBridgesDistinctNetworksWithinFourChunks() {
        var a = awakenedRecord(100_001);
        var b = awakenedRecord(100_002);
        var far = awakenedRecord(100_003);
        a.networkNodePos = new BlockPos(0, 64, 0);
        b.networkNodePos = new BlockPos(8, 64, 8);
        far.networkNodePos = new BlockPos(1000, 64, 1000);
        a.lastKnownChunk = new ChunkPos(0, 0);
        b.lastKnownChunk = new ChunkPos(3, 0);
        far.lastKnownChunk = new ChunkPos(50, 50);

        var component = FavorService.resolveLanComponent(
                a,
                List.of(a, b, far),
                record -> record.networkNodePos,
                record -> record.lastKnownChunk
        );
        assertEquals(2, component.size());
        assertTrue(component.contains(a));
        assertTrue(component.contains(b));
        assertFalse(component.contains(far));
    }

    @Test
    void lanAppliesDeltaOnceAcrossSeedUnion() {
        var a = awakenedRecord(100_001);
        var b = awakenedRecord(100_002);
        var node = new BlockPos(0, 64, 0);
        a.networkNodePos = node;
        b.networkNodePos = node;
        a.lastKnownChunk = new ChunkPos(0, 0);
        b.lastKnownChunk = new ChunkPos(0, 0);

        var applied = new HashSet<UUID>();
        for (var seed : List.of(a, b)) {
            for (var member : FavorService.resolveLanComponent(
                    seed,
                    List.of(a, b),
                    record -> record.networkNodePos,
                    record -> record.lastKnownChunk
            )) {
                if (applied.add(member.misakaUuid)) {
                    FavorService.modifyFavor(member, "killer", -2);
                }
            }
        }
        assertEquals(-2, a.favorByPlayerName.get("killer"));
        assertEquals(-2, b.favorByPlayerName.get("killer"));
    }

    @Test
    void distantNewNetworkStaysIsolated() {
        var oldNet = awakenedRecord(100_001);
        var newNet = awakenedRecord(100_002);
        oldNet.networkNodePos = new BlockPos(0, 64, 0);
        newNet.networkNodePos = new BlockPos(500, 64, 500);
        oldNet.lastKnownChunk = new ChunkPos(0, 0);
        newNet.lastKnownChunk = new ChunkPos(30, 30);

        FavorService.modifyFavor(oldNet, "villain", -5);
        var component = FavorService.resolveLanComponent(
                oldNet,
                List.of(oldNet, newNet),
                record -> record.networkNodePos,
                record -> record.lastKnownChunk
        );
        assertEquals(1, component.size());
        assertEquals(-5, oldNet.favorByPlayerName.get("villain"));
        assertNull(newNet.favorByPlayerName.get("villain"));
    }

    @Test
    void proximityDoesNotBridgeAcrossDimensions() {
        var overworld = ResourceKey.create(
                net.minecraft.core.registries.Registries.DIMENSION,
                net.minecraft.resources.Identifier.withDefaultNamespace("overworld")
        );
        var nether = ResourceKey.create(
                net.minecraft.core.registries.Registries.DIMENSION,
                net.minecraft.resources.Identifier.withDefaultNamespace("the_nether")
        );
        var a = awakenedRecord(100_001);
        var b = awakenedRecord(100_002);
        a.lastKnownChunk = new ChunkPos(0, 0);
        b.lastKnownChunk = new ChunkPos(1, 0);
        a.lastKnownDimension = overworld;
        b.lastKnownDimension = nether;

        var component = FavorService.resolveLanComponent(
                a,
                List.of(a, b),
                record -> null,
                record -> record.lastKnownChunk,
                record -> record.lastKnownDimension
        );
        assertEquals(1, component.size());
        assertTrue(component.contains(a));
        assertFalse(component.contains(b));
    }

    private static MisakaSisterRecord awakenedRecord(int serial) {
        var record = new MisakaSisterRecord(UUID.randomUUID(), serial, MisakaPersonality.TIMID, 0);
        record.awakened = true;
        return record;
    }
}
