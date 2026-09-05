package org.academy.internal.server.misaka;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

class MisakaComputeSettleTest {
    @Test
    void clampAllocationsCapsSumAt100() {
        assertArrayEquals(new int[]{40, 40, 20, 0}, MisakaComputeSink.clampAllocations(new int[]{40, 40, 30, 10}));
        assertArrayEquals(new int[]{0, 0, 0, 0}, MisakaComputeSink.clampAllocations(null));
        assertEquals(100, MisakaComputeSink.sum(MisakaComputeSink.clampAllocations(new int[]{50, 50, 50, 50})));
    }

    @Test
    void usageZeroSendsFullGroupToPoolEvenIfCpNotFull() {
        var result = MisakaComputeSettle.settle(
                List.of(new MisakaComputeSettle.ClosestBucket("n1", "Alice", 20f)),
                Map.of(),
                List.of(new MisakaComputeSettle.NetworkPoolInput("n1", 0f, new int[]{100, 0, 0, 0}, "Alice")),
                Map.of("Alice", true),
                2.0f
        );
        assertEquals(0f, result.personalCpByName().getOrDefault("Alice", 0f), 0.001f);
        assertEquals(20f, result.poolMskByNetwork().get("n1"), 0.001f);
        assertEquals(40f, result.networkCpByName().get("Alice"), 0.001f);
    }

    @Test
    void cpPerMskTwoConvertsOccupationAndRefund() {
        assertEquals(5f, MisakaComputeSettle.cpToUsageMsk(10f, 2f), 0.001f);
        var result = MisakaComputeSettle.settle(
                List.of(new MisakaComputeSettle.ClosestBucket("n1", "Alice", 20f)),
                Map.of("Alice", 5f),
                List.of(new MisakaComputeSettle.NetworkPoolInput("n1", 0f, new int[]{0, 0, 0, 0}, "Alice")),
                Map.of("Alice", true),
                2.0f
        );
        // min(0.75*20, 5) = 5 MSk -> 10 CP
        assertEquals(10f, result.personalCpByName().get("Alice"), 0.001f);
        assertEquals(15f, result.poolMskByNetwork().get("n1"), 0.001f);
    }

    @Test
    void usageBelowSeventyFivePercentCap() {
        var result = MisakaComputeSettle.settle(
                List.of(new MisakaComputeSettle.ClosestBucket("n1", "Alice", 40f)),
                Map.of("Alice", 10f),
                List.of(new MisakaComputeSettle.NetworkPoolInput("n1", 0f, new int[]{0, 0, 0, 0}, null)),
                Map.of("Alice", true),
                2.0f
        );
        assertEquals(20f, result.personalCpByName().get("Alice"), 0.001f);
        assertEquals(30f, result.poolMskByNetwork().get("n1"), 0.001f);
    }

    @Test
    void aggregatedSeventyFiveMatchesPerSisterCap() {
        var result = MisakaComputeSettle.settle(
                List.of(new MisakaComputeSettle.ClosestBucket("n1", "Alice", 20f + 20f)),
                Map.of("Alice", 100f),
                List.of(new MisakaComputeSettle.NetworkPoolInput("n1", 0f, new int[]{0, 0, 0, 0}, null)),
                Map.of("Alice", true),
                2.0f
        );
        // 0.75 * 40 = 30 MSk
        assertEquals(60f, result.personalCpByName().get("Alice"), 0.001f);
        assertEquals(10f, result.poolMskByNetwork().get("n1"), 0.001f);
    }

    @Test
    void stubSinksProduceNoCp() {
        var result = MisakaComputeSettle.settle(
                List.of(new MisakaComputeSettle.ClosestBucket("n1", "", 10f)),
                Map.of(),
                List.of(new MisakaComputeSettle.NetworkPoolInput("n1", 0f, new int[]{0, 50, 25, 25}, "Bob")),
                Map.of("Bob", true),
                2.0f
        );
        assertEquals(0f, result.networkCpByName().getOrDefault("Bob", 0f), 0.001f);
        assertEquals(10f, result.poolMskByNetwork().get("n1"), 0.001f);
    }

    @Test
    void dualPathCpClampedToMax() {
        var settled = MisakaComputeSettle.settle(
                List.of(new MisakaComputeSettle.ClosestBucket("n1", "Alice", 20f)),
                Map.of("Alice", 5f),
                List.of(new MisakaComputeSettle.NetworkPoolInput("n1", 0f, new int[]{100, 0, 0, 0}, "Alice")),
                Map.of("Alice", true),
                2.0f
        );
        var after = MisakaComputeSettle.applyCpDeltas(
                Map.of("Alice", new MisakaComputeSettle.PlayerCpState(90f, 100f)),
                settled
        );
        assertEquals(100f, after.get("Alice"), 0.001f);
    }

    @Test
    void offlineClosestSendsAllToPool() {
        var result = MisakaComputeSettle.settle(
                List.of(new MisakaComputeSettle.ClosestBucket("n1", "Alice", 20f)),
                Map.of("Alice", 100f),
                List.of(new MisakaComputeSettle.NetworkPoolInput("n1", 0f, new int[]{0, 0, 0, 0}, null)),
                Map.of("Alice", false),
                2.0f
        );
        assertEquals(0f, result.personalCpByName().getOrDefault("Alice", 0f), 0.001f);
        assertEquals(20f, result.poolMskByNetwork().get("n1"), 0.001f);
    }
}
