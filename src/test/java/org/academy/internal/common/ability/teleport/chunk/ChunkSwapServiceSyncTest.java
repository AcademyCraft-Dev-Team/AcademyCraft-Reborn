package org.academy.internal.common.ability.teleport.chunk;

import org.academy.internal.common.ability.teleport.chunk.ChunkSwapService;


import net.minecraft.world.level.ChunkPos;
import org.junit.jupiter.api.Test;

import java.util.HashSet;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ChunkSwapServiceSyncTest {
    @Test
    void carrierRecoverySendsTheCenterChunkFirst() {
        var center = new ChunkPos(41, -17);
        var order = ChunkSwapService.carrierResyncOrder(center, 1);

        assertEquals(center, order.getFirst());
    }

    @Test
    void carrierRecoveryContainsOneBoundedThreeByThreeNeighbourhood() {
        var center = new ChunkPos(41, -17);
        var order = ChunkSwapService.carrierResyncOrder(center, 1);

        assertEquals(9, order.size());
        assertEquals(9, new HashSet<>(order).size());
        for (var pos : order) {
            assertTrue(Math.abs(pos.x() - center.x()) <= 1);
            assertTrue(Math.abs(pos.z() - center.z()) <= 1);
        }
    }

    @Test
    void clientViewDistanceCannotExpandCarrierRecovery() {
        var center = new ChunkPos(41, -17);

        assertEquals(9, ChunkSwapService.carrierResyncOrder(center, 12).size());
    }

    @Test
    void zeroRadiusStillRepairsTheCollisionChunk() {
        var center = new ChunkPos(41, -17);

        assertEquals(java.util.List.of(center), ChunkSwapService.carrierResyncOrder(center, 0));
    }
}
