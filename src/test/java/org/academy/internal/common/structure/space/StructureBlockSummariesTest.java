package org.academy.internal.common.structure.space;

import net.minecraft.core.BlockPos;
import org.academy.api.common.structure.StructureBlockSummary;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

class StructureBlockSummariesTest {
    @Test void oversizedAndOverflowingBoundsNeverReadCells() {
        for (var max : new BlockPos[]{new BlockPos(4096, 0, 0), new BlockPos(16, 16, 16), new BlockPos(Integer.MAX_VALUE, Integer.MAX_VALUE, Integer.MAX_VALUE)}) {
            assertTrue(StructureBlockSummaries.scan(BlockPos.ZERO, max, 4096, _ -> { fail("over-budget read"); return Optional.empty(); }).isEmpty());
        }
        assertTrue(StructureBlockSummaries.scan(new BlockPos(Integer.MIN_VALUE, 0, 0), new BlockPos(Integer.MAX_VALUE, 0, 0),
                4096, _ -> { fail("overflow read"); return Optional.empty(); }).isEmpty());
    }
    @Test void missingContentIsUnknownWhileEmptyBoundsAreKnownEmpty() {
        var reads = new AtomicInteger();
        assertTrue(StructureBlockSummaries.scan(BlockPos.ZERO, new BlockPos(1, 0, 0), 2,
                _ -> { reads.incrementAndGet(); return Optional.empty(); }).isEmpty());
        assertEquals(1, reads.get());
        assertEquals(new StructureBlockSummary(Map.of(), 0), StructureBlockSummaries.scan(new BlockPos(1, 0, 0), BlockPos.ZERO, 1,
                _ -> { fail("empty bounds read"); return Optional.empty(); }).orElseThrow());
        assertThrows(IllegalArgumentException.class, () -> StructureBlockSummaries.scan(BlockPos.ZERO, BlockPos.ZERO, 0, _ -> Optional.empty()));
        assertThrows(IllegalArgumentException.class, () -> new StructureBlockSummary(Map.of(), -1));
    }
}
