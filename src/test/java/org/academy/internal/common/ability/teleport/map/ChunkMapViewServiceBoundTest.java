package org.academy.internal.common.ability.teleport.map;

import org.academy.internal.common.ability.teleport.chunk.ChunkLeapRegion;
import org.academy.internal.common.ability.teleport.map.ChunkMapViewService;


import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The view-distance bound is the load-safety contract for the map: it is what stops one player's map
 * page from pinning an arbitrary amount of terrain in memory.
 */
class ChunkMapViewServiceBoundTest {
    private static final ResourceKey<Level> OVERWORLD =
            ResourceKey.create(Registries.DIMENSION, Identifier.parse("minecraft:overworld"));

    @Test
    void aRequestWithinTheViewDistanceIsUntouched() {
        var requested = ChunkLeapRegion.ofChunks(OVERWORLD, 0, 0, 12, 12);
        var bounded = ChunkMapViewService.boundByViewDistance(12, requested);
        assertEquals(requested, bounded);
    }

    @Test
    void anOversizedRequestIsShrunkAroundItsCentre() {
        var requested = ChunkLeapRegion.ofChunks(OVERWORLD, 0, 0, 64, 64);
        var bounded = ChunkMapViewService.boundByViewDistance(10, requested);
        // A view distance below the usable minimum is raised to it: a map of two chunks would be
        // useless, and the minimum is still trivial to load.
        assertEquals(16, bounded.width());
        assertEquals(16, bounded.height());
        // Centre preserved: (0 + 64/2) - 16/2 = 24.
        assertEquals(24, bounded.minChunkX());
        assertEquals(24, bounded.minChunkZ());
    }

    @Test
    void aLargerViewDistanceGivesAProportionallyLargerRegion() {
        var requested = ChunkLeapRegion.ofChunks(OVERWORLD, 0, 0, 64, 64);
        assertEquals(24, ChunkMapViewService.boundByViewDistance(24, requested).width());
    }

    @Test
    void aTinyViewDistanceStillGetsAUsableRegion() {
        // A player with view distance 2 should still be able to read a small area rather than a sliver.
        var requested = ChunkLeapRegion.ofChunks(OVERWORLD, 0, 0, 64, 64);
        var bounded = ChunkMapViewService.boundByViewDistance(2, requested);
        assertTrue(bounded.width() >= 2, "should not collapse below a couple of chunks");
        assertTrue(bounded.chunkCount() < requested.chunkCount(), "but must stay far smaller");
    }

    @Test
    void boundingNeverExceedsTheRequestedArea() {
        for (var viewDistance = 2; viewDistance <= 32; viewDistance++) {
            var requested = ChunkLeapRegion.ofChunks(OVERWORLD, 0, 0, 64, 64);
            var bounded = ChunkMapViewService.boundByViewDistance(viewDistance, requested);
            assertTrue(bounded.chunkCount() <= requested.chunkCount(),
                    "viewDistance " + viewDistance + " must not enlarge the request");
            assertTrue(bounded.width() <= Math.max(viewDistance, 16),
                    "viewDistance " + viewDistance + " must bound the side");
        }
    }
}
