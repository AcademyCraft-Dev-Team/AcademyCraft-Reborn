package org.academy.internal.client.ability.mentalout;

/** Provides access to the stateful interpolation values owned by Minecraft's shared main camera. */
public interface CameraSmoothingBridge {
    CameraSmoothingState academy$captureSmoothingState();

    void academy$restoreSmoothingState(CameraSmoothingState state);

    record CameraSmoothingState(
            float eyeHeight,
            float previousEyeHeight,
            float fovModifier,
            float previousFovModifier
    ) {
    }
}
