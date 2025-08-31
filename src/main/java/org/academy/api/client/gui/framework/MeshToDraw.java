package org.academy.api.client.gui.framework;

import com.mojang.blaze3d.buffers.GpuBufferSlice;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.textures.GpuTextureView;
import com.mojang.blaze3d.vertex.MeshData;
import java.util.Map;
import javax.annotation.Nullable;

public final class MeshToDraw implements AutoCloseable {
    private final MeshData mesh;
    private final RenderPipeline pipeline;
    @Nullable
    private final ScissorRect scissorArea;
    private final Map<String, GpuTextureView> samplers;
    private final Map<String, GpuBufferSlice> uniforms;

    public MeshToDraw(
            MeshData mesh,
            RenderPipeline pipeline,
            @Nullable ScissorRect scissorArea,
            Map<String, GpuTextureView> samplers,
            Map<String, GpuBufferSlice> uniforms
    ) {
        this.mesh = mesh;
        this.pipeline = pipeline;
        this.scissorArea = scissorArea;
        this.samplers = samplers;
        this.uniforms = uniforms;
    }

    public MeshData getMesh() {
        return this.mesh;
    }

    public RenderPipeline getPipeline() {
        return this.pipeline;
    }

    @Nullable
    public ScissorRect getScissorArea() {
        return this.scissorArea;
    }

    public Map<String, GpuTextureView> getSamplers() {
        return this.samplers;
    }

    public Map<String, GpuBufferSlice> getUniforms() {
        return this.uniforms;
    }

    @Override
    public void close() {
        this.mesh.close();
    }
}