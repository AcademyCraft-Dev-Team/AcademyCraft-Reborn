package org.academy.api.client.render.vfxgraph.shape;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class OcclusionQueryBudgetTest {
    @Test void manyActorsCannotMultiplyTheSharedTickBudget() {
        var budget = new OcclusionQueryBudget(48);
        var world = new Object();
        int accepted = 0;
        for (int actor = 0; actor < 18; actor++) for (int wing = 0; wing < 6; wing++)
            if (budget.tryAcquire(world, 100, 3)) accepted++;
        assertEquals(16, accepted);
        assertEquals(48, budget.used());
        assertFalse(budget.tryAcquire(world, 100, 3));
        assertTrue(budget.tryAcquire(world, 101, 3));
        assertEquals(3, budget.used());
        assertEquals(51, budget.total());
    }

    @Test void worldChangeOrRewoundTimeStartsANewBudget() {
        var budget = new OcclusionQueryBudget(3);
        var world = new Object();
        assertTrue(budget.tryAcquire(world, 100, 3));
        assertFalse(budget.tryAcquire(world, 100, 3));
        assertTrue(budget.tryAcquire(new Object(), 100, 3));
        assertTrue(budget.tryAcquire(world, 50, 3));
    }

    @Test void rejectedGroupsDoNotConsumePartialBudget() {
        var budget = new OcclusionQueryBudget(4);
        var world = new Object();
        assertTrue(budget.tryAcquire(world, 1, 3));
        assertFalse(budget.tryAcquire(world, 1, 3));
        assertFalse(budget.tryAcquire(world, 1, 0));
        assertFalse(budget.tryAcquire(world, 1, 5));
        assertEquals(3, budget.used());
        assertTrue(budget.tryAcquire(world, 1, 1));
    }

    @Test void eighteenActorsShareQueriesWithoutStarvingTheLastActor() {
        verifyFairService(1, 4);
    }

    @Test void lowFrameRatesKeepDeferredGroupsAcrossSkippedWorldTicks() {
        verifyFairService(2, 1);
        verifyFairService(5, 1);
    }

    private void verifyFairService(int tickStep, int frames) {
        var budget = new OcclusionQueryBudget(48);
        var world = new Object();
        var groups = new Object[108];
        var next = new long[108];
        var serviced = new int[108];
        java.util.Arrays.setAll(groups, i -> new Object());
        for (long tick = 0; tick < 200L * tickStep; tick += tickStep) {
            // Several rendered frames per world tick must neither multiply work nor lose queue order.
            for (int frame = 0; frame < frames; frame++) for (int group = 0; group < groups.length; group++) {
                if (tick < next[group]) continue;
                if (budget.tryAcquire(world, tick, 3, groups[group])) {
                    serviced[group]++;
                    next[group] = tick + 4 + (group / 6 + group % 6) % 2;
                }
            }
            assertTrue(budget.used() <= 48);
        }
        for (int count : serviced) assertTrue(count >= 20, "Every visible group must receive repeated service");
    }

    @Test void invisibleQueuedCallerCannotBlockNewVisibleActorsIndefinitely() {
        var budget = new OcclusionQueryBudget(3);
        var world = new Object();
        var hidden = new Object();
        var visible = new Object();
        assertTrue(budget.tryAcquire(world, 0, 3, new Object()));
        assertFalse(budget.tryAcquire(world, 0, 3, hidden));
        assertFalse(budget.tryAcquire(world, 1, 3, visible));
        assertTrue(budget.tryAcquire(world, 2, 3, visible));
    }

    @Test void worldChangeAndTimeRewindReleaseOldQueuedPriorities() {
        var budget = new OcclusionQueryBudget(3);
        var world = new Object();
        assertTrue(budget.tryAcquire(world, 100, 3, new Object()));
        assertFalse(budget.tryAcquire(world, 100, 3, new Object()));
        var nextWorld = new Object();
        assertTrue(budget.tryAcquire(nextWorld, 100, 3, new Object()));
        assertFalse(budget.tryAcquire(nextWorld, 100, 3, new Object()));
        assertTrue(budget.tryAcquire(nextWorld, 50, 3, new Object()));
    }
}
