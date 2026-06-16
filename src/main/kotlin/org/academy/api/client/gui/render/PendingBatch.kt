package org.academy.api.client.gui.render

import com.mojang.blaze3d.pipeline.RenderPipeline
import com.mojang.blaze3d.vertex.MeshData
import org.academy.api.client.render.TextureBinding
import org.academy.api.client.render.UniformBinding
import java.lang.AutoCloseable

data class PendingBatch(
    val meshDataList: List<MeshData>,
    val slotIndices: List<Int>,
    val pipeline: RenderPipeline,
    val scissorArea: ScissorRect?,
    val textures: List<TextureBinding>,
    val uniforms: List<UniformBinding>,
    val indexCount: Int,
    val vertexStride: Int,
    val instanceCount: Int
) : AutoCloseable {
    constructor(
        meshData: MeshData, pipeline: RenderPipeline, scissorArea: ScissorRect?,
        textures: List<TextureBinding>, uniforms: List<UniformBinding>
    ) : this(
        listOf(meshData), listOf(0), pipeline, scissorArea,
        textures, uniforms,
        meshData.drawState().indexCount(),
        meshData.drawState().format().vertexSize,
        1
    )

    override fun close() {
        meshDataList.forEach { it.close() }
    }
}