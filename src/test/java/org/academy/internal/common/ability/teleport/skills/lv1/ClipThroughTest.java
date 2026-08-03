package org.academy.internal.common.ability.teleport.skills.lv1;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ClipThroughTest {
    @Test
    void rangeScalesWithSkillLevel() {
        assertEquals(2.0f, ClipThrough.getMaxDistance(0), 0.0001f);
        assertEquals(5.0f, ClipThrough.getMaxDistance(3), 0.0001f);
    }
}
