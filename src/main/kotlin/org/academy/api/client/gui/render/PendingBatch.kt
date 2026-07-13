package org.academy.api.client.gui.render

import com.mojang.blaze3d.pipeline.RenderPipeline
import com.mojang.blaze3d.vertex.MeshData
import org.academy.api.client.render.TextureBinding
import org.academy.api.client.render.UniformBinding

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
)
