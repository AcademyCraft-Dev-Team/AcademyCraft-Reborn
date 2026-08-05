package org.academy.api.client.vanilla;

import net.minecraft.client.renderer.entity.player.AvatarRenderer;
import org.joml.Matrix4f;
import org.jspecify.annotations.Nullable;

public interface AvatarRendererContext {
    @Nullable Matrix4f takeModelRootMatrix();
}
