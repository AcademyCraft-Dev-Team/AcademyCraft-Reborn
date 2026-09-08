package org.academy.api.client.input;

import com.mojang.blaze3d.platform.InputConstants;
import org.junit.jupiter.api.Test;
import java.util.ArrayList;
import static org.junit.jupiter.api.Assertions.assertEquals;

class InputSystemExclusiveBindingTest {
    @Test
    void reboundSelectedCastOwnsBothPhasesAndReleasesTheOldKey() {
        var phases = new ArrayList<Integer>();
        var ordinary = new ArrayList<Integer>();
        var type = InputSystem.InputType.KEYBOARD;
        try {
            InputSystem.addExclusiveKeyBinding("test_selected_cast",
                    InputSystem.combo(type, InputConstants.KEY_C, InputConstants.RELEASE, 0, true),
                    event -> phases.add(event.action()), () -> true);
            InputSystem.addKeyBinding("test_other_skill",
                    InputSystem.combo(type, InputConstants.KEY_J, InputSystem.ANY_ACTION, 0, true),
                    event -> ordinary.add(event.action()));
            InputSystem.updateKeyBinding("test_selected_cast",
                    InputSystem.combo(type, InputConstants.KEY_J, InputConstants.RELEASE, 0, true));
            InputSystem.dispatch(type, InputConstants.KEY_C, InputConstants.RELEASE, 0);
            InputSystem.dispatch(type, InputConstants.KEY_J, InputConstants.PRESS, 0);
            InputSystem.dispatch(type, InputConstants.KEY_J, InputConstants.RELEASE, 0);
            assertEquals(java.util.List.of(InputConstants.PRESS, InputConstants.RELEASE), phases);
            assertEquals(java.util.List.of(), ordinary);
        } finally {
            InputSystem.removeKeyBinding("test_selected_cast");
            InputSystem.removeKeyBinding("test_other_skill");
        }
    }
}
