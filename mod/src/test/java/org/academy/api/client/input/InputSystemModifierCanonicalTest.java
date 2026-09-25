package org.academy.api.client.input;

import com.mojang.blaze3d.platform.InputConstants;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.lwjgl.sdl.SDLKeycode;

import java.lang.reflect.Field;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Modifier matching is side-insensitive: defaults are registered with the combined SDL masks
 * (e.g. MOD_SHIFT covers both Shift keys) while real key events carry side-specific bits
 * (left Shift = 0x1, right Shift = 0x2). Either side must satisfy a "Shift+N" style binding.
 */
class InputSystemModifierCanonicalTest {
    private static final String BINDING = "input_system_test_canonical";
    private static final String START = "input_system_test_canonical_start";
    private static final String STOP = "input_system_test_canonical_stop";

    // SDL side-specific modifier bits behind the combined InputConstants masks.
    private static final int LEFT_SHIFT = SDLKeycode.SDL_KMOD_LSHIFT;
    private static final int RIGHT_SHIFT = SDLKeycode.SDL_KMOD_RSHIFT;
    private static final int LEFT_CTRL = SDLKeycode.SDL_KMOD_LCTRL;
    private static final int RIGHT_CTRL = SDLKeycode.SDL_KMOD_RCTRL;
    private static final int LEFT_ALT = SDLKeycode.SDL_KMOD_LALT;
    private static final int RIGHT_ALT = SDLKeycode.SDL_KMOD_RALT;

    @Test
    void sideBitsRecomposeCombinedMasks() {
        assertEquals(InputConstants.MOD_SHIFT, LEFT_SHIFT | RIGHT_SHIFT);
        assertEquals(InputConstants.MOD_CONTROL, LEFT_CTRL | RIGHT_CTRL);
        assertEquals(InputConstants.MOD_ALT, LEFT_ALT | RIGHT_ALT);
        assertEquals(InputConstants.MOD_SUPER,
                SDLKeycode.SDL_KMOD_LGUI | SDLKeycode.SDL_KMOD_RGUI);
    }

    @AfterEach
    void tearDown() {
        InputSystem.removeKeyBinding(BINDING);
        InputSystem.removeKeyBinding(START);
        InputSystem.removeKeyBinding(STOP);
        clearState();
    }

    @Test
    void combinedShiftDefaultFiresWithEitherSideOnPress() {
        var fires = new AtomicInteger();
        InputSystem.addKeyBinding(
                BINDING,
                InputSystem.combo(InputSystem.InputType.KEYBOARD, InputConstants.KEY_N,
                        InputConstants.PRESS, InputConstants.MOD_SHIFT, true),
                _ -> fires.incrementAndGet()
        );
        pressKey(InputConstants.KEY_N);

        InputSystem.dispatch(InputSystem.InputType.KEYBOARD, InputConstants.KEY_N,
                InputConstants.PRESS, LEFT_SHIFT);
        InputSystem.dispatch(InputSystem.InputType.KEYBOARD, InputConstants.KEY_N,
                InputConstants.PRESS, RIGHT_SHIFT);
        InputSystem.dispatch(InputSystem.InputType.KEYBOARD, InputConstants.KEY_N,
                InputConstants.PRESS, InputConstants.MOD_SHIFT);

        assertEquals(3, fires.get());
    }

    @Test
    void combinedAltDefaultFiresWithEitherSideOnRelease() {
        var fires = new AtomicInteger();
        InputSystem.addKeyBinding(
                BINDING,
                InputSystem.combo(InputSystem.InputType.KEYBOARD, InputConstants.KEY_N,
                        InputConstants.RELEASE, InputConstants.MOD_ALT, true),
                _ -> fires.incrementAndGet()
        );

        var snapshotLeft = InputSystem.modifiersForDispatch(InputSystem.InputType.KEYBOARD,
                InputConstants.KEY_N, InputConstants.PRESS, LEFT_ALT);
        releaseKey(InputConstants.KEY_N);
        var dispatchLeft = InputSystem.modifiersForDispatch(InputSystem.InputType.KEYBOARD,
                InputConstants.KEY_N, InputConstants.RELEASE, 0);
        InputSystem.dispatch(InputSystem.InputType.KEYBOARD, InputConstants.KEY_N,
                InputConstants.RELEASE, dispatchLeft);

        var snapshotRight = InputSystem.modifiersForDispatch(InputSystem.InputType.KEYBOARD,
                InputConstants.KEY_N, InputConstants.PRESS, RIGHT_ALT);
        releaseKey(InputConstants.KEY_N);
        var dispatchRight = InputSystem.modifiersForDispatch(InputSystem.InputType.KEYBOARD,
                InputConstants.KEY_N, InputConstants.RELEASE, 0);
        InputSystem.dispatch(InputSystem.InputType.KEYBOARD, InputConstants.KEY_N,
                InputConstants.RELEASE, dispatchRight);

        assertEquals(LEFT_ALT, snapshotLeft);
        assertEquals(RIGHT_ALT, snapshotRight);
        assertEquals(2, fires.get());
    }

    @Test
    void specifiedSideStoredComboFiresOnlyThatSide() {
        var fires = new AtomicInteger();
        InputSystem.addKeyBinding(
                BINDING,
                InputSystem.combo(InputSystem.InputType.KEYBOARD, InputConstants.KEY_N,
                        InputConstants.PRESS, LEFT_SHIFT, true),
                _ -> fires.incrementAndGet()
        );
        pressKey(InputConstants.KEY_N);

        InputSystem.dispatch(InputSystem.InputType.KEYBOARD, InputConstants.KEY_N,
                InputConstants.PRESS, LEFT_SHIFT);
        InputSystem.dispatch(InputSystem.InputType.KEYBOARD, InputConstants.KEY_N,
                InputConstants.PRESS, RIGHT_SHIFT);
        InputSystem.dispatch(InputSystem.InputType.KEYBOARD, InputConstants.KEY_N,
                InputConstants.PRESS, InputConstants.MOD_SHIFT);

        assertEquals(2, fires.get());
    }

    @Test
    void specifiedSideReleaseRequiresThatSide() {
        var fires = new AtomicInteger();
        InputSystem.addKeyBinding(
                BINDING,
                InputSystem.combo(InputSystem.InputType.KEYBOARD, InputConstants.KEY_N,
                        InputConstants.RELEASE, RIGHT_SHIFT, true),
                _ -> fires.incrementAndGet()
        );

        InputSystem.modifiersForDispatch(InputSystem.InputType.KEYBOARD,
                InputConstants.KEY_N, InputConstants.PRESS, RIGHT_SHIFT);
        releaseKey(InputConstants.KEY_N);
        var dispatchRight = InputSystem.modifiersForDispatch(InputSystem.InputType.KEYBOARD,
                InputConstants.KEY_N, InputConstants.RELEASE, 0);
        InputSystem.dispatch(InputSystem.InputType.KEYBOARD, InputConstants.KEY_N,
                InputConstants.RELEASE, dispatchRight);

        InputSystem.modifiersForDispatch(InputSystem.InputType.KEYBOARD,
                InputConstants.KEY_N, InputConstants.PRESS, LEFT_SHIFT);
        releaseKey(InputConstants.KEY_N);
        var dispatchLeft = InputSystem.modifiersForDispatch(InputSystem.InputType.KEYBOARD,
                InputConstants.KEY_N, InputConstants.RELEASE, 0);
        InputSystem.dispatch(InputSystem.InputType.KEYBOARD, InputConstants.KEY_N,
                InputConstants.RELEASE, dispatchLeft);

        assertEquals(SDLKeycode.SDL_KMOD_RSHIFT, dispatchRight);
        assertEquals(SDLKeycode.SDL_KMOD_LSHIFT, dispatchLeft);
        assertEquals(1, fires.get());
    }

    @Test
    void pinModifierSidesAnyCanonicalizesAndPinsKeepSides() {
        assertEquals(InputConstants.MOD_SHIFT,
                InputSystem.pinModifierSides(LEFT_SHIFT, InputSystem.SIDE_ANY));
        assertEquals(InputConstants.MOD_SHIFT | InputConstants.MOD_CONTROL,
                InputSystem.pinModifierSides(LEFT_SHIFT | RIGHT_CTRL, InputSystem.SIDE_ANY));
        assertEquals(LEFT_SHIFT,
                InputSystem.pinModifierSides(LEFT_SHIFT, InputSystem.SIDE_LEFT));
        assertEquals(SDLKeycode.SDL_KMOD_RSHIFT,
                InputSystem.pinModifierSides(LEFT_SHIFT, InputSystem.SIDE_RIGHT));
        assertEquals(RIGHT_CTRL,
                InputSystem.pinModifierSides(LEFT_CTRL | RIGHT_CTRL, InputSystem.SIDE_RIGHT));
        assertEquals(0, InputSystem.pinModifierSides(0, InputSystem.SIDE_LEFT));
    }

    @Test
    void sideAwareDisplayNames() {
        assertEquals("Shift+N", InputSystem.combo(InputSystem.InputType.KEYBOARD,
                InputConstants.KEY_N, InputConstants.PRESS, InputConstants.MOD_SHIFT).displayName());
        assertEquals("LShift+N", InputSystem.combo(InputSystem.InputType.KEYBOARD,
                InputConstants.KEY_N, InputConstants.PRESS, LEFT_SHIFT).displayName());
        assertEquals("RShift+N", InputSystem.combo(InputSystem.InputType.KEYBOARD,
                InputConstants.KEY_N, InputConstants.PRESS, RIGHT_SHIFT).displayName());
        assertEquals("LShift + N", InputSystem.formatKeyCombination(
                InputSystem.combo(InputSystem.InputType.KEYBOARD,
                        InputConstants.KEY_N, InputConstants.PRESS, LEFT_SHIFT)));
    }

    @Test
    void bareKeyBindingDoesNotFireWhenModifiersHeld() {
        var fires = new AtomicInteger();
        InputSystem.addKeyBinding(
                BINDING,
                InputSystem.combo(InputSystem.InputType.KEYBOARD, InputConstants.KEY_N,
                        InputConstants.PRESS, 0, true),
                _ -> fires.incrementAndGet()
        );
        pressKey(InputConstants.KEY_N);

        InputSystem.dispatch(InputSystem.InputType.KEYBOARD, InputConstants.KEY_N,
                InputConstants.PRESS, LEFT_SHIFT);
        InputSystem.dispatch(InputSystem.InputType.KEYBOARD, InputConstants.KEY_N,
                InputConstants.PRESS, LEFT_CTRL);
        InputSystem.dispatch(InputSystem.InputType.KEYBOARD, InputConstants.KEY_N,
                InputConstants.PRESS, 0);

        assertEquals(1, fires.get());
    }

    @Test
    void ctrlSidesAreEquivalent() {
        var fires = new AtomicInteger();
        InputSystem.addKeyBinding(
                BINDING,
                InputSystem.combo(InputSystem.InputType.KEYBOARD, InputConstants.KEY_R,
                        InputConstants.PRESS, InputConstants.MOD_CONTROL, true),
                _ -> fires.incrementAndGet()
        );
        pressKey(InputConstants.KEY_R);

        InputSystem.dispatch(InputSystem.InputType.KEYBOARD, InputConstants.KEY_R,
                InputConstants.PRESS, LEFT_CTRL);
        InputSystem.dispatch(InputSystem.InputType.KEYBOARD, InputConstants.KEY_R,
                InputConstants.PRESS, RIGHT_CTRL);

        assertEquals(2, fires.get());
    }

    @Test
    void pairedReleaseArmsAcrossShiftSides() {
        var stops = new AtomicInteger();
        InputSystem.addKeyBinding(START,
                InputSystem.combo(InputSystem.InputType.KEYBOARD, InputConstants.KEY_G,
                        InputConstants.PRESS, LEFT_SHIFT, true),
                _ -> {
                });
        InputSystem.addKeyBinding(STOP,
                InputSystem.combo(InputSystem.InputType.KEYBOARD, InputConstants.KEY_G,
                        InputConstants.RELEASE, InputConstants.MOD_SHIFT, true),
                _ -> stops.incrementAndGet());
        pressKey(InputConstants.KEY_G);

        InputSystem.dispatch(InputSystem.InputType.KEYBOARD, InputConstants.KEY_G,
                InputConstants.PRESS, LEFT_SHIFT);
        InputSystem.dispatch(InputSystem.InputType.KEYBOARD, InputConstants.KEY_G,
                InputConstants.RELEASE, InputConstants.MOD_SHIFT);

        assertEquals(1, stops.get());
    }

    private static void pressKey(int key) {
        keyboardState().put(key, InputConstants.PRESS);
    }

    private static void releaseKey(int key) {
        keyboardState().put(key, InputConstants.RELEASE);
    }

    @SuppressWarnings("unchecked")
    private static Map<Integer, Integer> keyboardState() {
        try {
            Field field = InputSystem.class.getDeclaredField("KEYBOARD_STATE");
            field.setAccessible(true);
            return (Map<Integer, Integer>) field.get(null);
        } catch (ReflectiveOperationException e) {
            throw new RuntimeException(e);
        }
    }

    private static void clearState() {
        keyboardState().clear();
        InputSystem.clearModifierSnapshotsForTesting();
    }
}
