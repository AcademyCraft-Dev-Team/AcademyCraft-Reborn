package org.academy.internal.common.ability.darkmatter.skills.lv5;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class DarkmatterSixWingsTest {
    @Test
    void reservationMatchesReferenceContract() {
        assertEquals(50.0f, DarkmatterSixWings.RESERVED_CP, 0.0001f);
    }
}
