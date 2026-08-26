package org.academy.internal.common.ability.teleport.program;

import net.minecraft.world.phys.AABB;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

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

    @Test
    void blockItemTeleportDamageIncludesEntitiesTouchingTheTargetCell() {
        var cell = new AABB(4.0, 8.0, 12.0, 5.0, 9.0, 13.0);

        assertTrue(ServerTeleportProgramRuntime.touchesBlockCell(
                new AABB(4.2, 9.0, 12.2, 4.8, 10.8, 12.8), cell));
        assertTrue(ServerTeleportProgramRuntime.touchesBlockCell(
                new AABB(4.2, 8.2, 12.2, 4.8, 8.8, 12.8), cell));
        assertFalse(ServerTeleportProgramRuntime.touchesBlockCell(
                new AABB(4.2, 9.0001, 12.2, 4.8, 10.8, 12.8), cell));
    }
}
