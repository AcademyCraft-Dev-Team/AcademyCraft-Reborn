package org.academy.internal.common.ability.teleport;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit coverage for the vertical band that governs cross-dimension swaps.
 *
 * <p>The band is the whole point of the cross-dimension path: the Overworld and the Nether have
 * different section layouts, so only the shared absolute-Y range can move, and the band must be a
 * property of the levels rather than of their contents so that swapping twice restores the world.
 */
class ChunkVerticalBandTest {
    private static final int OVERWORLD_MIN = -64;
    private static final int OVERWORLD_MAX = 319;
    private static final int NETHER_MIN = 0;
    private static final int NETHER_MAX = 127;

    /**
     * Stands in for a level's height range without booting a real ServerLevel.
     */
    private record Range(int min, int max) {
    }

    private static ChunkVerticalBand overlap(Range first, Range second) {
        return new ChunkVerticalBand(Math.max(first.min(), second.min()),
                Math.min(first.max(), second.max()));
    }

    @Test
    void overlapOfOverworldAndNetherIsTheNarrowerRange() {
        var band = overlap(new Range(OVERWORLD_MIN, OVERWORLD_MAX), new Range(NETHER_MIN, NETHER_MAX));
        assertEquals(NETHER_MIN, band.minBlockY());
        assertEquals(NETHER_MAX, band.maxBlockY());
        assertEquals(128, band.height());
    }

    @Test
    void overlapIsSymmetric() {
        var forward = overlap(new Range(OVERWORLD_MIN, OVERWORLD_MAX), new Range(NETHER_MIN, NETHER_MAX));
        var reverse = overlap(new Range(NETHER_MIN, NETHER_MAX), new Range(OVERWORLD_MIN, OVERWORLD_MAX));
        assertEquals(forward, reverse, "the band must not depend on argument order");
    }

    @Test
    void bandIsUnchangedWhenAppliedTwiceSoSwapStaysSelfInverse() {
        // Swapping must use the same band on the second pass, or the untouched sections would drift.
        var first = overlap(new Range(OVERWORLD_MIN, OVERWORLD_MAX), new Range(NETHER_MIN, NETHER_MAX));
        var second = overlap(new Range(OVERWORLD_MIN, OVERWORLD_MAX), new Range(NETHER_MIN, NETHER_MAX));
        assertEquals(first, second);
    }

    @Test
    void disjointHeightsProduceAnEmptyBand() {
        var band = overlap(new Range(-64, -1), new Range(0, 127));
        assertTrue(band.isEmpty(), "no shared range means nothing can be exchanged");
        assertEquals(0, band.height());
    }

    @Test
    void sectionRangeIsInclusiveOnBothEnds() {
        var band = new ChunkVerticalBand(0, 127);
        assertEquals(0, band.minSectionY());
        assertEquals(7, band.maxSectionY());
        assertTrue(band.contains(0));
        assertTrue(band.contains(127));
        assertFalse(band.contains(-1));
        assertFalse(band.contains(128));
    }

    @Test
    void negativeMinimumYieldsNegativeSectionCoordinals() {
        var band = new ChunkVerticalBand(OVERWORLD_MIN, OVERWORLD_MAX);
        assertEquals(-4, band.minSectionY());
        assertEquals(19, band.maxSectionY());
        assertEquals(24, band.height() / 16, "24 sections span the overworld range");
    }
}
