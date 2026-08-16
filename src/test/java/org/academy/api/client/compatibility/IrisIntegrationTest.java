package org.academy.api.client.compatibility;

import net.irisshaders.iris.vertices.ImmediateState;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class IrisIntegrationTest {
    private Field hasIrisField;
    private boolean originalHasIris;
    private boolean originalBypass;

    @BeforeEach
    void setUp() throws ReflectiveOperationException {
        hasIrisField = IrisIntegration.class.getDeclaredField("hasIris");
        hasIrisField.setAccessible(true);
        originalHasIris = hasIrisField.getBoolean(null);
        originalBypass = ImmediateState.bypass;
        hasIrisField.setBoolean(null, true);
        ImmediateState.bypass = false;
    }

    @AfterEach
    void tearDown() throws ReflectiveOperationException {
        ImmediateState.bypass = originalBypass;
        hasIrisField.setBoolean(null, originalHasIris);
    }

    @Test
    void restoresBypassAfterNormalCall() {
        IrisIntegration.runWithBypass(() -> assertTrue(ImmediateState.bypass));
        assertFalse(ImmediateState.bypass);
    }

    @Test
    void restoresBypassAfterException() {
        assertThrows(IllegalStateException.class, () -> IrisIntegration.runWithBypass(() -> {
            assertTrue(ImmediateState.bypass);
            throw new IllegalStateException("test");
        }));
        assertFalse(ImmediateState.bypass);
    }

    @Test
    void nestedCallsRestoreTheEnteringState() {
        IrisIntegration.runWithBypass(() -> {
            assertTrue(ImmediateState.bypass);
            IrisIntegration.runWithBypass(() -> assertTrue(ImmediateState.bypass));
            assertTrue(ImmediateState.bypass);
        });
        assertFalse(ImmediateState.bypass);
    }

    @Test
    void preservesAnAlreadyEnabledBypass() {
        ImmediateState.bypass = true;
        IrisIntegration.runWithBypass(() -> assertTrue(ImmediateState.bypass));
        assertTrue(ImmediateState.bypass);
    }
}
