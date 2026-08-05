package org.academy.api.client.render.vfx.data;

import org.academy.api.client.render.vfx.VfxRenderData;
import org.joml.Vector3f;

public record ParticleData(
        Vector3f pos,
        float size,
        float r,
        float g,
        float b,
        float a
) implements VfxRenderData {
}
