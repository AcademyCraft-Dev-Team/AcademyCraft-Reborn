package org.academy.internal.client.renderer.vfx;

import net.minecraft.resources.Identifier;
import org.academy.api.client.render.vfx.VfxRenderData;
import org.joml.Vector3f;
import org.joml.Vector4f;

public interface SkyStrikeWorldData extends VfxRenderData {
    Identifier texture();

    Vector3f corner0();

    Vector3f corner1();

    Vector3f corner2();

    Vector3f corner3();

    Vector4f color();
}
