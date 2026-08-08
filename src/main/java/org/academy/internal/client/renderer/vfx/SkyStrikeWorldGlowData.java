package org.academy.internal.client.renderer.vfx;

import net.minecraft.resources.Identifier;
import org.joml.Vector3f;
import org.joml.Vector4f;

public record SkyStrikeWorldGlowData(
        Identifier texture,
        Vector3f corner0,
        Vector3f corner1,
        Vector3f corner2,
        Vector3f corner3,
        Vector4f color
) implements SkyStrikeWorldData {
}
