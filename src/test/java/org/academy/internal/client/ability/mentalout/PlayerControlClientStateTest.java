package org.academy.internal.client.ability.mentalout;

import org.academy.api.common.entitycontrol.PlayerControlFrame;
import org.academy.api.common.entitycontrol.PlayerMovementMode;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PlayerControlClientStateTest {
    @Test
    void authorizedFrameProjectsToClientInputWithoutKeyMappings() {
        var frame = new PlayerControlFrame(
                1.0f, -0.5f, 30.0f, -10.0f,
                true, true, true, false, false, PlayerMovementMode.JUMP
        );
        var input = PlayerControlClientState.inputForFrame(frame);
        assertTrue(input.forward());
        assertFalse(input.backward());
        assertFalse(input.left());
        assertTrue(input.right());
        assertTrue(input.jump());
        assertTrue(input.shift());
        assertTrue(input.sprint());

        var movement = PlayerControlClientState.moveVectorForFrame(frame);
        assertEquals(-0.4472f, movement.x, 0.0002f);
        assertEquals(0.8944f, movement.y, 0.0002f);
    }
}
