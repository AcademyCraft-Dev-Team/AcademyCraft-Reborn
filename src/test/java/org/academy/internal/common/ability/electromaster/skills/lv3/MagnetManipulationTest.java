package org.academy.internal.common.ability.electromaster.skills.lv3;

import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class MagnetManipulationTest {
    @Test
    void movementUsesTheReferenceSpeedTowardTheServerTarget() {
        var velocity = MagnetManipulation.calculateMoveVelocity(
                Vec3.ZERO,
                new Vec3(0, 0, 10),
                new Vec3(1, 0, 0)
        );

        assertEquals(MagnetManipulation.MOVE_SPEED_PER_TICK, velocity.length(), 1.0e-9);
        assertEquals(0, velocity.x, 1.0e-9);
        assertEquals(MagnetManipulation.MOVE_SPEED_PER_TICK, velocity.z, 1.0e-9);
    }

    @Test
    void movementFallsBackToLookDirectionAtTheTarget() {
        var velocity = MagnetManipulation.calculateMoveVelocity(
                new Vec3(2, 3, 4),
                new Vec3(2, 3, 4),
                new Vec3(0, 1, 0)
        );

        assertEquals(MagnetManipulation.MOVE_SPEED_PER_TICK, velocity.y, 1.0e-9);
    }

    @Test
    void movementSlowsInsideTheArrivalDistance() {
        var velocity = MagnetManipulation.calculatePullVelocity(
                new Vec3(0.4, 0, 0),
                Vec3.ZERO,
                new Vec3(0.5, 0, 0),
                new Vec3(1, 0, 0),
                1.0,
                0.75
        );

        assertEquals(0.1, velocity.x, 1.0e-9);
    }

    @Test
    void controlledBlocksReuseTheMagnetHomingProfile() {
        var current = new Vec3(0.2, 0.1, 0.0);
        var origin = new Vec3(1.0, 2.0, 3.0);
        var target = new Vec3(4.0, 2.5, 3.0);
        var fallback = new Vec3(1.0, 0.0, 0.0);

        assertEquals(
                MagnetManipulation.calculatePullVelocity(
                        current, origin, target, fallback, 0.9, 0.45),
                MagnetManipulation.calculateControlledBlockVelocity(
                        current, origin, target, fallback, 0.9, 0.45)
        );
    }

    @Test
    void wheelScrollAdjustsAndClampsTheControlledHoldDistance() {
        assertTrue(MagnetManipulation.HOLD_DISTANCE_STEP > MagnetManipulation.TARGET_STOP_DISTANCE,
                "A notch must clear the pull stop tolerance");
        assertEquals(MagnetManipulation.TARGET_FRONT_DISTANCE + MagnetManipulation.HOLD_DISTANCE_STEP,
                MagnetManipulation.resolveHoldDistance(MagnetManipulation.TARGET_FRONT_DISTANCE, 1), 1.0e-9);
        assertEquals(MagnetManipulation.TARGET_FRONT_DISTANCE - MagnetManipulation.HOLD_DISTANCE_STEP,
                MagnetManipulation.resolveHoldDistance(MagnetManipulation.TARGET_FRONT_DISTANCE, -1), 1.0e-9);
        assertEquals(MagnetManipulation.HOLD_DISTANCE_MAX,
                MagnetManipulation.resolveHoldDistance(MagnetManipulation.HOLD_DISTANCE_MAX, 1), 1.0e-9);
        assertEquals(MagnetManipulation.HOLD_DISTANCE_MIN,
                MagnetManipulation.resolveHoldDistance(MagnetManipulation.HOLD_DISTANCE_MIN, -1), 1.0e-9);
        assertEquals(MagnetManipulation.TARGET_FRONT_DISTANCE,
                MagnetManipulation.resolveHoldDistance(Double.NaN, 1), 1.0e-9);
        assertEquals(MagnetManipulation.TARGET_FRONT_DISTANCE,
                MagnetManipulation.resolveHoldDistance(MagnetManipulation.TARGET_FRONT_DISTANCE, Double.NaN), 1.0e-9);
    }

    @Test
    void ironPathDetectionDoesNotTreatEveryMetalAsIron() {
        assertTrue(MagnetManipulation.isIronRelatedPath("iron_ore"));
        assertTrue(MagnetManipulation.isIronRelatedPath("raw_iron_block"));
        assertTrue(MagnetManipulation.isIronRelatedPath("iron_horse_armor"));
        assertTrue(MagnetManipulation.hasMagneticKeyword("forge:storage_blocks/steel"));
        assertFalse(MagnetManipulation.isIronRelatedPath("copper_block"));
        assertFalse(MagnetManipulation.isIronRelatedPath("netherite_block"));
    }

    @Test
    void toolRequirementTagsDoNotMakeBlocksMagnetic() {
        assertFalse(MagnetManipulation.isMagneticTagPath("incorrect_for_iron_tool"));
        assertFalse(MagnetManipulation.isMagneticTagPath("needs_iron_tool"));
        assertTrue(MagnetManipulation.isMagneticTagPath("storage_blocks/iron"));
        assertTrue(MagnetManipulation.isMagneticTagPath("ores/magnetite"));
    }
}
