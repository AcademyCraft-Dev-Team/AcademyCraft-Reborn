package org.academy.internal.common.ability.mentalout;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MentalControlRecallTest {
    @AfterEach
    void clearRecallState() {
        MentalControlRecall.clear();
    }

    @Test
    void explicitlyReleasedSubjectStaysSuppressedUntilAllowed() {
        var controllerId = UUID.randomUUID();
        var subjectId = UUID.randomUUID();

        MentalControlRecall.suppressUntilExit(controllerId, subjectId);

        assertTrue(MentalControlRecall.isSuppressed(controllerId, subjectId));
        MentalControlRecall.allow(controllerId, subjectId);
        assertFalse(MentalControlRecall.isSuppressed(controllerId, subjectId));
    }

    @Test
    void controllerCleanupDropsItsRecallSuppressions() {
        var controllerId = UUID.randomUUID();
        var subjectId = UUID.randomUUID();
        MentalControlRecall.suppressUntilExit(controllerId, subjectId);

        MentalControlRecall.releaseController(controllerId);

        assertFalse(MentalControlRecall.isSuppressed(controllerId, subjectId));
    }
}
