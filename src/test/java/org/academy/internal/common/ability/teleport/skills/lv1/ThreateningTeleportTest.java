package org.academy.internal.common.ability.teleport.skills.lv1;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ThreateningTeleportTest {
    @Test
    void teleportedItemDropsOnlyOnMissOrKill() {
        assertTrue(ThreateningTeleport.shouldDropTeleportedItem(false, false));
        assertTrue(ThreateningTeleport.shouldDropTeleportedItem(true, true));
        assertFalse(ThreateningTeleport.shouldDropTeleportedItem(true, false));
    }
}
