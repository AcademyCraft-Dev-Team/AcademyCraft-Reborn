package org.academy.internal.common.structure;

import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.academy.api.common.structure.StructureImpactSweep;
import org.academy.api.common.structure.StructurePose;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class PreparedStructureImpactSweepTest {
    @Test void sparseLargeGroundCaptureDoesNotSpendTheWorstCaseForEveryTargetCell() {
        var cells = new ArrayList<AABB>();
        for (int x = 0; x < 13; x++) for (int z = 0; z < 11; z++) {
            if (x < 4 && z < 4) continue;
            cells.add(new AABB(x, 0, z, x + 1, 1, z + 1));
        }
        assertEquals(127, cells.size());
        var prepared = new PreparedStructureImpactSweep(cells, StructurePose.identity(Vec3.ZERO),
                StructurePose.identity(new Vec3(3.75, 0, 0)));
        int budget = 2_000_000;
        int hit = 0;
        for (int x = -10; x <= 24; x++) for (int y = -4; y <= 4; y++) for (int z = -8; z <= 20; z++) {
            var result = prepared.test(new AABB(x, y, z, x + 1, y + 1, z + 1), budget);
            assertTrue(result.complete());
            budget -= result.tests();
            if (result.intersects()) hit++;
        }
        assertTrue(hit >= 127);
        assertTrue(budget > 1_990_000, "Bounding rejection and successful early exits must cost actual tests only");
    }

    @Test void matchesTheSharedSampledSweepForRotationAndEmptyGaps() {
        var cells = List.of(new AABB(0, 0, 0, 4, 1, 0.15), new AABB(0, 0, 3, 4, 1, 3.15));
        var from = StructurePose.identity(Vec3.ZERO);
        var to = StructurePose.fromYawAroundPivot(new Vec3(2, 0, 0), 90, Vec3.ZERO);
        var prepared = new PreparedStructureImpactSweep(cells, from, to);
        for (int x = -4; x < 8; x++) for (int z = -4; z < 8; z++) {
            var target = new AABB(x + .2, .1, z + .2, x + .8, .9, z + .8);
            var result = prepared.test(target, 100_000);
            assertTrue(result.complete());
            assertEquals(StructureImpactSweep.intersects(cells, from, to, target), result.intersects());
        }
    }

    @Test void exhaustedNarrowPhaseCannotBeMisreportedAsAFreePath() {
        var prepared = new PreparedStructureImpactSweep(List.of(new AABB(0, 0, 0, 1, 1, 1)),
                StructurePose.identity(Vec3.ZERO), StructurePose.identity(Vec3.ZERO));
        var blocked = prepared.test(new AABB(.1, .1, .1, .9, .9, .9), 0);
        assertFalse(blocked.complete());
        assertFalse(blocked.intersects());
        assertEquals(0, blocked.tests());
        var empty = prepared.test(new AABB(10, 10, 10, 11, 11, 11), 0);
        assertTrue(empty.complete());
        assertFalse(empty.intersects());
        var hit = prepared.test(new AABB(.1, .1, .1, .9, .9, .9), 1);
        assertTrue(hit.complete());
        assertTrue(hit.intersects());
        assertEquals(1, hit.tests());
    }
}
