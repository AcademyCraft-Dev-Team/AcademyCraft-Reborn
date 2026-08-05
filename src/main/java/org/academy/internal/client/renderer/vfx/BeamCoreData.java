package org.academy.internal.client.renderer.vfx;

import org.joml.Vector3f;

public record BeamCoreData(
        Vector3f pos,
        float yRot,
        float xRot,
        float length,
        float progress,
        boolean isCharging
) implements BeamData {
}
