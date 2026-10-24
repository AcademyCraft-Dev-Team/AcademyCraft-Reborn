package org.academy.internal.common.structure.upgrade.control;

import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.academy.api.common.structure.StructurePose;
import org.joml.Quaterniond;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class StructureRestSupportTest {
    @Test
    void tiltedRodCanRestOnAStepWellAboveItsLowestCorner() {
        var body = TranslationSweep.Box.of(new AABB(0, 0, 0, 4, 1, 1),
                new StructurePose(Vec3.ZERO, new Quaterniond().rotationZ(Math.PI / 6)));
        var step = new AABB(2, 0, 0, 3, 2 * Math.tan(Math.PI / 6), 1);
        assertTrue(step.maxY > body.bounds().minY + 1);
        assertTrue(StructureRestSupport.supports(body, step));
    }

    @Test
    void floorSlopIsAllowedButHoverWallsAndDeepPenetrationAreNot() {
        var unit = TranslationSweep.Box.axisAligned(new AABB(0, 0, 0, 1, 1, 1));
        assertTrue(StructureRestSupport.supports(unit, new AABB(-1, -1, -1, 2, .01, 2)));
        assertFalse(StructureRestSupport.supports(unit, new AABB(-1, -1, -1, 2, -.3, 2)));
        assertFalse(StructureRestSupport.supports(unit, new AABB(1, -1, -1, 2, 3, 2)));
        assertFalse(StructureRestSupport.supports(unit, new AABB(-1, -1, -1, 2, .2, 2)));
    }
}
