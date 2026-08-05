package org.academy.internal.client.renderer.vfx;

import org.academy.api.client.render.vfx.VfxRenderData;
import org.joml.Vector3f;

public interface BeamData extends VfxRenderData {
    Vector3f pos();

    float yRot();

    float xRot();

    float length();

    float progress();

    boolean isCharging();
}
