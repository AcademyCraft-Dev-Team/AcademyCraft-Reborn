package org.academy.internal.gui.map;

import org.academy.internal.common.ability.teleport.ChunkLeapPackets;
import org.junit.jupiter.api.Test;

import java.util.HashSet;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ChunkMapRendererGeometryTest {
    @Test
    void chunkSizeIsClampedToAHitTestableMinimum() {
        var zoomedOut = new ChunkMapRenderer.Geometry(0, 0, 400, 300, 0, 0, 0.001);
        assertTrue(zoomedOut.chunkSize() >= 2.0, "chunks must stay pickable when zoomed far out");
    }

    @Test
    void screenCoordinatesAreCentredOnTheViewCentre() {
        var g = new ChunkMapRenderer.Geometry(0, 0, 320, 240, 10.0, 20.0, 1.0);
        // The exact centre of the viewport is the centre of chunk (10, 20).
        assertEquals(160.0, g.screenX(10.0), 1.0e-9);
        assertEquals(120.0, g.screenZ(20.0), 1.0e-9);
    }

    @Test
    void screenCoordinatesAdvanceByChunkSize() {
        var g = new ChunkMapRenderer.Geometry(0, 0, 320, 240, 0.0, 0.0, 1.0);
        assertEquals(g.chunkSize(), g.screenX(1.0) - g.screenX(0.0), 1.0e-9);
        assertEquals(g.chunkSize(), g.screenZ(1.0) - g.screenZ(0.0), 1.0e-9);
    }

    @Test
    void zoomScalesTheChunkSize() {
        var one = new ChunkMapRenderer.Geometry(0, 0, 320, 240, 0, 0, 1.0);
        var two = new ChunkMapRenderer.Geometry(0, 0, 320, 240, 0, 0, 2.0);
        assertEquals(one.chunkSize() * 2.0, two.chunkSize(), 1.0e-9);
    }

    @Test
    void markerColourIsDistinctPerCategory() {
        var seen = new HashSet<Integer>();
        for (byte category = 0; category < ChunkLeapPackets.CATEGORY_COUNT; category++) {
            assertTrue(seen.add(ChunkMapRenderer.markerColor(category)),
                    "category " + category + " must not reuse another category's colour");
        }
    }
}
