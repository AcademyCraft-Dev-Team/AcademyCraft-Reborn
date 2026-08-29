package org.academy.internal.common.ability.teleport.skills.lv5;

import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DefensiveTeleportTest {
    @Test
    void projectileMustHaveMotionTowardPlayer() {
        var toPlayer = new Vec3(4, 0, 0);
        assertTrue(DefensiveTeleport.Events.isHeadingToward(new Vec3(1, 0, 0), toPlayer));
        assertFalse(DefensiveTeleport.Events.isHeadingToward(new Vec3(-1, 0, 0), toPlayer));
        assertFalse(DefensiveTeleport.Events.isHeadingToward(Vec3.ZERO, toPlayer));
    }

    @Test
    void worldSelectionBoxStaysFixedAcrossFirstAndThirdPersonCameras() {
        var worldBox = new AABB(8.0, 68.0, 14.0, 12.0, 72.0, 18.0);
        var firstPersonCamera = new Vec3(10.0, 70.0, 10.0);
        var thirdPersonCamera = new Vec3(10.0, 71.0, 6.0);

        assertRestoresWorldBox(worldBox,
                DefensiveTeleport.cameraRelativeBox(worldBox, firstPersonCamera),
                firstPersonCamera);
        assertRestoresWorldBox(worldBox,
                DefensiveTeleport.cameraRelativeBox(worldBox, thirdPersonCamera),
                thirdPersonCamera);
    }

    @Test
    void thirdPersonSelectionUsesHorizontalPlayerFacing() {
        var direction = DefensiveTeleport.selectionDirection(
                new Vec3(0.0, -1.0, 0.0),
                0.0f,
                false
        );

        assertEquals(0.0, direction.x, 1.0e-9);
        assertEquals(0.0, direction.y, 1.0e-9);
        assertEquals(1.0, direction.z, 1.0e-9);
        assertEquals(1.0, direction.length(), 1.0e-9);
    }

    @Test
    void firstPersonSelectionKeepsVerticalAim() {
        var viewDirection = new Vec3(0.0, -1.0, 0.0);

        assertEquals(viewDirection, DefensiveTeleport.selectionDirection(
                viewDirection,
                90.0f,
                true
        ));
    }

    private static void assertRestoresWorldBox(AABB expected, AABB relative, Vec3 camera) {
        var restored = relative.move(camera);
        assertEquals(expected.minX, restored.minX, 1.0e-9);
        assertEquals(expected.minY, restored.minY, 1.0e-9);
        assertEquals(expected.minZ, restored.minZ, 1.0e-9);
        assertEquals(expected.maxX, restored.maxX, 1.0e-9);
        assertEquals(expected.maxY, restored.maxY, 1.0e-9);
        assertEquals(expected.maxZ, restored.maxZ, 1.0e-9);
    }
}
