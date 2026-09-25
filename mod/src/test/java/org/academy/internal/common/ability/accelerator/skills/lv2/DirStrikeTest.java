package org.academy.internal.common.ability.accelerator.skills.lv2;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class DirStrikeTest {
    @Test
    void usesReferenceCircularRadius() {
        assertTrue(DirStrike.isInsideAttackRadius(12, 0));
        assertTrue(DirStrike.isInsideAttackRadius(0, -12));
        assertFalse(DirStrike.isInsideAttackRadius(9, 9));
    }

    @Test
    void scalesReferenceDamageThroughCurrentMultipliers() {
        assertEquals(12.0f, DirStrike.getDamage(1.0f, 1.0f));
        assertEquals(30.0f, DirStrike.getDamage(1.25f, 2.0f));
    }
}
