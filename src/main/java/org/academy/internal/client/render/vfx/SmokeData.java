package org.academy.internal.client.render.vfx;

import org.academy.api.client.render.vfx.VfxRenderData;
import org.joml.Vector3f;

public record SmokeData(
        Vector3f pos,
        float size,
        float alpha,
        float u0,
        float v0,
        float u1,
        float v1
) implements VfxRenderData {
}
