package org.academy.internal.common.ability.teleport.program;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ServerTeleportProgramRuntimeTest {
    @Test
    void standardTargetTeleportSupportsSixtyFourBlocks() {
        assertEquals(8.0, ServerTeleportProgramRuntime.entityTargetRange(0.0f));
        assertEquals(64.0, ServerTeleportProgramRuntime.entityTargetRange(1.0f));
        assertEquals(128.0, ServerTeleportProgramRuntime.entityTargetRange(2.0f));
        assertEquals(64.0, ServerTeleportProgramRuntime.entityMoveRange(1.0f));
    }

    @Test
    void targetTeleportCostUsesActualDistanceTiers() {
        assertEquals(7.5f, ServerTeleportProgramRuntime.entityCost(1.0f, 16.0));
        assertEquals(15.0f, ServerTeleportProgramRuntime.entityCost(1.0f, 32.0));
        assertEquals(30.0f, ServerTeleportProgramRuntime.entityCost(1.0f, 32.01));
        assertEquals(30.0f, ServerTeleportProgramRuntime.entityCost(1.0f, 64.0));

        assertEquals(33.75f, ServerTeleportProgramRuntime.entityCost(2.0f, 16.0));
        assertEquals(67.5f, ServerTeleportProgramRuntime.entityCost(2.0f, 32.0));
        assertEquals(135.0f, ServerTeleportProgramRuntime.entityCost(2.0f, 64.0));
    }
}
