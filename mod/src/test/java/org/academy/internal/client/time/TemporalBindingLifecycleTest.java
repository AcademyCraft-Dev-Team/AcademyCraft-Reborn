package org.academy.internal.client.time;

import org.academy.api.client.input.InputSystem;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TemporalBindingLifecycleTest {
    private static final String KEY = "temporal_lifecycle_fixture";

    @AfterEach
    void cleanup() {
        TemporalClientRuntime.reset();
        InputSystem.removeKeyBinding(KEY);
    }

    @Test
    void newServerSessionDoesNotInheritOldExternalBindingLocks() {
        TemporalClientRuntime.applyState(UUID.randomUUID(), 1, 1, Map.of(), Map.of());
        InputSystem.addKeyBinding(KEY, InputSystem.combo(InputSystem.InputType.KEYBOARD, 321, 1, -1), c -> {
        });
        InputSystem.setKeyBindingEnabled(KEY, false);
        assertFalse(InputSystem.isKeyBindingEnabled(KEY));
        TemporalClientRuntime.applyState(UUID.randomUUID(), 1, 1, Map.of(), Map.of());
        assertTrue(InputSystem.isKeyBindingEnabled(KEY));
    }

    @Test
    void reRegisteringMaintainedBindingDoesNotResurrectAnOldLock() {
        InputSystem.addKeyBinding(KEY, InputSystem.combo(InputSystem.InputType.KEYBOARD, 321, 1, -1), c -> {
        });
        InputSystem.setKeyBindingEnabled(KEY, false);
        InputSystem.addMaintainedKeyBinding(KEY,
                InputSystem.combo(InputSystem.InputType.KEYBOARD, 321, 1, -1), c -> {
                }, c -> {
                });
        InputSystem.refreshTemporalBindingRestrictions();
        assertTrue(InputSystem.isKeyBindingEnabled(KEY));
    }
}
