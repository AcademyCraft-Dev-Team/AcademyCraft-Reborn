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
    void restClearanceIsTheHoverEquilibrium() {
        var tuning = LevitationTuning.DEFAULT;
        assertEquals(0.0, MagneticMovement.hoverVertical(0, tuning.restClearance(), tuning), 1.0e-9);
    }

    @Test
    void risingGroundIsCorrectedUpwardsInsteadOfLettingTheMoverSink() {
        var tuning = LevitationTuning.DEFAULT;
        // Terrain has risen into the mover: the only safe correction is upward.
        var climb = MagneticMovement.hoverVertical(0, MagneticMovement.MIN_CLEARANCE, tuning);
        assertTrue(climb > 0, "ground closing in must produce lift, not sink");
        assertTrue(climb <= tuning.maxClimbSpeed() + 1.0e-9);
    }

    @Test
    void clearanceErrorRespectsAsymmetricSpeedLimits() {
        var tuning = LevitationTuning.DEFAULT;
        // Far above the ground the solver descends, but never faster than the descent cap.
        var descend = MagneticMovement.hoverVertical(0, 40.0, tuning);
        assertTrue(descend < 0);
        assertEquals(-tuning.maxDescentSpeed(), descend, 1.0e-9);
        // Deep inside the ground it climbs, but never faster than the climb cap.
        assertEquals(tuning.maxClimbSpeed(), MagneticMovement.hoverVertical(0, 0.0, tuning), 1.0e-9);
    }

    @Test
    void verticalInputMovesTheTargetClearanceAroundTheRestHeight() {
        var tuning = LevitationTuning.DEFAULT;
        assertTrue(MagneticMovement.hoverVertical(1, tuning.restClearance(), tuning) > 0);
        assertTrue(MagneticMovement.hoverVertical(-1, tuning.restClearance(), tuning) < 0);
        // Opposite inputs cancel, matching the shared input convention.
        assertEquals(0.0, MagneticMovement.hoverVertical(0, tuning.restClearance(), tuning), 1.0e-9);
    }

    /**
     * Climbing moves away from the ground, so an unbounded target would ask for clearance the field cannot
     * provide and leave the mover fighting a boundary clamp every tick instead of holding a ceiling.
     */
    @Test
    void climbTargetIsCappedByFieldReachAndThenHoldsSteady() {
        var tuning = LevitationTuning.DEFAULT;
        var reach = 4.0;
        assertTrue(MagneticMovement.hoverVertical(1, tuning.restClearance(), reach, tuning) > 0,
                "still climbing towards the capped target");
        assertEquals(0.0, MagneticMovement.hoverVertical(1, reach, reach, tuning), 1.0e-9,
                "at the capped ceiling further climb input must not keep pushing upward");
        // Past the cap the solver must descend back into the field, not keep climbing out of it.
        assertTrue(MagneticMovement.hoverVertical(1, reach + 1.0, reach, tuning) < 0);
    }

    @Test
    void invalidExtendedInputCannotCreateNonFiniteMotion() {
        var tuning = LevitationTuning.DEFAULT;
        assertEquals(0.0, MagneticMovement.hoverVertical(Double.NaN, 1.5, 4.0, tuning), 1.0e-9);
        assertEquals(0.0, MagneticMovement.hoverVertical(0, Double.NaN, 4.0, tuning), 1.0e-9);
        // A non-finite cap falls back to no cap rather than producing NaN.
        assertEquals(MagneticMovement.hoverVertical(1, 1.5, tuning),
                MagneticMovement.hoverVertical(1, 1.5, Double.NaN, tuning), 1.0e-9);
    }

    /**
     * Regression for the reported drop: crossing an uneven surface must never sink the mover into the
     * ground. Stairs and slabs are the traversable case, so one tick can always clear the rise.
     */
    @Test
    void unevenTerrainNeverPutsTheMoverInsideTheGround() {
        var tuning = LevitationTuning.DEFAULT;
        // Stair-like terrain: up two half steps, a plateau, a half step down, then a shallow dip.
        double[] ground = {0, 0, 0.5, 1.0, 1.0, 1.0, 0.5, 0.5, 0.25, 0};
        var feetY = ground[0] + tuning.restClearance();
        for (var step = 0; step < ground.length; step++) {
            var clearance = feetY - ground[step];
            feetY += MagneticMovement.hoverVertical(0, clearance, tuning);
            var settled = feetY - ground[step];
            assertTrue(settled > 0.0,
                    "mover ended up inside the ground at step " + step + ": clearance=" + settled);
        }
    }

    /**
     * A rise the mover cannot clear in one tick must still be corrected monotonically and from the right
     * direction: never downward, and back above the floor within a bounded number of ticks.
     */
    @Test
    void aRiseTooTallForOneTickRecoversUpwardAndNeverSinks() {
        var tuning = LevitationTuning.DEFAULT;
        var groundY = 2.0;
        var feetY = tuning.restClearance();
        var inside = feetY - groundY;
        assertTrue(inside < 0, "the ledge must start buried, otherwise this is not the case under test");
        var recoveredTicks = -1;
        for (var tick = 0; tick < 20; tick++) {
            var clearance = feetY - groundY;
            var vertical = MagneticMovement.hoverVertical(0, clearance, tuning);
            assertTrue(vertical >= 0.0, "a rising ground must never push the mover down");
            feetY += vertical;
            if (feetY - groundY > 0) { recoveredTicks = tick; break; }
        }
        assertTrue(recoveredTicks >= 0, "the mover never climbed back out of the ground");
        assertTrue(recoveredTicks <= 8, "recovery took too long: " + recoveredTicks + " ticks");
    }

    /**
     * A drop must be taken at a bounded speed instead of turning into a free fall, which is what made the
     * mover feel like it was plummeting off the edge of raised terrain.
     */
    @Test
    void aDropIsDescendedAtABoundedSpeed() {
        var tuning = LevitationTuning.DEFAULT;
        var feetY = 40.0;
        for (var tick = 0; tick < 10; tick++) {
            var vertical = MagneticMovement.hoverVertical(0, feetY, tuning);
            assertTrue(vertical < 0, "far above the ground the mover should descend");
            assertTrue(vertical >= -tuning.maxDescentSpeed() - 1.0e-9,
                    "descent exceeded the cap at tick " + tick + ": " + vertical);
            feetY += vertical;
        }
    }

    @Test
    void degradedVerticalSinksWithoutInputAndKeepsClimbAuthority() {
        var tuning = LevitationTuning.DEFAULT;
        var sink = MagneticMovement.degradedVertical(0, tuning);
        assertTrue(sink < 0, "a mover without a field must not hold altitude");
        assertEquals(-tuning.sinkSpeed(), sink, 1.0e-9);
        // Holding jump must still be able to climb back into the field.
        assertEquals(tuning.maxClimbSpeed(), MagneticMovement.degradedVertical(1, tuning), 1.0e-9);
        // Downward input is capped by the ordinary descent limit.
        assertEquals(-tuning.maxDescentSpeed(), MagneticMovement.degradedVertical(-1, tuning), 1.0e-9);
        assertEquals(-tuning.sinkSpeed(), MagneticMovement.degradedVertical(Double.NaN, tuning), 1.0e-9);
    }

    @Test
    void horizontalSmoothingNeverTouchesVerticalMotion() {
        var smoothed = MagneticMovement.approachHorizontal(Vec3.ZERO, new Vec3(1.0, 5.0, -1.0));
        assertEquals(0.0, smoothed.y, 1.0e-12);
        assertTrue(Math.abs(smoothed.x) <= MagneticMovement.ACCELERATION + 1.0e-9);
        assertTrue(smoothed.z < 0);
    }

    @Test
    void invalidInputCannotCreateNonFiniteMotion() {
        assertEquals(Vec3.ZERO, MagneticMovement.flightDirection(Float.NaN, 1, 0, 1));
        assertEquals(Vec3.ZERO, MagneticMovement.approachVelocity(Vec3.ZERO,
                new Vec3(Double.NaN, 0, 0), Vec3.ZERO, 0.9));
        assertEquals(0.0, MagneticMovement.hoverVertical(Double.NaN, 1.5, LevitationTuning.DEFAULT), 1.0e-9);
        assertEquals(0.0, MagneticMovement.hoverVertical(0, Double.NaN, LevitationTuning.DEFAULT), 1.0e-9);
        assertEquals(0.0, MagneticMovement.hoverVertical(0, 1.5, null), 1.0e-9);
        assertEquals(Vec3.ZERO, MagneticMovement.approachHorizontal(Vec3.ZERO, new Vec3(Double.NaN, 0, 0)));
    }
}
