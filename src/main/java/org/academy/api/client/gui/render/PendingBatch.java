package org.academy.api.client.gui.render;

import com.mojang.blaze3d.buffers.GpuBufferSlice;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.textures.GpuTextureView;
import com.mojang.blaze3d.vertex.MeshData;
import org.jspecify.annotations.Nullable;

import java.util.Map;

public record PendingBatch(
        MeshData meshData,
        RenderPipeline pipeline,
        @Nullable ScissorRect scissorArea,
        Map<String, GpuTextureView> samplers,
        Map<String, GpuBufferSlice> uniforms,
        int vertexBufferSize,
        int vertexStride,
        int indexCount
) implements AutoCloseable {

    public PendingBatch(MeshData meshData, RenderPipeline pipeline, @Nullable ScissorRect scissorArea,
                        Map<String, GpuTextureView> samplers, Map<String, GpuBufferSlice> uniforms) {
        this(meshData, pipeline, scissorArea, samplers, uniforms,
                meshData.vertexBuffer().remaining(),
                meshData.drawState().format().getVertexSize(),
                meshData.drawState().indexCount());
    }

    @Override
    public void close() {
        meshData.close();
    }
}