package org.academy.api.server.ability.electromaster;

import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.academy.api.server.ability.HostileProjectiles;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ElectromasterGeometryTest {
    @Test void sweptPathCatchesProjectilesThatCrossBetweenTicks() {
        assertTrue(HostileProjectiles.sweptIntersectsSphere(new Vec3(-10, 0, 0), new Vec3(10, 0, 0), Vec3.ZERO, 2));
        assertTrue(HostileProjectiles.sweptIntersectsSphere(new Vec3(-10, 2, 0), new Vec3(10, 2, 0), Vec3.ZERO, 2));
        assertFalse(HostileProjectiles.sweptIntersectsSphere(new Vec3(-10, 2.01, 0), new Vec3(10, 2.01, 0), Vec3.ZERO, 2));
        assertFalse(HostileProjectiles.sweptIntersectsSphere(new Vec3(-10, 0, 0), new Vec3(-3, 0, 0), Vec3.ZERO, 2));
    }
    @Test void whipHasAngularRangeAndHeightBoundaries() {
        var actor = new AABB(-0.3, 0, -0.3, 0.3, 1.8, 0.3);
        assertTrue(IronSandActions.inWhip(Vec3.ZERO, new Vec3(0, 0, 1), 12, actor, box(0, 1, 12)));
        assertFalse(IronSandActions.inWhip(Vec3.ZERO, new Vec3(0, 0, 1), 12, actor, box(0, 1, 12.01)));
        assertFalse(IronSandActions.inWhip(Vec3.ZERO, new Vec3(0, 0, 1), 12, actor, box(0, 1, -1)));
        assertFalse(IronSandActions.inWhip(Vec3.ZERO, new Vec3(0, 0, 1), 12, actor, box(0, 5, 1)));
    }
    @Test void supportUsesSurfaceDistanceInEveryDirection() {
        var actor = new AABB(0, 0, 0, 1, 2, 1);
        assertEquals(16, MagneticSupportQuery.distanceSquared(actor, new AABB(5, 0, 0, 6, 1, 1)));
        assertEquals(16, MagneticSupportQuery.distanceSquared(actor, new AABB(0, 6, 0, 1, 7, 1)));
        assertEquals(0, MagneticSupportQuery.distanceSquared(actor, new AABB(0, 2, 0, 1, 3, 1)));
    }
    private static AABB box(double x, double y, double z) { return new AABB(x, y, z, x, y, z); }
}
