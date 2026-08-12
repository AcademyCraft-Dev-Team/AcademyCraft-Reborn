package org.academy.api.client.render.vfx;

import com.mojang.blaze3d.buffers.GpuBufferSlice;
import com.mojang.blaze3d.systems.GpuDevice;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.GpuTextureView;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.jspecify.annotations.Nullable;

import java.util.Objects;

public interface VfxRenderContext {
    GpuDevice device();

    float gameTime();

    Vector3f cameraPos();

    Quaternionf cameraOrientation();

    /**
     * Camera-relative world-to-view rotation captured for this VFX frame.
     */
    default Matrix4f viewRotationMatrix() {
        return RenderSystem.getModelViewMatrixCopy();
    }

    /**
     * Exact world projection uniform bound when this VFX frame was sampled. This
     * includes camera bob/hurt transforms and remains stable if a shader pack
     * switches RenderSystem's global projection before a deferred VFX pass.
     */
    default GpuBufferSlice projectionUniform() {
        return Objects.requireNonNull(RenderSystem.getProjectionMatrixBuffer());
    }

    @Nullable GpuTextureView mainColor();

    @Nullable GpuTextureView mainDepth();

    @Nullable GpuTextureView bloomInputColor();

    @Nullable GpuTextureView bloomInputDepth();

    @Nullable GpuTextureView sceneColor();
}
