package org.academy.internal.common.ability.teleport;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Selection validation. Equal size and matching shape are now guaranteed by construction (the target is
 * the source shape re-anchored), so these tests pin what remains checkable: emptiness, the size cap,
 * self-overlap, and the cross-dimension vertical requirement.
 */
class ChunkSwapServiceValidationTest {
    private static final ResourceKey<Level> OVERWORLD = key("minecraft:overworld");
    private static final ResourceKey<Level> NETHER = key("minecraft:the_nether");

    private static ResourceKey<Level> key(String id) {
        return ResourceKey.create(Registries.DIMENSION, Identifier.parse(id));
    }

    private static ChunkLeapSelection rectangle(ResourceKey<Level> dimension, int x, int z, int w, int h) {
        return ChunkLeapSelection.rectangle(dimension, new ChunkPos(x, z), new ChunkPos(x + w - 1, z + h - 1));
    }

    @Test
    void rejectsNullSelections() {
        assertEquals("chunk_leap.reason.invalid_region", ChunkSwapService.validateSelections(null, null));
        assertNotNull(ChunkSwapService.validateSelections(rectangle(OVERWORLD, 0, 0, 2, 2), null));
    }

    @Test
    void rejectsAnEmptySelection() {
        var empty = new ChunkLeapSelection(OVERWORLD, 0, 0, java.util.List.of());
        assertEquals("chunk_leap.reason.invalid_region",
                ChunkSwapService.validateSelections(empty, empty.anchoredAt(OVERWORLD, new ChunkPos(50, 50))));
    }

    @Test
    void rejectsAnOversizedSelection() {
        // Sized between the gameplay cap and the container cap, so it is representable but not allowed.
        var overCap = ChunkSwapService.MAX_SWAP_CHUNKS + 1;
        var offsets = new java.util.ArrayList<ChunkLeapSelection.Offset>(overCap);
        for (var i = 0; i < overCap; i++) {
            offsets.add(new ChunkLeapSelection.Offset(i % 60, i / 60));
        }
        var huge = new ChunkLeapSelection(OVERWORLD, 0, 0, offsets);
        assertEquals(overCap, huge.count(), "the container must keep a representable oversize selection");
        assertEquals("chunk_leap.reason.too_large",
                ChunkSwapService.validateSelections(huge, huge.anchoredAt(OVERWORLD, new ChunkPos(200, 200))));
    }

    @Test
    void theGameplayCapBindsBeforeTheContainerCap() {
        // If these were equal the size check could never fire, since construction truncates.
        assertTrue(ChunkSwapService.MAX_SWAP_CHUNKS < ChunkLeapSelection.MAX_CHUNKS);
    }

    @Test
    void rejectsATargetOnTopOfItsSource() {
        var source = rectangle(OVERWORLD, 10, 10, 4, 4);
        var samePlace = source.anchoredAt(OVERWORLD, new ChunkPos(10, 10));
        assertEquals("chunk_leap.reason.overlap", ChunkSwapService.validateSelections(source, samePlace));
    }

    @Test
    void acceptsADisjointSameDimensionTarget() {
        var source = rectangle(OVERWORLD, 0, 0, 4, 4);
        assertNull(ChunkSwapService.validateSelections(source,
                source.anchoredAt(OVERWORLD, new ChunkPos(100, 100))));
    }

    @Test
    void acceptsCrossDimensionBecauseTheSharedBandIsExchanged() {
        var source = rectangle(OVERWORLD, 0, 0, 2, 2);
        assertNull(ChunkSwapService.validateSelections(source,
                source.anchoredAt(NETHER, new ChunkPos(40, 40))));
    }

    @Test
    void targetShapeAndSizeAlwaysMatchTheSource() {
        // The derived target is the source shape re-anchored, so a mismatch is unrepresentable.
        var source = ChunkLeapSelection.of(OVERWORLD, java.util.List.of(
                new ChunkPos(5, 5), new ChunkPos(6, 5), new ChunkPos(9, 9)));
        var target = source.anchoredAt(NETHER, new ChunkPos(-40, 200));
        assertEquals(source.count(), target.count());
        assertEquals(source.offsets(), target.offsets());
    }



    @Test
    void ctrlClickStyleSelectionKeepsDisconnectedChunks() {
        // A non-rectangular selection is legal and must survive normalisation.
        var selection = ChunkLeapSelection.of(OVERWORLD, java.util.List.of(
                new ChunkPos(0, 0), new ChunkPos(5, 0), new ChunkPos(0, 7)));
        assertEquals(3, selection.count());
        assertTrue(selection.offsets().contains(new ChunkLeapSelection.Offset(5, 0)));
        assertTrue(selection.offsets().contains(new ChunkLeapSelection.Offset(0, 7)));
        // Normalised so the origin is the top-left corner.
        assertEquals(0, selection.originChunkX());
        assertEquals(0, selection.originChunkZ());
    }

    @Test
    void aSelectionIsShapedNotBounded() {
        // The bounding box of an L-shaped selection is larger than the selection itself, which is exactly
        // why the wire format carries offsets rather than a rectangle.
        var lShape = ChunkLeapSelection.of(OVERWORLD, java.util.List.of(
                new ChunkPos(0, 0), new ChunkPos(0, 1), new ChunkPos(0, 2), new ChunkPos(1, 2),
                new ChunkPos(2, 2)));
        assertEquals(5, lShape.count());
        assertEquals(9, lShape.bounds().chunkCount());
        assertFalse(lShape.bounds().chunkCount() == lShape.count());
    }
}
