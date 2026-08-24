package org.academy.api.client.input;

import com.mojang.blaze3d.platform.InputConstants;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;

class InputSystemPairedBindingTest {
    private static final String START = "paired_input_test_start";
    private static final String STOP = "paired_input_test_stop";
    private static final String UNRELATED = "paired_input_test_toggle";

    @AfterEach
    void tearDown() {
        InputSystem.removeKeyBinding(START);
        InputSystem.removeKeyBinding(STOP);
        InputSystem.removeKeyBinding(UNRELATED);
    }

    @Test
    void screenCancellationStopsLegacyPairOnceAndSuppressesLaterPhysicalRelease() {
        var starts = new AtomicInteger();
        var stops = new AtomicInteger();
        register(starts, stops);

        InputSystem.dispatchPairedForTesting(START, context(InputConstants.PRESS));
        InputSystem.cancelMaintainedKeyBindings();
        InputSystem.dispatchPairedForTesting(STOP, context(InputConstants.RELEASE));

        assertEquals(1, starts.get());
        assertEquals(1, stops.get());
    }

    @Test
    void physicalReleaseStopsArmedLegacyPairExactlyOnce() {
        var starts = new AtomicInteger();
        var stops = new AtomicInteger();
        register(starts, stops);

        InputSystem.dispatchPairedForTesting(START, context(InputConstants.PRESS));
        InputSystem.dispatchPairedForTesting(STOP, context(InputConstants.RELEASE));

        assertEquals(1, starts.get());
        assertEquals(1, stops.get());
    }

    @Test
    void unrelatedReleaseBindingIsNotCancelledWithActivePair() {
        var stops = new AtomicInteger();
        InputSystem.addKeyBinding(START, combo(InputConstants.PRESS), _ -> {});
        InputSystem.addKeyBinding(UNRELATED, combo(InputConstants.RELEASE),
                _ -> stops.incrementAndGet());

        InputSystem.dispatchPairedForTesting(START, context(InputConstants.PRESS));
        InputSystem.cancelMaintainedKeyBindings();

        assertEquals(0, stops.get());
    }

    private static void register(AtomicInteger starts, AtomicInteger stops) {
        InputSystem.addKeyBinding(START, combo(InputConstants.PRESS), _ -> starts.incrementAndGet());
        InputSystem.addKeyBinding(STOP, combo(InputConstants.RELEASE), _ -> stops.incrementAndGet());
    }

    private static InputSystem.KeyCombination combo(int action) {
        return InputSystem.combo(
                InputSystem.InputType.KEYBOARD,
                InputConstants.KEY_G,
                action,
                InputConstants.MOD_ALT
        );
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
