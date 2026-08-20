package org.academy.internal.client.animation;

import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class WingFlightAnimationClientTest {
    @Test
    void compactWingPoseDoesNotTriggerVanillaSwimmingRotation() {
        var state = new AvatarRenderState();
        state.isVisuallySwimming = true;
        state.swimAmount = 1.0f;

        WingFlightAnimationClient.suppressVanillaCompactPoseAnimation(state);

        assertFalse(state.isVisuallySwimming);
        assertEquals(0.0f, state.swimAmount);
    }
}
