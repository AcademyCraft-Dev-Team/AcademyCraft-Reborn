package org.academy.internal.client.render.vfx;

import org.academy.api.client.render.vfx.VfxRenderData;
import org.joml.Vector3f;

public record PlasmaCoreData(Vector3f pos, float size, float alpha) implements VfxRenderData {
}
