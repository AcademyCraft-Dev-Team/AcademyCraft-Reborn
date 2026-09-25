package org.academy.api.client.input;

import com.mojang.blaze3d.platform.InputConstants;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;

class InputSystemMaintainedBindingTest {
    private static final String BINDING = "input_system_test_maintained";

    @AfterEach
    void tearDown() {
        InputSystem.removeKeyBinding(BINDING);
    }

    @Test
    void oneLogicalBindingStartsAndStopsExactlyOnce() {
        var starts = new AtomicInteger();
        var stops = new AtomicInteger();
        InputSystem.addMaintainedKeyBinding(
                BINDING,
                InputSystem.combo(InputSystem.InputType.KEYBOARD, InputConstants.KEY_G,
                        InputSystem.ANY_ACTION, InputConstants.MOD_ALT),
                _ -> starts.incrementAndGet(),
                _ -> stops.incrementAndGet()
        );

        InputSystem.dispatchMaintainedForTesting(BINDING, context(InputConstants.PRESS));
        InputSystem.dispatchMaintainedForTesting(BINDING, context(InputConstants.PRESS));
        InputSystem.dispatchMaintainedForTesting(BINDING, context(InputConstants.RELEASE));
        InputSystem.dispatchMaintainedForTesting(BINDING, context(InputConstants.RELEASE));

        assertEquals(1, starts.get());
        assertEquals(1, stops.get());
        assertEquals(InputSystem.ANY_ACTION, InputSystem.getKeyBinding(BINDING).action());
    }

    @Test
    void disablingAnActiveBindingForcesItsStopCallback() {
        var stops = new AtomicInteger();
        InputSystem.addMaintainedKeyBinding(
                BINDING,
                InputSystem.combo(InputSystem.InputType.KEYBOARD, InputConstants.KEY_G,
                        InputSystem.ANY_ACTION, 0),
                _ -> {
                },
                _ -> stops.incrementAndGet()
        );

        InputSystem.dispatchMaintainedForTesting(BINDING, context(InputConstants.PRESS));
        InputSystem.setKeyBindingEnabled(BINDING, false);
        InputSystem.setKeyBindingEnabled(BINDING, false);

        assertEquals(1, stops.get());
    }

    private static InputSystem.BindingContext context(int action) {
        return new InputSystem.BindingContext(
                InputSystem.InputType.KEYBOARD,
                InputConstants.KEY_G,
                action,
                InputConstants.MOD_ALT
        );
    }
}
