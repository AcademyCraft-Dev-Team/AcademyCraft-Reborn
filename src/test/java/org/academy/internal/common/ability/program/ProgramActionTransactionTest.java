package org.academy.internal.common.ability.program;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ProgramActionTransactionTest {
    @Test
    void validatesEveryActionBeforeApplyingAnyAction() {
        var events = new ArrayList<String>();
        var transaction = new ProgramActionTransaction();
        transaction.stage(1, action(events, "one", false, false));
        transaction.stage(2, action(events, "two", true, false));

        var result = transaction.commit();

        assertFalse(result.successful());
        assertEquals(ProgramActionTransaction.Phase.VALIDATE, result.phase());
        assertEquals(2, result.nodeId());
        assertEquals(List.of("validate-one", "validate-two"), events);
    }

    @Test
    void compensatesAppliedActionsInReverseOrderWhenApplyFails() {
        var events = new ArrayList<String>();
        var transaction = new ProgramActionTransaction();
        transaction.stage(1, action(events, "one", false, false));
        transaction.stage(2, action(events, "two", false, false));
        transaction.stage(3, action(events, "three", false, true));

        var result = transaction.commit();

        assertFalse(result.successful());
        assertEquals(3, result.nodeId());
        assertEquals(List.of(
                "validate-one", "validate-two", "validate-three",
                "apply-one", "apply-two", "apply-three",
                "undo-two", "undo-one"
        ), events);
    }

    @Test
    void committedEffectsCanBeRolledBackOrReleased() {
        var rolledBackEvents = new ArrayList<String>();
        var rolledBack = new ProgramActionTransaction();
        rolledBack.stage(1, action(rolledBackEvents, "one", false, false));
        assertTrue(rolledBack.commit().successful());
        assertTrue(rolledBack.rollback().successful());
        assertEquals(List.of("validate-one", "apply-one", "undo-one"), rolledBackEvents);

        var releasedEvents = new ArrayList<String>();
        var released = new ProgramActionTransaction();
        released.stage(1, action(releasedEvents, "one", false, false));
        assertTrue(released.commit().successful());
        released.release();
        assertEquals(ProgramActionTransaction.State.RELEASED, released.state());
        assertThrows(IllegalStateException.class, released::rollback);
        assertEquals(List.of("validate-one", "apply-one"), releasedEvents);
    }

    private static ProgramActionTransaction.ProgramAction action(
            List<String> events,
            String name,
            boolean failValidation,
            boolean failApply
    ) {
        return new ProgramActionTransaction.ProgramAction() {
            @Override
            public void validate() {
                events.add("validate-" + name);
                if (failValidation) throw new IllegalStateException("validation failed");
            }

            @Override
            public ProgramActionTransaction.Undo apply() {
                events.add("apply-" + name);
                if (failApply) throw new IllegalStateException("apply failed");
                return () -> events.add("undo-" + name);
            }
        };
    }
}
