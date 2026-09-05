package org.academy.api.common.ability.electromaster;

import net.minecraft.core.Direction;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class MagneticMovementTest {
    @Test
    void anchorsKeepTheWholeSubjectOutsideWallsAndCeilings() {
        var contact = new Vec3(10, 5, 10);
        assertEquals(new Vec3(10, 6.5, 10),
                MagneticMovement.anchorPosition(contact, Direction.UP, 0.6, 1.8, 1.5));
        var wall = MagneticMovement.anchorPosition(contact, Direction.WEST, 0.6, 1.8, 1.5);
        assertTrue(wall.x + 0.3 < contact.x);
        assertEquals(4.1, wall.y, 1.0e-9);
        var ceiling = MagneticMovement.anchorPosition(contact, Direction.DOWN, 0.6, 1.8, 1.5);
        assertTrue(ceiling.y + 1.8 < contact.y);
    }

    @Test
    void approachConvergesWithoutOrbitingTheAnchor() {
        var feet = Vec3.ZERO;
        var velocity = Vec3.ZERO;
        var anchor = new Vec3(8, 3, -4);
        for (var tick = 0; tick < 240; tick++) {
            var next = MagneticMovement.approachVelocity(velocity, feet, anchor, 0.9);
            assertTrue(next.subtract(velocity).length() <= MagneticMovement.ACCELERATION + 1.0e-9);
            velocity = next;
            feet = feet.add(velocity);
        }
        assertTrue(feet.distanceTo(anchor) < 0.01);
        assertTrue(velocity.length() < 0.01);
    }

    @Test
    void flightFollowsYawAndDiagonalMovementCannotExceedSpeed() {
        assertEquals(new Vec3(0, 0, 0.9), MagneticMovement.flightDirection(0, 1, 0, 0.9));
        var west = MagneticMovement.flightDirection(90, 1, 0, 0.9);
        assertEquals(-0.9, west.x, 1.0e-9);
        assertEquals(0.0, west.z, 1.0e-9);
        assertEquals(0.9, MagneticMovement.flightDirection(40, 1, 1, 0.9).length(), 1.0e-9);
        assertEquals(Vec3.ZERO, MagneticMovement.flightDirection(40, 0, 0, 0.9));
    }

    @Test
    void hoverTracksTerrainHeightAndBrakesWithoutInput() {
        var feetY = 2.5;
        var velocity = new Vec3(0.8, 0, 0);
        for (var tick = 0; tick < 180; tick++) {
            velocity = MagneticMovement.hoverVelocity(velocity, Vec3.ZERO, feetY, 3.0, 1.5);
            feetY += velocity.y;
        }
        assertEquals(4.5, feetY, 0.01);
        assertEquals(0.0, velocity.length(), 0.01);
    }

    @Test
    void heightControlsAreBoundedAndOppositeInputsCancel() {
        assertEquals(1.5, MagneticMovement.adjustHoverHeight(1.5, true, true));
        assertEquals(1.62, MagneticMovement.adjustHoverHeight(1.5, true, false), 1.0e-9);
        assertEquals(MagneticMovement.MAX_HOVER_HEIGHT,
                MagneticMovement.adjustHoverHeight(MagneticMovement.MAX_HOVER_HEIGHT, true, false));
        assertEquals(MagneticMovement.MIN_HOVER_HEIGHT,
                MagneticMovement.adjustHoverHeight(MagneticMovement.MIN_HOVER_HEIGHT, false, true));
    }

    @Test
    void invalidInputCannotCreateNonFiniteMotion() {
        assertEquals(Vec3.ZERO, MagneticMovement.flightDirection(Float.NaN, 1, 0, 1));
        assertEquals(Vec3.ZERO, MagneticMovement.approachVelocity(Vec3.ZERO,
                new Vec3(Double.NaN, 0, 0), Vec3.ZERO, 0.9));
        assertEquals(Vec3.ZERO, MagneticMovement.hoverVelocity(Vec3.ZERO, Vec3.ZERO,
                2, Double.NaN, 1.5));
    }
}
