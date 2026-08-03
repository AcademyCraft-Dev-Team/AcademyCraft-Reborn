package org.academy.internal.common.ability.teleport.skills.lv5;

import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class FlashingTest {
    @Test
    void serverDerivesOppositeAndHorizontalDirectionsFromLook() {
        var look = new Vec3(0, 0, 1);
        assertVec(look, Flashing.Server.directionFromLook(look, 0, Flashing.Direction.FORWARD));
        assertVec(look.scale(-1), Flashing.Server.directionFromLook(look, 0, Flashing.Direction.BACK));
        assertVec(new Vec3(1, 0, 0),
                Flashing.Server.directionFromLook(look, 0, Flashing.Direction.LEFT));
        assertVec(new Vec3(-1, 0, 0),
                Flashing.Server.directionFromLook(look, 0, Flashing.Direction.RIGHT));
    }

    private static void assertVec(Vec3 expected, Vec3 actual) {
        assertEquals(expected.x, actual.x, 1.0e-6);
        assertEquals(expected.y, actual.y, 1.0e-6);
        assertEquals(expected.z, actual.z, 1.0e-6);
    }
}
