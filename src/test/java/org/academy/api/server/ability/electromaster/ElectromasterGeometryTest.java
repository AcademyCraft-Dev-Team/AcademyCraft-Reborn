package org.academy.api.server.ability.electromaster;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
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

    /**
     * Height control reads only ground references. A wall or ceiling contact is a valid field anchor but
     * must never be mistaken for the floor, which is what made altitude depend on an arbitrary neighbour.
     */
    @Test void onlyUpwardFacingContactsCountAsGround() {
        var pos = new BlockPos(1, 2, 3);
        var ground = new SupportReference(pos, new Vec3(1.5, 3.0, 3.5), Direction.UP, 0.5);
        var ceiling = new SupportReference(pos, new Vec3(1.5, 5.0, 3.5), Direction.DOWN, 0.5);
        var wall = new SupportReference(pos, new Vec3(2.0, 4.0, 3.5), Direction.WEST, 0.5);
        assertTrue(ground.isGround());
        assertFalse(ceiling.isGround());
        assertFalse(wall.isGround());
        assertEquals(3.0, ground.surfaceY(), 1.0e-9);
    }
}
