package org.academy.api.common.ability.event;

import org.academy.api.common.ability.Skill;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SkillExecutionEventTest {
    @Test
    void preEventCanCancelBeforeCpIsTouched() {
        var event = new SkillExecutionPreEvent(null, null, false);

        assertFalse(event.isCanceled());
        event.setCanceled(true);
        assertTrue(event.isCanceled());
        assertFalse(event.continuous());
    }

    @Test
    void costEventExposesMutablePreIntensityCost() {
        var event = new SkillExecutionCostEvent(null, true, 12.5f);

        assertEquals(12.5f, event.cost(), 0.0001f);
        assertTrue(event.continuous());
        event.setCost(3.25f);
        assertEquals(3.25f, event.cost(), 0.0001f);
        event.setCanceled(true);
        assertTrue(event.isCanceled());
    }

    @Test
    void finishEventReportsSuccessOrFailureWithoutHiddenExecutionState() {
        var event = new SkillExecutionFinishEvent(null, false, false, new IllegalStateException("test"));

        assertNull(event.context());
        assertFalse(event.successful());
        assertEquals("test", event.failure().getMessage());
    }
}
