package org.academy.api.client.render.vfx;

import com.mojang.blaze3d.buffers.GpuBufferSlice;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.jspecify.annotations.Nullable;

public record VfxCamera(
        Vector3f pos,
        Quaternionf orientation,
        Matrix4f projectionMatrix,
        Matrix4f viewRotationMatrix,
        float fov,
        @Nullable GpuBufferSlice projectionUniform
) {
    public VfxCamera(
            Vector3f pos,
            Quaternionf orientation,
            Matrix4f projectionMatrix,
            Matrix4f viewRotationMatrix,
            float fov
    ) {
        this(pos, orientation, projectionMatrix, viewRotationMatrix, fov, null);
    }
}
