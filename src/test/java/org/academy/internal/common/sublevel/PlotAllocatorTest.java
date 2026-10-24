package org.academy.internal.common.sublevel;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class PlotAllocatorTest {
    @Test
    void firstFreeAllocationIsDeterministicAndReusable() {
        var layout = new PlotLayout(0, 100, -200, 8);
        var allocator = new PlotAllocator(layout);
        for (int index = 0; index < 8; index++) {
            var plot = allocator.allocate().orElseThrow();
            assertEquals(index % 3, plot.x());
            assertEquals(index / 3, plot.z());
        }
        assertTrue(allocator.allocate().isEmpty());
        assertEquals(8, allocator.allocated());
        assertTrue(allocator.free(new PlotPos(1, 0)));
        assertFalse(allocator.free(new PlotPos(1, 0)));
        assertEquals(new PlotPos(1, 0), allocator.allocate().orElseThrow());
        assertEquals(8, allocator.allocated());
    }

    @Test
    void layoutMapsPlotsToChunkOrigins() {
        var layout = new PlotLayout(2, 100, 200, 16);
        var plot = new PlotPos(2, 1);
        assertEquals(108, layout.chunkMinX(plot));
        assertEquals(204, layout.chunkMinZ(plot));
        assertEquals(4, layout.sideChunks());
        assertTrue(layout.containsChunk(111, 207));
        assertFalse(layout.containsChunk(116, 216));
    }
}
