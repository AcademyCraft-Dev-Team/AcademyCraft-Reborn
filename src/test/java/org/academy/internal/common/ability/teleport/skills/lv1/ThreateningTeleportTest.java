package org.academy.internal.common.ability.teleport.skills.lv1;

import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ThreateningTeleportTest {
    @Test
    void untargetedDestinationIsExactlySixteenBlocksAlongView() {
        var eye = new Vec3(3.0, 5.0, 7.0);
        var destination = ThreateningTeleport.untargetedDestination(eye, new Vec3(3.0, 0.0, 4.0));

        assertEquals(16.0, destination.distanceTo(eye), 0.0001);
        assertEquals(12.6, destination.x, 0.0001);
        assertEquals(5.0, destination.y, 0.0001);
        assertEquals(19.8, destination.z, 0.0001);
    }

    @Test
    void invalidViewDirectionKeepsTheEyePosition() {
        var eye = new Vec3(3.0, 5.0, 7.0);
        assertEquals(eye, ThreateningTeleport.untargetedDestination(eye, Vec3.ZERO));
    }
}
