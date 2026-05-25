package org.academy.api.client.gui.command

import com.mojang.blaze3d.pipeline.RenderPipeline
import com.mojang.blaze3d.vertex.VertexConsumer
import org.academy.api.client.render.TextureBinding
import org.academy.api.client.render.UniformPayload
import org.joml.Matrix4f

abstract class DrawCommand protected constructor(
    pipeline: RenderPipeline,
    textures: List<TextureBinding>,
    uniforms: List<UniformPayload<*>>
) {
    val pipeline: RenderPipeline
    val textures: List<TextureBinding>
    val uniforms: List<UniformPayload<*>>

    init {
        validatePipelineMode(pipeline)
        this.pipeline = pipeline
        this.textures = textures
        this.uniforms = uniforms
    }

    abstract fun generateVertices(consumer: VertexConsumer, pose: Matrix4f)

    companion object {
        private fun validatePipelineMode(pipeline: RenderPipeline) {
            val mode = pipeline.vertexFormatMode
            require(!mode.connectedPrimitives) {
                ("Connected primitive modes (e.g., TRIANGLE_STRIP, LINE_STRIP) are forbidden in DrawCommands. "
                        + "To ensure correct batching, please generate independent triangles using TRIANGLES or QUADS mode.")
            }
        }
    }
}