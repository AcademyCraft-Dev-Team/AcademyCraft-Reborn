package org.academy.internal.client.render.vfx;

import org.joml.Vector3f;

public record BeamGlowData(
        Vector3f pos,
        float yRot,
        float xRot,
        float length,
        float progress,
        boolean isCharging,
        float widthScale,
        float ballScale
) implements BeamData {
}
