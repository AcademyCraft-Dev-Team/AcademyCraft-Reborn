package org.academy.internal.common.ability.accelerator.skills.lv4;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class StormWingTest {
    @Test
    void reservationMatchesReferenceContract() {
        assertEquals(20.0f, StormWing.RESERVED_CP, 0.0001f);
    }
}
