package org.academy.internal.client.ability.mentalout;

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
