package org.academy.api.client.gui.framework;

import com.mojang.blaze3d.buffers.GpuBufferSlice;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.textures.GpuTextureView;
import com.mojang.blaze3d.vertex.MeshData;

import javax.annotation.Nullable;
import java.util.Map;

public record MeshToDraw(MeshData mesh, RenderPipeline pipeline, @Nullable ScissorRect scissorArea,
                         Map<String, GpuTextureView> samplers,
                         Map<String, GpuBufferSlice> uniforms) implements AutoCloseable {
    @Override
    public void close() {
        mesh.close();
    }
}