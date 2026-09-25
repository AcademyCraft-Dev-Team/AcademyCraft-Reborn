package org.academy.internal.common.ability.teleport;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ChunkLeapRegionTest {
    /**
     * Dimension keys are built from identifiers rather than {@code Level.OVERWORLD}: referencing the
     * constant initialises {@code Level}, which a plain JUnit run cannot bootstrap.
     */
    private static final ResourceKey<Level> OVERWORLD = key("minecraft:overworld");
    private static final ResourceKey<Level> NETHER = key("minecraft:the_nether");

    private static ResourceKey<Level> key(String id) {
        return ResourceKey.create(Registries.DIMENSION, Identifier.parse(id));
    }

    @Test
    void buildsFromTwoCornersRegardlessOfOrder() {
        var region = ChunkLeapRegion.of(OVERWORLD,
                new ChunkPos(10, 20),
                new ChunkPos(7, 24));
        assertEquals(7, region.minChunkX());
        assertEquals(20, region.minChunkZ());
        assertEquals(4, region.width());
        assertEquals(5, region.height());
        assertEquals(20, region.chunkCount());
        assertEquals(10, region.maxChunkX());
        assertEquals(24, region.maxChunkZ());
    }

    @Test
    void clampsSideLengthToTheHardLimit() {
        var region = ChunkLeapRegion.ofChunks(OVERWORLD, 0, 0, 500, 3);
        assertEquals(ChunkLeapRegion.MAX_SIDE, region.width());
        assertEquals(3, region.height());
    }

    @Test
    void intersectsOnlyWithinTheSameDimension() {
        var a = ChunkLeapRegion.ofChunks(OVERWORLD, 0, 0, 4, 4);
        var overlapping = ChunkLeapRegion.ofChunks(OVERWORLD, 2, 2, 4, 4);
        var disjoint = ChunkLeapRegion.ofChunks(OVERWORLD, 10, 10, 4, 4);
        var otherDimension = ChunkLeapRegion.ofChunks(NETHER, 0, 0, 4, 4);

        assertTrue(a.intersects(overlapping));
        assertFalse(a.intersects(disjoint));
        assertFalse(a.intersects(otherDimension));
        assertFalse(a.intersects(null));
    }

    @Test
    void touchingEdgesDoNotCountAsOverlap() {
        var a = ChunkLeapRegion.ofChunks(OVERWORLD, 0, 0, 2, 2);
        var adjacent = ChunkLeapRegion.ofChunks(OVERWORLD, 2, 0, 2, 2);
        assertFalse(a.intersects(adjacent));
    }

    @Test
    void shapeComparisonIgnoresPosition() {
        var a = ChunkLeapRegion.ofChunks(OVERWORLD, 0, 0, 3, 2);
        var sameShape = ChunkLeapRegion.ofChunks(NETHER, 50, 50, 3, 2);
        var rotated = ChunkLeapRegion.ofChunks(OVERWORLD, 0, 0, 2, 3);
        assertTrue(a.sameShape(sameShape));
        assertFalse(a.sameShape(rotated));
    }

    @Test
    void offsetsMapBetweenRegions() {
        var source = ChunkLeapRegion.ofChunks(OVERWORLD, 10, 10, 4, 4);
        var target = ChunkLeapRegion.ofChunks(OVERWORLD, -6, 30, 4, 4);
        assertEquals(-16, source.offsetChunkX(target));
        assertEquals(20, source.offsetChunkZ(target));
    }

    @Test
    void coordinateBoundsMatchChunkExtent() {
        var region = ChunkLeapRegion.ofChunks(OVERWORLD, 1, 2, 2, 2);
        assertEquals(16, region.minBlockX());
        assertEquals(32, region.minBlockZ());
        assertEquals(47, region.maxBlockX());
        assertEquals(63, region.maxBlockZ());
    }

    @Test
    void clampAreaShrinksRunawaySelectionsToTheCap() {
        // A drag that raced from chunk (0,0) to (500,400) must be cut down to the configured cap
        // before the client ever renders, validates or requests it.
        var runaway = ChunkLeapRegion.of(OVERWORLD,
                new ChunkPos(0, 0),
                new ChunkPos(500, 400));
        var clamped = runaway.clampArea(new ChunkPos(0, 0), 256);
        assertTrue(clamped.chunkCount() <= 256, "clamped region must respect the cap");
        assertEquals(0, clamped.minChunkX(), "anchor corner must stay fixed");
        assertEquals(0, clamped.minChunkZ(), "anchor corner must stay fixed");
    }

    @Test
    void clampAreaKeepsTheAnchorOnItsOriginalCorner() {
        // Dragging up-left from the anchor: the anchor is now the bottom-right corner and must stay.
        var region = ChunkLeapRegion.of(OVERWORLD,
                new ChunkPos(10, 10),
                new ChunkPos(3, 3));
        var anchor = new ChunkPos(10, 10);
        var clamped = region.clampArea(anchor, 4);
        assertTrue(clamped.chunkCount() <= 4);
        assertEquals(10, clamped.maxChunkX());
        assertEquals(10, clamped.maxChunkZ());
    }

    @Test
    void clampAreaLeavesSmallRegionsAlone() {
        var region = ChunkLeapRegion.ofChunks(OVERWORLD, 0, 0, 4, 4);
        assertEquals(region, region.clampArea(new ChunkPos(0, 0), 256));
    }
}
