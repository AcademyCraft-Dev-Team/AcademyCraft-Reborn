package org.academy.internal.common.sublevel;

import net.minecraft.world.phys.AABB;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;

import static org.junit.jupiter.api.Assertions.*;

class SubLevelCollisionBudgetTest {
    @Test void smallSetsAreUnchanged() {
        var boxes = new ArrayList<AABB>();
        for (int index = 0; index < 10; index++) {
            boxes.add(new AABB(index, 0, 0, index + 1, 1, 1));
        }
        assertEquals(boxes, SubLevelCollisionBudget.apply(boxes, 10));
    }

    @Test void largeSetsAreBoundedAndCoverCenters() {
        var boxes = new ArrayList<AABB>();
        for (int x = 0; x < 40; x++) {
            for (int z = 0; z < 40; z++) {
                boxes.add(new AABB(x, 0, z, x + 1, 1, z + 1));
            }
        }
        var bounded = SubLevelCollisionBudget.apply(boxes, 64);
        assertTrue(bounded.size() <= 64);
        for (var box : boxes) {
            var center = box.getCenter();
            assertTrue(bounded.stream().anyMatch(candidate -> candidate.contains(center)),
                    "Coalesced boxes must cover original centers");
        }
    }
}
