package org.academy.internal.common.ability.teleport.program;

import net.minecraft.world.phys.AABB;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ServerTeleportProgramRuntimeTest {
    @Test
    void targetDirectionsProduceMinecraftYawAndPitch() {
        var east = new org.academy.api.common.ability.program.ProgramDirection(1, 0, 0);
        var up = new org.academy.api.common.ability.program.ProgramDirection(0, 1, 0);
        assertEquals(-90.0f, ServerTeleportProgramRuntime.rotationYaw(east));
        assertEquals(0.0f, ServerTeleportProgramRuntime.rotationPitch(east), 0.0001f);
        assertEquals(-90.0f, ServerTeleportProgramRuntime.rotationPitch(up));
    }

    @Test
    void targetTeleportNeverExceedsTheCategorySixtyFourBlockLimit() {
        assertEquals(8.0, ServerTeleportProgramRuntime.entityTargetRange(0.0f));
        assertEquals(64.0, ServerTeleportProgramRuntime.entityTargetRange(1.0f));
        assertEquals(64.0, ServerTeleportProgramRuntime.entityTargetRange(2.0f));
        assertEquals(64.0, ServerTeleportProgramRuntime.entityMoveRange(1.0f));
        assertEquals(64.0, ServerTeleportProgramRuntime.entityMoveRange(2.0f));
    }

    @Test
    void targetTeleportCostUsesActualDistanceTiers() {
        assertEquals(7.5f, ServerTeleportProgramRuntime.entityCost(1.0f, 16.0));
        assertEquals(15.0f, ServerTeleportProgramRuntime.entityCost(1.0f, 32.0));
        assertEquals(30.0f, ServerTeleportProgramRuntime.entityCost(1.0f, 32.01));
        assertEquals(30.0f, ServerTeleportProgramRuntime.entityCost(1.0f, 64.0));

        assertEquals(30.0f, ServerTeleportProgramRuntime.entityCost(2.0f, 16.0));
        assertEquals(60.0f, ServerTeleportProgramRuntime.entityCost(2.0f, 32.0));
        assertEquals(120.0f, ServerTeleportProgramRuntime.entityCost(2.0f, 64.0));
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

    @Test
    void blockItemTeleportBaseDamageUsesMaximumCpAndTargetHealthFloor() {
        assertEquals(80.0f, ServerTeleportProgramRuntime.blockItemBaseDamage(
                100.0f, 80.0f, 800.0f));
        assertEquals(40.0f, ServerTeleportProgramRuntime.blockItemBaseDamage(
                100.0f, 30.0f, 800.0f));
        assertEquals(40.0f, ServerTeleportProgramRuntime.blockItemBaseDamage(
                20.0f, 80.0f, 800.0f));
        assertEquals(15.0f, ServerTeleportProgramRuntime.blockItemBaseDamage(
                15.0f, 100.0f, 20.0f));
        assertEquals(50.0f, ServerTeleportProgramRuntime.blockItemBaseDamage(
                15.0f, 10.0f, 1_000.0f));
    }

    @Test
    void blockItemTeleportRawBlockDamageIsHardnessTimesTen() {
        assertEquals(15.0f, ServerTeleportProgramRuntime.blockItemHardnessDamage(1.5f));
        assertEquals(500.0f, ServerTeleportProgramRuntime.blockItemHardnessDamage(50.0f));
    }

    @Test
    void blockItemTeleportCostUsesRawDamageAndMaximumCp() {
        assertEquals(11.5f, ServerTeleportProgramRuntime.blockItemTeleportCost(
                ServerTeleportProgramRuntime.blockItemHardnessDamage(1.5f), 1_000.0f));
        assertEquals(20.0f, ServerTeleportProgramRuntime.blockItemTeleportCost(
                ServerTeleportProgramRuntime.blockItemHardnessDamage(50.0f), 100.0f));
        assertEquals(10.0f, ServerTeleportProgramRuntime.blockItemTeleportCost(0.0f, 100.0f));
    }

    @Test
    void blockItemTeleportRejectsInvalidDamageInputs() {
        assertEquals(0.0f, ServerTeleportProgramRuntime.blockItemBaseDamage(
                Float.POSITIVE_INFINITY, 100.0f, 100.0f));
        assertEquals(0.0f, ServerTeleportProgramRuntime.blockItemBaseDamage(
                10.0f, Float.NaN, 100.0f));
        assertEquals(0.0f, ServerTeleportProgramRuntime.blockItemBaseDamage(
                10.0f, 100.0f, Float.NaN));
    }
}
