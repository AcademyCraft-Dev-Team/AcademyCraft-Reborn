package org.academy.internal.common.ability.teleport;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.ChunkPos;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Covers the entity relocation decision: an entity standing in one swapped chunk must be shifted by
 * exactly the offset that maps that chunk onto its partner, and an unrelated entity must not move.
 *
 * <p>The live in-world path is exercised by {@code ChunkSwapGameTests}; this pins the mapping itself,
 * which is where an off-by-one or a swapped delta would silently corrupt entity positions.
 */
class ChunkRegionSwapDeltaTest {
    private static final ChunkPos CHUNK_A = new ChunkPos(10, 10);
    private static final ChunkPos CHUNK_B = new ChunkPos(14, 7);
    /** The offset applied to entities in A, and its inverse for entities in B. */
    private static final BlockPos DELTA_A_TO_B = new BlockPos(4 << 4, 0, -3 << 4);
    private static final BlockPos DELTA_B_TO_A = DELTA_A_TO_B.multiply(-1);

    @Test
    void entityInFirstChunkMovesByForwardDelta() {
        assertEquals(DELTA_A_TO_B, ChunkRegionSwap.deltaFor(
                CHUNK_A, CHUNK_A, CHUNK_B, DELTA_A_TO_B, DELTA_B_TO_A));
    }

    @Test
    void entityInSecondChunkMovesByInverseDelta() {
        assertEquals(DELTA_B_TO_A, ChunkRegionSwap.deltaFor(
                CHUNK_B, CHUNK_A, CHUNK_B, DELTA_A_TO_B, DELTA_B_TO_A));
    }

    @Test
    void unrelatedEntityDoesNotMove() {
        assertEquals(BlockPos.ZERO, ChunkRegionSwap.deltaFor(
                new ChunkPos(999, 999), CHUNK_A, CHUNK_B, DELTA_A_TO_B, DELTA_B_TO_A));
    }

    @Test
    void deltasAreExactInversesSoASecondSwapRestoresPositions() {
        for (var x = -2; x <= 2; x++) {
            for (var z = -2; z <= 2; z++) {
                var origin = new BlockPos(x * 16 + 8, 64, z * 16 + 8);
                var there = origin.offset(DELTA_A_TO_B.getX(), 0, DELTA_A_TO_B.getZ());
                var back = there.offset(DELTA_B_TO_A.getX(), 0, DELTA_B_TO_A.getZ());
                assertEquals(origin, back, "double swap must be a no-op at " + x + "," + z);
            }
        }
    }
}
