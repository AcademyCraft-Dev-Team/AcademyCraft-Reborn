package org.academy.internal.client.render.vfxgraph;

import com.mojang.renderpearl.api.buffers.GpuBufferSlice;
import org.joml.Matrix4f;
import org.joml.Vector3f;

public record FrameContext(
        Vector3f cameraPos,
        float gameTime,
        float partialTick,
        float dt,
        Matrix4f projectionMatrix,
        Matrix4f viewRotationMatrix,
        GpuBufferSlice projectionUniform
) {
}