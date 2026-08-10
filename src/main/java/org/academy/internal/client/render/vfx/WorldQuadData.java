package org.academy.internal.client.render.vfx;

import net.minecraft.resources.Identifier;
import org.academy.api.client.render.vfx.VfxRenderData;
import org.joml.Vector2f;
import org.joml.Vector3f;
import org.joml.Vector4f;

public interface WorldQuadData extends VfxRenderData {
    Identifier texture();

    Vector3f corner0();

    Vector3f corner1();

    Vector3f corner2();

    Vector3f corner3();

    Vector2f uv0();

    Vector2f uv1();

    Vector2f uv2();

    Vector2f uv3();

    Vector4f color();
}
