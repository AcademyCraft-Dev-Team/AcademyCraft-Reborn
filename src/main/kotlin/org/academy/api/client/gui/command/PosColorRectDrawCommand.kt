package org.academy.api.client.gui.command

import com.mojang.blaze3d.pipeline.RenderPipeline
import com.mojang.blaze3d.vertex.VertexConsumer
import org.academy.api.client.render.TextureBinding
import org.academy.api.client.render.UniformPayload
import org.joml.Matrix4f

abstract class PosColorRectDrawCommand protected constructor(
    pipeline: RenderPipeline,
    protected val width: Float,
    protected val height: Float,
    protected val red: Float,
    protected val green: Float,
    protected val blue: Float,
    protected val alpha: Float,
    textures: List<TextureBinding>,
    uniforms: List<UniformPayload<*>>
) : DrawCommand(pipeline, textures, uniforms) {
    override fun generateVertices(consumer: VertexConsumer, pose: Matrix4f) {
        consumer.addVertex(pose, 0.0f, 0.0f, 0.0f).setColor(red, green, blue, alpha)
        consumer.addVertex(pose, 0.0f, height, 0.0f).setColor(red, green, blue, alpha)
        consumer.addVertex(pose, width, height, 0.0f).setColor(red, green, blue, alpha)
        consumer.addVertex(pose, width, 0.0f, 0.0f).setColor(red, green, blue, alpha)
    }
}