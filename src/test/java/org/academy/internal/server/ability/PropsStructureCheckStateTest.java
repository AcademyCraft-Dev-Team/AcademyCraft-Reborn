package org.academy.internal.server.ability;

import net.minecraft.resources.Identifier;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class PropsStructureCheckStateTest {
    private static final Identifier OVERWORLD = Identifier.withDefaultNamespace("overworld");
    private static final Identifier NETHER = Identifier.withDefaultNamespace("the_nether");

    @Test
    void idlePlayersDoNotRepeatTheSameScanEverySecond() {
        var state = new PropsStructureCheckState();
        var scans = 0;
        for (var tick = 0; tick < 1_200; tick++) {
            if (state.shouldCheck(tick, OVERWORLD, 42, 7)) scans++;
        }
        assertEquals(6, scans);
    }

    @Test
    void movingPlayersStayBoundedAndAreSpreadAcrossTicks() {
        var scansPerTick = new int[200];
        for (var player = 0; player < 100; player++) {
            var state = new PropsStructureCheckState();
            var scans = 0;
            for (var tick = 0; tick < scansPerTick.length; tick++) {
                if (state.shouldCheck(tick, OVERWORLD, tick, player)) {
                    scansPerTick[tick]++;
                    scans++;
                }
            }
            assertEquals(10, scans);
        }
        for (var count : scansPerTick) assertEquals(5, count);
    }

    @Test
    void movementDimensionChangeAndClockRollbackInvalidateIdleDelay() {
        var state = new PropsStructureCheckState();
        assertTrue(state.shouldCheck(100, OVERWORLD, 1, 0));
        assertFalse(state.shouldCheck(120, OVERWORLD, 1, 0));
        assertTrue(state.shouldCheck(140, OVERWORLD, 2, 0));
        assertTrue(state.shouldCheck(160, NETHER, 2, 0));
        assertTrue(state.shouldCheck(0, NETHER, 2, 0));
    }

    @Test
    void negativeUuidHashesStillReceiveAScanSlot() {
        var state = new PropsStructureCheckState();
        assertTrue(state.shouldCheck(Math.floorMod(Integer.MIN_VALUE, 20), OVERWORLD, 0, Integer.MIN_VALUE));
    }
}
