package org.academy.internal.common.ability.teleport.chunk;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A selection stores chunk <em>offsets</em> from its own corner rather than a rectangle. That is what makes
 * disconnected ctrl-clicked selections and one-click target derivation both work, and it is what makes the
 * two sides of a swap identical in shape by construction.
 */
class ChunkLeapSelectionTest {
    private static final ResourceKey<Level> OVERWORLD =
            ResourceKey.create(Registries.DIMENSION, Identifier.parse("minecraft:overworld"));
    private static final ResourceKey<Level> NETHER =
            ResourceKey.create(Registries.DIMENSION, Identifier.parse("minecraft:the_nether"));

    @Test
    void aRectangleSelectionCoversEveryCell() {
        var selection = ChunkLeapSelection.rectangle(OVERWORLD, new ChunkPos(10, 20), new ChunkPos(12, 21));
        assertEquals(6, selection.count());
        assertEquals(10, selection.originChunkX());
        assertEquals(20, selection.originChunkZ());
        assertTrue(selection.chunks().contains(new ChunkPos(12, 21)));
    }

    @Test
    void anArbitrarySelectionIsNormalisedToItsTopLeftCorner() {
        var selection = ChunkLeapSelection.of(OVERWORLD,
                List.of(new ChunkPos(50, 60), new ChunkPos(48, 60), new ChunkPos(50, 55)));
        assertEquals(48, selection.originChunkX());
        assertEquals(55, selection.originChunkZ());
        assertEquals(3, selection.count());
        // Offsets must be non-negative, or the origin would not be the corner.
        for (var offset : selection.offsets()) {
            assertTrue(offset.dx() >= 0 && offset.dz() >= 0);
        }
    }

    @Test
    void reanchoringReusesTheOffsetsSoTheTargetMatchesTheSourceExactly() {
        var source = ChunkLeapSelection.of(OVERWORLD, List.of(
                new ChunkPos(0, 0), new ChunkPos(4, 0), new ChunkPos(4, 4)));
        var target = source.anchoredAt(NETHER, new ChunkPos(-100, 300));
        assertEquals(source.offsets(), target.offsets(), "the shape must be identical");
        assertEquals(source.count(), target.count());
        assertEquals(NETHER, target.dimension());
        // Offset i of the source must pair with offset i of the target.
        assertEquals(new ChunkPos(-100, 300), target.chunkAt(0));
        assertEquals(new ChunkPos(-96, 304), target.chunkAt(2));
    }

    @Test
    void duplicatesCollapse() {
        var selection = ChunkLeapSelection.of(OVERWORLD,
                List.of(new ChunkPos(1, 1), new ChunkPos(1, 1), new ChunkPos(2, 1)));
        assertEquals(2, selection.count());
    }

    @Test
    void anEmptySequenceYieldsNoSelection() {
        assertNull(ChunkLeapSelection.of(OVERWORLD, List.of()));
    }

    @Test
    void boundingBoxIsReportedSeparatelyFromTheSelection() {
        // An L shape has a bounding box larger than itself; the wire format must not be a rectangle.
        var lShape = ChunkLeapSelection.of(OVERWORLD, List.of(
                new ChunkPos(0, 0), new ChunkPos(0, 1), new ChunkPos(1, 1)));
        assertEquals(3, lShape.count());
        assertEquals(4, lShape.bounds().chunkCount());
        assertNotNull(lShape.bounds());
    }

    @Test
    void aSelectionSpanningBeyondTheOffsetLimitDropsTheFarChunks() {
        var selection = ChunkLeapSelection.of(OVERWORLD, List.of(
                new ChunkPos(0, 0), new ChunkPos(ChunkLeapSelection.MAX_OFFSET + 5, 0)));
        assertEquals(1, selection.count(), "the unrepresentable chunk must be dropped, not wrapped");
    }

    @Test
    void aSingleChunkSelectionIsJustAnOffset() {
        var selection = ChunkLeapSelection.single(OVERWORLD, new ChunkPos(7, -9));
        assertEquals(1, selection.count());
        assertEquals(new ChunkPos(7, -9), selection.chunkAt(0));
    }
}
