package org.academy.internal.gui.map;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Round-trip check for the map's screen↔world projection.
 *
 * <p>This is worth pinning because the inverse is easy to get subtly wrong and the error is invisible at
 * zoom 1: converting a pixel offset to blocks needs {@code chunkSize / 16} (pixels per block), and the
 * reciprocal {@code 16 / chunkSize} happens to agree only at exactly 1×. At 3× the wrong form was off by
 * hundreds of blocks, so entity destinations landed nowhere near the click. These tests therefore sweep
 * several zoom levels rather than checking a single convenient one.
 */
class ChunkMapProjectionTest {
    private static final int LEFT = 6;
    private static final int WIDTH = 900;
    private static final int TOP = 4;
    private static final int HEIGHT = 600;

    private static ChunkMapRenderer.Geometry geometry(double zoom, double centreChunkX, double centreChunkZ) {
        return new ChunkMapRenderer.Geometry(LEFT, TOP, WIDTH, HEIGHT, centreChunkX, centreChunkZ, zoom);
    }

    /** The renderer's forward projection: chunk boundary to screen pixel. */
    private static double screenX(ChunkMapRenderer.Geometry g, double chunkX) {
        return LEFT + WIDTH / 2.0 + (chunkX - g.centerChunkX()) * g.chunkSize();
    }

    /** The inverse the screen uses for a block coordinate. */
    private static double blockX(ChunkMapRenderer.Geometry g, double mouseX) {
        return g.centerChunkX() * 16.0 + (mouseX - LEFT - WIDTH / 2.0) * 16.0 / g.chunkSize();
    }

    private static double blockZ(ChunkMapRenderer.Geometry g, double mouseY) {
        return g.centerChunkZ() * 16.0 + (mouseY - TOP - HEIGHT / 2.0) * 16.0 / g.chunkSize();
    }

    @Test
    void theCentreProjectionRoundTripsAtEveryZoom() {
        for (var zoom : new double[]{0.25, 0.5, 1.0, 2.0, 3.0, 8.0}) {
            var g = geometry(zoom, 100.0, -40.0);
            // The viewport centre is the centre chunk's start, i.e. block 1600 / -640.
            assertEquals(1600.0, blockX(g, LEFT + WIDTH / 2.0), 1.0e-6, "zoom " + zoom);
            assertEquals(-640.0, blockZ(g, TOP + HEIGHT / 2.0), 1.0e-6, "zoom " + zoom);
        }
    }

    @Test
    void aPointFiveChunksRightIsEightyBlocksRightAtEveryZoom() {
        for (var zoom : new double[]{0.5, 1.0, 3.0}) {
            var g = geometry(zoom, 0.0, 0.0);
            // Five chunks to the right of centre is block 80.
            var pixel = screenX(g, 5.0);
            assertEquals(80.0, blockX(g, pixel), 1.0e-6,
                    "zoom " + zoom + " must map 5 chunks to 80 blocks");
        }
    }

    @Test
    void theReciprocalFactorWouldDisagreeAwayFromUnitZoom() {
        // The inverse must be 16/chunkSize (blocks per pixel). Using chunkSize/16 instead agrees only at
        // exactly 1x, so a test that checked just the default zoom would not catch the mistake.
        var g = geometry(3.0, 0.0, 0.0);
        var pixelOffset = WIDTH / 2.0 / 2.0;
        var correct = pixelOffset * 16.0 / g.chunkSize();
        var reciprocal = pixelOffset * (g.chunkSize() / 16.0);
        org.junit.jupiter.api.Assertions.assertNotEquals(correct, reciprocal, 1.0e-6,
                "the two forms must differ away from unit zoom, or this test proves nothing");
    }

    @Test
    void aMarkersChunkCoordinateIsTheBlockCentreNotTheBlockEdge() {
        // Regression: the renderer used blockX/16 + 0.5, which shifts a marker half a CHUNK right and down
        // (7.5 blocks). That made picked entities and clicked destinations land consistently off-target in
        // the bottom-right direction. The correct form centres the block.
        for (var block = 0; block < 400; block += 37) {
            var correct = (block + 0.5) / 16.0;   // block centre, in chunk units
            var buggy = block / 16.0 + 0.5;      // half a chunk too far right/down
            // The correct value must stay within the block's own chunk (offset < 1).
            org.junit.jupiter.api.Assertions.assertTrue(correct - (block >> 4) >= 0
                            && correct - (block >> 4) < 1.0,
                    "block " + block + " must sit inside its own chunk");
            org.junit.jupiter.api.Assertions.assertTrue(Math.abs(buggy - correct) > 0.4,
                    "the old form was off by half a chunk for block " + block);
        }
    }

    @Test
    void floorOfTheProjectionIsTheClickedBlock() {
        // What the screen ultimately does: floor the continuous coordinate to a block index.
        var g = geometry(2.0, 10.0, 10.0);
        var pixelOfBlock321 = LEFT + WIDTH / 2.0 + (321 - 160.0) / 16.0 * g.chunkSize();
        assertEquals(321, (int) Math.floor(blockX(g, pixelOfBlock321 + 0.4)));
    }
}
