package org.academy.api.client.input;

import com.mojang.blaze3d.platform.InputConstants;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class InputSystemModifierSnapshotTest {
    @BeforeEach
    void setUp() {
        InputSystem.clearModifierSnapshotsForTesting();
    }

    @AfterEach
    void tearDown() {
        InputSystem.clearModifierSnapshotsForTesting();
    }

    @Test
    void keyboardReleaseUsesModifiersCapturedWhenMainKeyWasPressed() {
        InputSystem.modifiersForDispatch(
                InputSystem.InputType.KEYBOARD,
                InputConstants.KEY_R,
                InputConstants.PRESS,
                InputConstants.MOD_ALT
        );

        assertEquals(
                InputConstants.MOD_ALT,
                InputSystem.modifiersForDispatch(
                        InputSystem.InputType.KEYBOARD,
                        InputConstants.KEY_R,
                        InputConstants.RELEASE,
                        0
                )
        );
    }

    @Test
    void mouseReleaseUsesModifiersCapturedWhenButtonWasPressed() {
        InputSystem.modifiersForDispatch(
                InputSystem.InputType.MOUSE,
                InputConstants.MOUSE_BUTTON_RIGHT,
                InputConstants.PRESS,
                InputConstants.MOD_ALT
        );

        assertEquals(
                InputConstants.MOD_ALT,
                InputSystem.modifiersForDispatch(
                        InputSystem.InputType.MOUSE,
                        InputConstants.MOUSE_BUTTON_RIGHT,
                        InputConstants.RELEASE,
                        0
                )
        );
    }

    @Test
    void releaseWithoutMatchingPressUsesCurrentModifiers() {
        assertEquals(
                InputConstants.MOD_SHIFT,
                InputSystem.modifiersForDispatch(
                        InputSystem.InputType.KEYBOARD,
                        InputConstants.KEY_R,
                        InputConstants.RELEASE,
                        InputConstants.MOD_SHIFT
                )
        );
    }
}
