package org.academy.internal.common.ability.electromaster.skills.lv1;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ElectricalContactTest {
    @Test
    void damageUsesPlayerScaling() {
        assertEquals(2.0f, ElectricalContact.Events.calculateDamage(1.0f), 0.0001f);
        assertEquals(3.0f, ElectricalContact.Events.calculateDamage(1.5f), 0.0001f);
        assertEquals(0.0f, ElectricalContact.Events.calculateDamage(-1.0f), 0.0001f);
    }
}
