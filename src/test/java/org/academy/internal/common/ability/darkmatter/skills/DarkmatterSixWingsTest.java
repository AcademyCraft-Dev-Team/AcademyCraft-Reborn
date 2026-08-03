package org.academy.internal.common.ability.darkmatter.skills;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class DarkmatterSixWingsTest {
    @Test
    void reservationMatchesReferenceContract() {
        assertEquals(70.0f, DarkmatterSixWings.RESERVED_CP, 0.0001f);
    }
}
