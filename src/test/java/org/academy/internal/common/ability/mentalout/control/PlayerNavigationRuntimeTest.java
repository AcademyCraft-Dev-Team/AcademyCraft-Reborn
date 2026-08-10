package org.academy.internal.common.ability.mentalout.control;

import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PlayerNavigationRuntimeTest {
    @Test
    void globalPlannerBudgetIsBoundedAndResetsOnNextTick() {
        PlayerNavigationRuntime.beginServerTick(100L);
        assertEquals(256, PlayerNavigationRuntime.claimExpansionBudget(100L, 256));
        assertEquals(768, PlayerNavigationRuntime.claimExpansionBudget(100L, 900));
        assertEquals(0, PlayerNavigationRuntime.claimExpansionBudget(100L, 1));

        assertEquals(256, PlayerNavigationRuntime.claimExpansionBudget(101L, 256));
    }

    @Test
    void firstGroundJumpPulseDoesNotOverflowSentinelTimestamp() {
        assertTrue(DefaultPlayerNavigationAdapter.shouldPulseJump(true, 100L, Long.MIN_VALUE));
        assertFalse(DefaultPlayerNavigationAdapter.shouldPulseJump(true, 101L, 100L));
        assertTrue(DefaultPlayerNavigationAdapter.shouldPulseJump(true, 108L, 100L));
        assertFalse(DefaultPlayerNavigationAdapter.shouldPulseJump(false, 108L, 100L));
    }

    @Test
    void arrivalRadiusDoesNotAcceptPositionOneBlockBelowGoal() {
        var goal = new Vec3(3.5, 65.0, 3.5);

        assertFalse(DefaultPlayerNavigationAdapter.hasReached(
                new Vec3(3.5, 64.0, 3.5), goal, 1.0));
        assertTrue(DefaultPlayerNavigationAdapter.hasReached(
                new Vec3(3.5, 64.6, 3.5), goal, 1.0));
    }

    @Test
    void plannerOnlyAcceptsArrivalNodesAtGoalFootLevel() {
        var goal = new BlockPos(3, 65, 3);

        assertFalse(DefaultPlayerNavigationAdapter.searchNodeReaches(
                new BlockPos(3, 64, 3), goal, 1.0));
        assertTrue(DefaultPlayerNavigationAdapter.searchNodeReaches(
                new BlockPos(2, 65, 3), goal, 1.0));
    }

    @Test
    void navigationRetainsAutoEnabledFlightAtUnsupportedAirDestination() {
        assertTrue(DefaultPlayerNavigationAdapter.shouldRetainAutoFlight(
                true, false, false, false, false, false
        ));
        assertFalse(DefaultPlayerNavigationAdapter.shouldRetainAutoFlight(
                true, true, false, false, false, false
        ));
        assertFalse(DefaultPlayerNavigationAdapter.shouldRetainAutoFlight(
                true, false, false, false, false, true
        ));
        assertFalse(DefaultPlayerNavigationAdapter.shouldRetainAutoFlight(
                false, false, false, false, false, false
        ));
    }
}
