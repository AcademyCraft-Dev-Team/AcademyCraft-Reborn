package org.academy.internal.server.misaka;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

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
        var networkId = UUID.randomUUID();
        float msk = 100f;
        index.testingApplyMskDelta(networkId, "Alice", msk);
        assertEquals(msk, index.networkTotals().get(networkId), 0.001f);
        assertEquals(
                msk,
                index.benevolentGroups().get(new MisakaComputeIndex.NetworkGroupKey(networkId, "Alice")),
                0.001f
        );

        index.testingApplyMskDelta(networkId, "Alice", -msk);
        assertTrue(index.networkTotals().isEmpty()
                || !index.networkTotals().containsKey(networkId)
                || index.networkTotals().getOrDefault(networkId, 0f) <= 0f);
        assertTrue(!index.benevolentGroups().containsKey(new MisakaComputeIndex.NetworkGroupKey(networkId, "Alice")));

        index.testingApplyMskDelta(networkId, "Alice", msk);
        assertEquals(msk, index.networkTotals().get(networkId), 0.001f);
    }

    @Test
    void multiNameGroupKeyRoundsTrip() {
        String key = "Alice" + MisakaComputeIndex.GROUP_KEY_SEP + "Bob";
        assertEquals(List.of("Alice", "Bob"), MisakaComputeIndex.parseGroupKey(key));
        assertEquals(List.of(), MisakaComputeIndex.parseGroupKey(""));
        assertEquals(List.of(), MisakaComputeIndex.parseGroupKey(null));
    }
}
