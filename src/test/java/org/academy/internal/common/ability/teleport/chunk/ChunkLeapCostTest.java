package org.academy.internal.common.ability.teleport.chunk;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ChunkLeapCostTest {
    @Test
    void baseCostAppliesWithNoVolumeOrEntities() {
        assertEquals(ChunkLeapCost.BASE, ChunkLeapCost.forSwap(0, 0));
    }

    @Test
    void scalesLinearlyWithChunksAndEntities() {
        assertEquals(ChunkLeapCost.BASE + 4 * ChunkLeapCost.PER_CHUNK, ChunkLeapCost.forSwap(4, 0));
        assertEquals(ChunkLeapCost.BASE + 16 * ChunkLeapCost.PER_CHUNK + 3 * ChunkLeapCost.PER_ENTITY,
                ChunkLeapCost.forSwap(16, 3));
    }

    @Test
    void entityTeleportUsesItsOwnFlatCost() {
        assertEquals(ChunkLeapCost.ENTITY_TELEPORT, ChunkLeapCost.forEntityTeleport());
    }

    @Test
    void saturatesInsteadOfOverflowingOnAbsurdInput() {
        var cost = ChunkLeapCost.forSwap(Long.MAX_VALUE / 2, Long.MAX_VALUE / 2);
        assertTrue(cost > 0, "cost must stay positive rather than wrap negative");
        assertEquals(Integer.MAX_VALUE, cost);
    }

    @Test
    void negativeInputsAreClampedToTheBase() {
        assertEquals(ChunkLeapCost.BASE, ChunkLeapCost.forSwap(-5, -5));
    }
}
