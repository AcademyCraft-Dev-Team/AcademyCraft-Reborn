package org.academy.api.common.ability;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SkillToggleTransactionTest {
    @Test
    void enablingRequiresCpToRemainAfterTheMaintenanceLease() {
        assertTrue(Skill.hasSufficientCpToEnable(10.0f, 4.0f, 1.0f));
        assertFalse(Skill.hasSufficientCpToEnable(4.0f, 4.0f, 1.0f));
        assertFalse(Skill.hasSufficientCpToEnable(4.00001f, 4.0f, 1.0f));
    }

    @Test
    void invalidCpInputsCannotCommitAnEnableTransaction() {
        assertFalse(Skill.hasSufficientCpToEnable(Float.NaN, 1.0f, 1.0f));
        assertFalse(Skill.hasSufficientCpToEnable(10.0f, Float.POSITIVE_INFINITY, 1.0f));
        assertFalse(Skill.hasSufficientCpToEnable(10.0f, 1.0f, -1.0f));
    }
}
