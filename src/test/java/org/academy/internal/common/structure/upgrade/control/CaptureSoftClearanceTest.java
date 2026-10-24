package org.academy.internal.common.structure.upgrade.control;

import net.minecraft.world.phys.AABB;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class CaptureSoftClearanceTest {
    @Test
    void verticalSweepPreservesHollowSpaceAndAdjacentWall() {
        var arms = List.of(new AABB(-9, 120, 4, -8, 126, 5), new AABB(-7, 120, 4, -6, 126, 5));
        assertTrue(CaptureSoftClearance.intersects(arms, new AABB(-7, 124, 4, -6, 125, 5)));
        assertFalse(CaptureSoftClearance.intersects(arms, new AABB(-8, 124, 4, -7, 125, 5)));
        assertFalse(CaptureSoftClearance.intersects(arms, new AABB(-6, 124, 4, -5, 125, 5)));
    }

    @Test
    void softClearanceIncludesTheTangentLeafThatWedgedTheSteppedCapture() {
        // Live vegetation trace: local [0,1,4] -> [2,2,5], origin (-160,123.06,53).
        // The leaf at (-160,125,58) touches z=58 but has no strict volume overlap.
        var real = new AABB(-160, 124.06, 57, -158, 125.06, 58);
        var boxes = List.of(real);
        var tangentLeaf = new AABB(-160, 125, 58, -159, 126, 59);
        assertFalse(CaptureSoftClearance.intersects(boxes, tangentLeaf));
        assertTrue(CaptureSoftClearance.intersects(CaptureSoftClearance.softContactBoxes(boxes), tangentLeaf));
        assertEquals(58, boxes.getFirst().maxZ, "Physical geometry must remain unchanged");
        assertFalse(CaptureSoftClearance.intersects(CaptureSoftClearance.softContactBoxes(boxes),
                tangentLeaf.move(0, 0, 0.02)), "Soft clearance must not reach separated foliage");
    }

    @Test
    void softContactBandPreservesHollowInterior() {
        var arms = List.of(new AABB(0, 0, 0, 1, 2, 4), new AABB(4, 0, 0, 5, 2, 4));
        assertFalse(CaptureSoftClearance.intersects(CaptureSoftClearance.softContactBoxes(arms),
                new AABB(2, 0, 1, 3, 1, 2)));
    }
}
