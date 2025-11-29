package org.academy.api.client.gui.render;

import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.vertex.MeshData;
import org.academy.api.client.render.TextureBinding;
import org.academy.api.client.render.UniformBinding;
import org.jspecify.annotations.Nullable;

import java.util.List;

public record PendingBatch(
        MeshData meshData,
        RenderPipeline pipeline,
        @Nullable ScissorRect scissorArea,
        List<TextureBinding> textures,
        List<UniformBinding> uniforms,
        int vertexBufferSize,
        int vertexStride,
        int indexCount
) implements AutoCloseable {

    public PendingBatch(
            MeshData meshData, RenderPipeline pipeline, @Nullable ScissorRect scissorArea,
            List<TextureBinding> textures, List<UniformBinding> uniforms
    ) {
        this(
                meshData, pipeline, scissorArea,
                textures, uniforms,
                meshData.vertexBuffer().remaining(),
                meshData.drawState().format().getVertexSize(),
                meshData.drawState().indexCount()
        );
    }

    @Override
    public void close() {
        meshData.close();
    }
}