package org.academy.api.server.ability;

import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class HostileProjectileImpactTest {
    @Test void fastProjectileImpactIsOnEntryBoundaryNotItsPreviousRemotePosition() {
        assertEquals(new Vec3(-2, 0, 0), HostileProjectiles.sphereEntry(
                new Vec3(-20, 0, 0), new Vec3(20, 0, 0), Vec3.ZERO, 2));
        assertEquals(new Vec3(0, 2, 0), HostileProjectiles.sphereEntry(
                new Vec3(0, 10, 0), new Vec3(0, -10, 0), Vec3.ZERO, 2));
        assertNull(HostileProjectiles.sphereEntry(new Vec3(-20, 3, 0), new Vec3(20, 3, 0), Vec3.ZERO, 2));
        assertNull(HostileProjectiles.sphereEntry(new Vec3(3, 0, 0), new Vec3(5, 0, 0), Vec3.ZERO, 2));
        assertEquals(Vec3.ZERO, HostileProjectiles.sphereEntry(Vec3.ZERO, Vec3.ZERO, Vec3.ZERO, 2));
    }
}
