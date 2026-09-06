package org.academy.internal.server.misaka;

import net.minecraft.core.BlockPos;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MisakaComputeIndexCoverageDeltaTest {
    private MisakaComputeIndex index;

    @BeforeEach
    void install() {
        index = new MisakaComputeIndex();
        MisakaComputeIndex.testingInstall(index);
    }

    @AfterEach
    void reset() {
        index.testingClearAggregates();
        MisakaComputeIndex.testingInstall(null);
    }

    @Test
    void inToOutSubtractsMskAndOutToInAddsBack() {
        var networkId = new BlockPos(1, 2, 3);
        float msk = 100f;
        index.testingApplyMskDelta(networkId, "Alice", msk);
        assertEquals(msk, index.networkTotals().get(networkId.immutable()), 0.001f);
        assertEquals(
                msk,
                index.closestGroups().get(new MisakaComputeIndex.NetworkClosestKey(networkId, "Alice")),
                0.001f
        );

        index.testingApplyMskDelta(networkId, "Alice", -msk);
        assertTrue(index.networkTotals().isEmpty()
                || !index.networkTotals().containsKey(networkId.immutable())
                || index.networkTotals().getOrDefault(networkId.immutable(), 0f) <= 0f);
        assertTrue(!index.closestGroups().containsKey(new MisakaComputeIndex.NetworkClosestKey(networkId, "Alice")));

        index.testingApplyMskDelta(networkId, "Alice", msk);
        assertEquals(msk, index.networkTotals().get(networkId.immutable()), 0.001f);
    }
}
