package org.academy.internal.server.misaka;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

class MisakaComputeSettleTest {
    private static final float CP_PER_MSK = 2.0f;

    @Test
    void clampAllocationsCapsSumAt100() {
        assertArrayEquals(new int[]{40, 40, 20, 0}, MisakaComputeSink.clampAllocations(new int[]{40, 40, 30, 10}));
        assertArrayEquals(new int[]{0, 0, 0, 0}, MisakaComputeSink.clampAllocations(null));
        assertEquals(100, MisakaComputeSink.sum(MisakaComputeSink.clampAllocations(new int[]{50, 50, 50, 50})));
    }

    /** Design §9.4: group 100 MSk, A demand 100, B demand 100 → equal split of 75 priority. */
    @Test
    void maxMinEqualDemandsSplitPriorityEvenly() {
        var result = MisakaComputeSettle.settle(
                List.of(new MisakaComputeSettle.GroupBucket(List.of("A", "B"), 100f)),
                Map.of("A", 100f, "B", 100f),
                Map.of("A", true, "B", true),
                null,
                new int[]{0, 0, 0, 0},
                null,
                CP_PER_MSK
        );
        assertEquals(37.5f, result.personalAllocatedMskByName().get("A"), 0.001f);
        assertEquals(37.5f, result.personalAllocatedMskByName().get("B"), 0.001f);
        // leftover shared 25 split equally → +12.5 each
        assertEquals(12.5f, result.sharedAllocatedMskByName().get("A"), 0.001f);
        assertEquals(12.5f, result.sharedAllocatedMskByName().get("B"), 0.001f);
        assertEquals(0.5f, result.satisfactionByName().get("A"), 0.001f);
        assertEquals(0.5f, result.satisfactionByName().get("B"), 0.001f);
        assertEquals(0f, result.poolLeftoverMsk(), 0.001f);
    }

    /**
     * Design §7.1: without a reconstruction work each bucket settles alone, so sister B's
     * 25% can never reach A, who is only benevolent-listed on sister A.
     */
    @Test
    void unintegratedNetworkKeepsEachBucketSeparate() {
        var groups = List.of(
                new MisakaComputeSettle.GroupBucket(List.of("A"), 100f),
                new MisakaComputeSettle.GroupBucket(List.of("B"), 100f)
        );
        var demand = Map.of("A", 1000f, "B", 0f);
        var online = Map.of("A", true, "B", true);

        var pooled = MisakaComputeSettle.settle(
                groups, demand, online, null, new int[]{0, 0, 0, 0}, null, CP_PER_MSK,
                java.util.Set.of("A", "B")
        );
        // Integrated: A also drains B's unused 75 + both 25% shares → all 200 MSk.
        assertEquals(200f, total(pooled, "A"), 0.001f);

        var dispersed = MisakaComputeSettle.settleUnintegrated(
                groups, demand, online, null, CP_PER_MSK, java.util.Set.of("A", "B")
        );
        // Dispersed: A only gets sister A's own 100; sister B's output goes idle.
        assertEquals(100f, total(dispersed, "A"), 0.001f);
        assertEquals(100f, dispersed.poolLeftoverMsk(), 0.001f);
        assertEquals(0.1f, dispersed.satisfactionByName().get("A"), 0.001f);
        // No reconstruction work → no leftover CP sink recipient.
        assertEquals(Map.of(), dispersed.networkCpByName());
    }

    private static float total(MisakaComputeSettle.Result result, String name) {
        return result.personalAllocatedMskByName().getOrDefault(name, 0f)
                + result.sharedAllocatedMskByName().getOrDefault(name, 0f);
    }

    /** Design §9.4: A=10 B=100 → A gets 10, B gets 65 from priority 75. */
    @Test
    void maxMinSmallDemandSaturatedFirst() {
        var result = MisakaComputeSettle.settle(
                List.of(new MisakaComputeSettle.GroupBucket(List.of("A", "B"), 100f)),
                Map.of("A", 10f, "B", 100f),
                Map.of("A", true, "B", true),
                null,
                new int[]{0, 0, 0, 0},
                null,
                CP_PER_MSK
        );
        assertEquals(10f, result.personalAllocatedMskByName().get("A"), 0.001f);
        assertEquals(65f, result.personalAllocatedMskByName().get("B"), 0.001f);
        // A fully satisfied; shared 25 all to B
        assertEquals(0f, result.sharedAllocatedMskByName().getOrDefault("A", 0f), 0.001f);
        assertEquals(25f, result.sharedAllocatedMskByName().get("B"), 0.001f);
        assertEquals(1.0f, result.satisfactionByName().get("A"), 0.001f);
        assertEquals(0.9f, result.satisfactionByName().get("B"), 0.001f);
    }

    /** Design §9.4: A=10 B=30 C=100 → A=10, B=30, C=35 from priority 75. */
    @Test
    void maxMinThreeWayProgressiveFill() {
        var result = MisakaComputeSettle.settle(
                List.of(new MisakaComputeSettle.GroupBucket(List.of("A", "B", "C"), 100f)),
                Map.of("A", 10f, "B", 30f, "C", 100f),
                Map.of("A", true, "B", true, "C", true),
                null,
                new int[]{0, 0, 0, 0},
                null,
                CP_PER_MSK
        );
        assertEquals(10f, result.personalAllocatedMskByName().get("A"), 0.001f);
        assertEquals(30f, result.personalAllocatedMskByName().get("B"), 0.001f);
        assertEquals(35f, result.personalAllocatedMskByName().get("C"), 0.001f);
        // A,B satisfied; shared 25 → C
        assertEquals(25f, result.sharedAllocatedMskByName().get("C"), 0.001f);
        assertEquals(0.6f, result.satisfactionByName().get("C"), 0.001f);
    }

    @Test
    void usageZeroSendsFullGroupToPoolEvenIfCpNotFull() {
        var result = MisakaComputeSettle.settle(
                List.of(new MisakaComputeSettle.GroupBucket(List.of("Alice"), 20f)),
                Map.of(),
                Map.of("Alice", true),
                null,
                new int[]{100, 0, 0, 0},
                "Alice",
                CP_PER_MSK
        );
        assertEquals(0f, result.personalCpByName().getOrDefault("Alice", 0f), 0.001f);
        assertEquals(0f, result.sharedCpByName().getOrDefault("Alice", 0f), 0.001f);
        assertEquals(20f, result.poolLeftoverMsk(), 0.001f);
        assertEquals(40f, result.networkCpByName().get("Alice"), 0.001f);
    }

    @Test
    void cpPerMskTwoConvertsOccupationAndRefund() {
        assertEquals(5f, MisakaComputeSettle.cpToUsageMsk(10f, 2f), 0.001f);
        var result = MisakaComputeSettle.settle(
                List.of(new MisakaComputeSettle.GroupBucket(List.of("Alice"), 20f)),
                Map.of("Alice", 5f),
                Map.of("Alice", true),
                null,
                new int[]{0, 0, 0, 0},
                "Alice",
                CP_PER_MSK
        );
        // priority 15, take 5 → personal 5 MSk → 10 CP; leftover shared 15
        assertEquals(10f, result.personalCpByName().get("Alice"), 0.001f);
        assertEquals(15f, result.poolLeftoverMsk(), 0.001f);
    }

    @Test
    void usageBelowSeventyFivePercentCap() {
        var result = MisakaComputeSettle.settle(
                List.of(new MisakaComputeSettle.GroupBucket(List.of("Alice"), 40f)),
                Map.of("Alice", 10f),
                Map.of("Alice", true),
                null,
                new int[]{0, 0, 0, 0},
                null,
                CP_PER_MSK
        );
        assertEquals(20f, result.personalCpByName().get("Alice"), 0.001f);
        assertEquals(30f, result.poolLeftoverMsk(), 0.001f);
    }

    @Test
    void aggregatedSeventyFiveMatchesPerSisterCap() {
        var result = MisakaComputeSettle.settle(
                List.of(new MisakaComputeSettle.GroupBucket(List.of("Alice"), 40f)),
                Map.of("Alice", 100f),
                Map.of("Alice", true),
                null,
                new int[]{0, 0, 0, 0},
                null,
                CP_PER_MSK
        );
        // priority 30 + shared 10 = 40 allocated (capped by group)
        assertEquals(60f, result.personalCpByName().get("Alice"), 0.001f);
        assertEquals(20f, result.sharedCpByName().get("Alice"), 0.001f);
        assertEquals(0f, result.poolLeftoverMsk(), 0.001f);
    }

    @Test
    void stubSinksProduceNoCp() {
        var result = MisakaComputeSettle.settle(
                List.of(new MisakaComputeSettle.GroupBucket(List.of(), 10f)),
                Map.of(),
                Map.of("Bob", true),
                null,
                new int[]{0, 50, 25, 25},
                "Bob",
                CP_PER_MSK
        );
        assertEquals(0f, result.networkCpByName().getOrDefault("Bob", 0f), 0.001f);
        assertEquals(10f, result.poolLeftoverMsk(), 0.001f);
    }

    @Test
    void dualPathCpClampedToMax() {
        var settled = MisakaComputeSettle.settle(
                List.of(new MisakaComputeSettle.GroupBucket(List.of("Alice"), 20f)),
                Map.of("Alice", 5f),
                Map.of("Alice", true),
                null,
                new int[]{100, 0, 0, 0},
                "Alice",
                CP_PER_MSK
        );
        var after = MisakaComputeSettle.applyCpDeltas(
                Map.of("Alice", new MisakaComputeSettle.PlayerCpState(90f, 100f)),
                settled
        );
        assertEquals(100f, after.get("Alice"), 0.001f);
    }

    @Test
    void offlineGroupMemberSendsPriorityToShared() {
        var result = MisakaComputeSettle.settle(
                List.of(new MisakaComputeSettle.GroupBucket(List.of("Alice"), 20f)),
                Map.of("Alice", 100f),
                Map.of("Alice", false),
                null,
                new int[]{0, 0, 0, 0},
                null,
                CP_PER_MSK
        );
        assertEquals(0f, result.personalCpByName().getOrDefault("Alice", 0f), 0.001f);
        assertEquals(0f, result.sharedCpByName().getOrDefault("Alice", 0f), 0.001f);
        assertEquals(20f, result.poolLeftoverMsk(), 0.001f);
    }

    @Test
    void outOfRangePrivilegeMatchesOfflinePersonalPath() {
        var result = MisakaComputeSettle.settle(
                List.of(new MisakaComputeSettle.GroupBucket(List.of("Alice"), 40f)),
                Map.of("Alice", 100f),
                Map.of("Alice", false),
                null,
                new int[]{100, 0, 0, 0},
                "Alice",
                CP_PER_MSK
        );
        assertEquals(0f, result.personalCpByName().getOrDefault("Alice", 0f), 0.001f);
        assertEquals(40f, result.poolLeftoverMsk(), 0.001f);
        assertEquals(0f, result.networkCpByName().getOrDefault("Alice", 0f), 0.001f);
    }

    @Test
    void reconInRangeReceivesPoolCpWhileGroupOorSendsAllToLeftover() {
        var result = MisakaComputeSettle.settle(
                List.of(new MisakaComputeSettle.GroupBucket(List.of("Alice"), 20f)),
                Map.of("Alice", 100f),
                Map.of("Alice", false, "Bob", true),
                null,
                new int[]{100, 0, 0, 0},
                "Bob",
                CP_PER_MSK
        );
        assertEquals(0f, result.personalCpByName().getOrDefault("Alice", 0f), 0.001f);
        assertEquals(20f, result.poolLeftoverMsk(), 0.001f);
        assertEquals(40f, result.networkCpByName().get("Bob"), 0.001f);
    }

    @Test
    void weightedSharedPoolPrefersHigherWeight() {
        // Empty benevolent set: all MSk → shared; A weight 1, B weight 3, equal leftover demand
        var result = MisakaComputeSettle.settle(
                List.of(new MisakaComputeSettle.GroupBucket(List.of(), 40f)),
                Map.of("A", 100f, "B", 100f),
                Map.of("A", true, "B", true),
                Map.of("A", 1f, "B", 3f),
                new int[]{0, 0, 0, 0},
                null,
                CP_PER_MSK
        );
        assertEquals(10f, result.sharedAllocatedMskByName().get("A"), 0.001f);
        assertEquals(30f, result.sharedAllocatedMskByName().get("B"), 0.001f);
    }

    @Test
    void sharedPoolSkipsPlayersWithoutAccess() {
        var result = MisakaComputeSettle.settle(
                List.of(new MisakaComputeSettle.GroupBucket(List.of(), 40f)),
                Map.of("A", 100f, "B", 100f),
                Map.of("A", true, "B", true),
                null,
                new int[]{0, 0, 0, 0},
                null,
                CP_PER_MSK,
                java.util.Set.of("A")
        );
        assertEquals(40f, result.sharedAllocatedMskByName().get("A"), 0.001f);
        assertEquals(0f, result.sharedAllocatedMskByName().getOrDefault("B", 0f), 0.001f);
    }
}
