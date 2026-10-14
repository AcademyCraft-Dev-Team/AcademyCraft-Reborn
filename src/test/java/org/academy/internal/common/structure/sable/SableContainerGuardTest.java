package org.academy.internal.common.structure.sable;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SableContainerGuardTest {
    @Test
    void mutationOutsideTickRunsInline() {
        var runs = new ArrayList<String>();
        assertTrue(SableContainerGuard.containerMutation("outside", () -> runs.add("outside")));
        assertEquals(List.of("outside"), runs);
        assertFalse(SableContainerGuard.inContainerTick());
    }

    @Test
    void mutationInsideTickDefersUntilDrain() {
        var runs = new ArrayList<String>();
        SableContainerGuard.enterContainerTick();
        try {
            assertTrue(SableContainerGuard.inContainerTick());
            assertFalse(SableContainerGuard.containerMutation("first", () -> runs.add("first")));
            assertFalse(SableContainerGuard.containerMutation("second", () -> runs.add("second")));
            assertEquals(List.of(), runs);
        } finally {
            SableContainerGuard.exitContainerTick();
        }
        assertFalse(SableContainerGuard.inContainerTick());
        assertEquals(2, SableContainerGuard.drainDeferred());
        assertEquals(List.of("first", "second"), runs);
    }

    @Test
    void allocationInsideTickFailsClosedAndOutsideRuns() {
        var supplierRuns = new ArrayList<String>();
        SableContainerGuard.enterContainerTick();
        try {
            var refused = SableContainerGuard.containerAllocation("allocate", () -> {
                supplierRuns.add("allocate");
                return "body";
            });
            assertTrue(refused.isEmpty());
            assertEquals(List.of(), supplierRuns);
        } finally {
            SableContainerGuard.exitContainerTick();
        }
        var accepted = SableContainerGuard.containerAllocation("allocate", () -> {
            supplierRuns.add("allocate");
            return "body";
        });
        assertEquals("body", accepted.orElseThrow());
        assertEquals(List.of("allocate"), supplierRuns);
    }

    @Test
    void oneFailingDeferredMutationDoesNotBlockTheRest() {
        var runs = new ArrayList<String>();
        SableContainerGuard.enterContainerTick();
        try {
            SableContainerGuard.containerMutation("fails", () -> {
                throw new IllegalStateException("expected fixture failure");
            });
            SableContainerGuard.containerMutation("still-runs", () -> runs.add("still-runs"));
        } finally {
            SableContainerGuard.exitContainerTick();
        }
        assertEquals(1, SableContainerGuard.drainDeferred());
        assertEquals(List.of("still-runs"), runs);
    }

    @Test
    void drainInsideTickAndUnbalancedExitAreRejected() {
        SableContainerGuard.enterContainerTick();
        try {
            assertThrows(IllegalStateException.class, SableContainerGuard::drainDeferred);
        } finally {
            SableContainerGuard.exitContainerTick();
        }
        assertThrows(IllegalStateException.class, SableContainerGuard::exitContainerTick);
    }
}
