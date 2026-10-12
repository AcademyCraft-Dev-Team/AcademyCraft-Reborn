package org.academy.internal.client.structure;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import org.academy.api.common.structure.StructureRef;
import org.academy.internal.common.network.NativeStructureMiningProgressPacket;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class NativeStructureMiningUpdatesTest {
    @Test
    void oldProgressCannotReviveATerminatedRequestAndOldAbortCannotClearANewerRequest() {
        var updates = new NativeStructureMiningUpdates();
        assertTrue(updates.accept(packet(1, 1, 10, 4, false), 0, true));
        assertTrue(updates.accept(packet(1, 1, 11, -1, true), 1, true));
        assertFalse(updates.accept(packet(1, 1, 10, 4, false), 2, true));
        assertTrue(updates.active(2).isEmpty());
        assertTrue(updates.accept(packet(1, 2, 12, 0, false), 3, true));
        assertFalse(updates.accept(packet(1, 1, 11, -1, true), 4, true));
        assertEquals(2, updates.active(4).getFirst().requestId());
    }

    @Test
    void expirationAndLocalAbortRetainOrderingWatermarks() {
        var updates = new NativeStructureMiningUpdates();
        updates.accept(packet(1, 1, 10, 4, false), 0, true);
        assertTrue(updates.active(NativeStructureMiningUpdates.VISUAL_TTL + 1).isEmpty());
        assertFalse(updates.accept(packet(1, 1, 9, 3, false), 110, true));
        updates.accept(packet(1, 1, 11, 5, false), 111, true);
        updates.clearVisual(1);
        assertTrue(updates.active(112).isEmpty());
        assertFalse(updates.accept(packet(1, 1, 11, 5, false), 112, true));
    }

    @Test
    void unrelatedOwnRequestAcknowledgementUpdatesWatermarkWithoutReplacingCurrentVisual() {
        var updates = new NativeStructureMiningUpdates();
        updates.acceptOwn(packet(1, 2, 20, 3, false), 1, true);
        updates.acceptOwn(packet(1, 1, 21, -1, true), 2, false);
        assertEquals(2, updates.active(2).getFirst().requestId());
        assertFalse(updates.acceptOwn(packet(1, 2, 20, 3, false), 3, true));
        assertTrue(updates.acceptOwn(packet(1, 2, 22, 4, false), 4, true));
    }

    @Test
    void breakerBudgetCannotGrowOrEvictLiveOrderingHistory() {
        var updates = new NativeStructureMiningUpdates();
        for (int breaker = 0; breaker < NativeStructureMiningUpdates.MAX_BREAKERS; breaker++) {
            assertTrue(updates.accept(packet(breaker, 1, 1, 1, false), 0, true));
        }
        assertFalse(updates.accept(packet(NativeStructureMiningUpdates.MAX_BREAKERS, 1, 1, 1, false), 1, true));
        assertTrue(updates.accept(packet(0, 1, 2, -1, true), 1, true));
        assertEquals(NativeStructureMiningUpdates.MAX_BREAKERS - 1, updates.active(1).size());
    }

    @Test
    void localConnectionKeepsAReservedWatermarkAcrossRespawnWhenRemoteBudgetIsFull() {
        var updates = new NativeStructureMiningUpdates();
        for (int breaker = 0; breaker < NativeStructureMiningUpdates.MAX_BREAKERS; breaker++) {
            assertTrue(updates.accept(packet(breaker, 1, 1, 1, false), 0, true));
        }
        assertTrue(updates.acceptOwn(packet(1000, 1, 10, 3, false), 1, true));
        updates.clearOwnVisual();
        assertTrue(updates.acceptOwn(packet(1001, 2, 11, 4, false), 2, true));
        assertFalse(updates.acceptOwn(packet(1000, 1, 10, 3, false), 3, true));
        assertEquals(NativeStructureMiningUpdates.MAX_BREAKERS + 1, updates.active(3).size());
        assertTrue(updates.active(3).stream().anyMatch(packet -> packet.breakerEntityId() == 1001 && packet.stage() == 4));
        assertTrue(updates.acceptOwn(packet(1001, 2, 12, -1, true), 4, true));
        assertEquals(NativeStructureMiningUpdates.MAX_BREAKERS, updates.active(4).size());
        assertFalse(updates.acceptOwn(packet(1001, 2, 11, 4, false), 5, true));
    }

    private static NativeStructureMiningProgressPacket packet(int breaker, long request, long sequence, int stage, boolean terminal) {
        var reference = new StructureRef(new UUID(1, 2), Identifier.withDefaultNamespace("overworld"),
                Identifier.fromNamespaceAndPath("academy", "native_level"), 0);
        return new NativeStructureMiningProgressPacket(request, sequence, breaker, reference, new UUID(3, 4),
                new BlockPos(0, 80, 0), 1, stage, terminal ? NativeStructureMiningProgressPacket.Status.ABORTED
                : NativeStructureMiningProgressPacket.Status.PROGRESS);
    }
}
