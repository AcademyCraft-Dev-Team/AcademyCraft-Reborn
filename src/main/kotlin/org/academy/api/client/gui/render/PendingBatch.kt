package org.academy.api.client.gui.render

import com.mojang.blaze3d.pipeline.RenderPipeline
import com.mojang.blaze3d.vertex.MeshData
import org.academy.api.client.render.TextureBinding
import org.academy.api.client.render.UniformBinding
import java.lang.AutoCloseable

data class PendingBatch(
    val meshData: MeshData,
    val pipeline: RenderPipeline,
    val scissorArea: ScissorRect?,
    val textures: List<TextureBinding>,
    val uniforms: List<UniformBinding>,
    val vertexBufferSize: Int,
    val vertexStride: Int,
    val indexCount: Int
) : AutoCloseable {
    constructor(
        meshData: MeshData, pipeline: RenderPipeline, scissorArea: ScissorRect?,
        textures: List<TextureBinding>, uniforms: List<UniformBinding>
    ) : this(
        meshData, pipeline, scissorArea,
        textures, uniforms,
        meshData.vertexBuffer().remaining(),
        meshData.drawState().format().vertexSize,
        meshData.drawState().indexCount()
    )

    override fun close() {
        meshData.close()
    }
}