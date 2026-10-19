package org.academy.api.server.ability;

import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class WorldBlockTargetsTest {
    private static final Vec3 START = new Vec3(120, 80, -34);
    private static final Vec3 END = START.add(0, 0, 40);

    @Test void noHitKeepsRangeEndpoint() {
        assertEquals(new WorldBlockTargets.Target(END, false), WorldBlockTargets.nearest(START, END, null, null));
    }

    @Test void structureInFrontOfTerrainWins() {
        var surface = START.add(0, 0, 5);
        assertEquals(surface, WorldBlockTargets.nearest(START, END, START.add(0, 0, 20), surface).position());
    }

    @Test void terrainOccludesFartherStructure() {
        var wall = START.add(0, 0, 3);
        assertEquals(wall, WorldBlockTargets.nearest(START, END, wall, START.add(0, 0, 8)).position());
    }

    @Test void privatePlotPointCannotBecomeProjectileDestination() {
        assertFalse(WorldBlockTargets.nearest(START, END, null, new Vec3(28_000_000, 80, 0)).hit());
    }

    @Test void localPointCannotMasqueradeAsWorldPoint() {
        assertFalse(WorldBlockTargets.nearest(START, END, null, new Vec3(0.5, 0.5, 0)).hit());
    }

    @Test void offRayBehindAndBeyondPointsAreRejected() {
        for (var invalid : new Vec3[]{START.add(1, 0, 8), START.add(0, 0, -1), END.add(0, 0, 1),
                new Vec3(Double.NaN, 0, 0), new Vec3(Double.POSITIVE_INFINITY, 0, 0)}) {
            assertFalse(WorldBlockTargets.nearest(START, END, invalid, invalid).hit());
        }
    }

    @Test void insideAndExactRangeBoundaryHitsArePreserved() {
        assertEquals(new WorldBlockTargets.Target(START, true, WorldBlockTargets.Source.STRUCTURE),
                WorldBlockTargets.nearest(START, END, null, START));
        assertEquals(new WorldBlockTargets.Target(END, true, WorldBlockTargets.Source.STRUCTURE),
                WorldBlockTargets.nearest(START, END, null, END));
    }
}
