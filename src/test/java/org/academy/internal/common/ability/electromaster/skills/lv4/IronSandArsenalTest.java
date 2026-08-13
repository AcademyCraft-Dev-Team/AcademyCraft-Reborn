package org.academy.internal.common.ability.electromaster.skills.lv4;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class IronSandArsenalTest {
    @Test
    void allFormsUseAbilityScaling() {
        assertEquals(15.0f, IronSandArsenal.Server.calculateDamage(15.0f, 1.0f, 1.0f), 0.0001f);
        assertEquals(33.75f, IronSandArsenal.Server.calculateDamage(15.0f, 1.5f, 1.5f), 0.0001f);
        assertEquals(0.0f, IronSandArsenal.Server.calculateDamage(-1.0f, 1.0f, 1.0f), 0.0001f);
    }
}
