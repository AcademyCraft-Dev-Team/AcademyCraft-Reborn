package org.academy.internal.common.event;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class DarkmatterEquipmentEventsTest {
    @Test
    void armorReductionIsTenPercentPerProtectedPiece() {
        assertEquals(1.0f, DarkmatterEquipmentEvents.damageMultiplier(0), 0.0001f);
        assertEquals(0.7f, DarkmatterEquipmentEvents.damageMultiplier(3), 0.0001f);
        assertEquals(0.6f, DarkmatterEquipmentEvents.damageMultiplier(8), 0.0001f);
    }
}
