package org.academy.internal.common.ability.teleport.skills.lv5;

import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

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
}
