package org.academy.api.client.render.vfx;

import com.mojang.blaze3d.systems.GpuDevice;
import com.mojang.blaze3d.textures.GpuTextureView;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.jspecify.annotations.Nullable;

public interface VfxRenderContext {
    GpuDevice device();

    float gameTime();

    Vector3f cameraPos();

    Quaternionf cameraOrientation();

    @Nullable GpuTextureView mainColor();

    @Nullable GpuTextureView mainDepth();

    @Nullable GpuTextureView bloomInputColor();

    @Nullable GpuTextureView bloomInputDepth();

    @Nullable GpuTextureView sceneColor();
}
