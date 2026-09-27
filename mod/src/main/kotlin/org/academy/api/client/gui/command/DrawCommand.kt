package org.academy.api.client.gui.command

import com.mojang.blaze3d.vertex.PoseStack
import com.mojang.renderpearl.api.pipeline.RenderPipeline
import org.academy.api.client.gui.render.VertexWriter
import org.academy.api.client.render.TextureBinding
import org.academy.api.client.render.UniformPayload

data class LocalBounds(
    val left: Float,
    val top: Float,
    val right: Float,
    val bottom: Float
)

abstract class DrawCommand protected constructor(
    val pipeline: RenderPipeline,
    val textures: List<TextureBinding>,
    val uniforms: List<UniformPayload<*>>
) {
    init {
        validatePipelineMode(pipeline)
    }

    abstract fun generateVertices(writer: VertexWriter, pose: PoseStack.Pose, alphaMul: Float)

    open fun localBounds(): LocalBounds? = null

    open fun allowsOverlapMerge(): Boolean = false

    open fun isGeometryFixed(): Boolean = false

    open fun generateInstanceData(
        slot: Int,
        writer: VertexWriter,
        instanceIndex: Int,
        pose: PoseStack.Pose,
        alphaMul: Float
    ) {
    }

    open fun instanceCount(pose: PoseStack.Pose, alphaMul: Float): Int = 1

    open fun appendInstances(slot: Int, writer: VertexWriter, pose: PoseStack.Pose, alphaMul: Float) {
        generateInstanceData(slot, writer, 0, pose, alphaMul)
    }

    companion object {
        private fun validatePipelineMode(pipeline: RenderPipeline) {
            require(!pipeline.primitiveTopology.connectedPrimitives) {
                ("Connected primitive modes (e.g., TRIANGLE_STRIP, LINE_STRIP) are forbidden in DrawCommands. "
                        + "To ensure correct batching, please generate independent triangles using TRIANGLES or QUADS mode.")
            }
        }
    }
}
