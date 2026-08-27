package org.academy.api.client.input;

import com.mojang.blaze3d.platform.InputConstants;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class InputSystemPrimaryBindingTest {
    private static final String TEST_BINDING = "input_system_test_use";

    @AfterEach
    void tearDown() {
        InputSystem.removeKeyBinding(TEST_BINDING);
    }

    @Test
    void baseUseAndToggleBindingsTakePriorityOverBranches() {
        assertTrue(InputSystem.primaryBindingPriority("vector_blast_use", "vector_blast")
                < InputSystem.primaryBindingPriority("vector_blast_pull_start", "vector_blast"));
        assertTrue(InputSystem.primaryBindingPriority(
                "kinetic_energy_applied_toggle", "kinetic_energy_applied")
                < InputSystem.primaryBindingPriority(
                "kinetic_energy_applied_block_break_toggle", "kinetic_energy_applied"));
    }

    @Test
    void baseStartAndEndRemainOnePrimaryMaintainedGesture() {
        assertEquals(InputSystem.primaryBindingPriority("railgun_start", "railgun"), InputSystem.primaryBindingPriority("railgun_end", "railgun"));
    }

    @Test
    void gestureMatchingIgnoresPhaseButKeepsModifiers() {
        InputSystem.addKeyBinding(
                TEST_BINDING,
                InputSystem.combo(
                        InputSystem.InputType.MOUSE,
                        InputConstants.MOUSE_BUTTON_LEFT,
                        InputConstants.RELEASE,
                        InputConstants.MOD_ALT
                ),
                ignored -> {
                }
        );

        assertTrue(InputSystem.matchesKeyBindingGesture(
                TEST_BINDING,
                new InputSystem.BindingContext(
                        InputSystem.InputType.MOUSE,
                        InputConstants.MOUSE_BUTTON_LEFT,
                        InputConstants.PRESS,
                        InputConstants.MOD_ALT
                )
        ));
        assertFalse(InputSystem.matchesKeyBindingGesture(
                TEST_BINDING,
                new InputSystem.BindingContext(
                        InputSystem.InputType.MOUSE,
                        InputConstants.MOUSE_BUTTON_LEFT,
                        InputConstants.PRESS,
                        InputConstants.MOD_CONTROL
                )
        ));
    }

    @Test
    void unboundBindingIsSafelyInactive() {
        InputSystem.addKeyBinding(
                TEST_BINDING,
                InputSystem.unbound(InputSystem.combo(
                        InputSystem.InputType.KEYBOARD,
                        InputConstants.KEY_Y,
                        InputConstants.PRESS,
                        0
                )),
                ignored -> {
                }
        );

        var binding = InputSystem.getKeyBinding(TEST_BINDING);
        assertTrue(binding.unbound());
        assertEquals("None", InputSystem.formatKeyCombination(binding));
        assertFalse(InputSystem.matchesKeyBindingGesture(
                TEST_BINDING,
                new InputSystem.BindingContext(
                        InputSystem.InputType.KEYBOARD,
                        InputConstants.KEY_Y,
                        InputConstants.PRESS,
                        0
                )
        ));
    }
}
