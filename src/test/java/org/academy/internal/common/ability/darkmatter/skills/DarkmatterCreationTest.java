package org.academy.internal.common.ability.darkmatter.skills;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class DarkmatterCreationTest {
    @Test
    void contractLimitsAndReservationMatchAudit() {
        assertEquals(8, DarkmatterCreation.MAX_BEETLES);
        assertEquals(20.0f, DarkmatterCreation.RESERVED_CP_PER_BEETLE, 0.0001f);
    }
}
