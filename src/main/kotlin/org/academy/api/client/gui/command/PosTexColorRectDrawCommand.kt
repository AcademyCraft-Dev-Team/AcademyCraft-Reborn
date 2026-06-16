package org.academy.api.client.gui.command

import com.mojang.blaze3d.pipeline.RenderPipeline
import com.mojang.blaze3d.vertex.PoseStack
import com.mojang.blaze3d.vertex.VertexConsumer
import org.academy.api.client.render.TextureBinding
import org.academy.api.client.render.UniformPayload

abstract class PosTexColorRectDrawCommand protected constructor(
    pipeline: RenderPipeline,
    protected val width: Float,
    protected val height: Float,
    protected val u0: Float,
    protected val v0: Float,
    protected val u1: Float,
    protected val v1: Float,
    protected val red: Float,
    protected val green: Float,
    protected val blue: Float,
    protected val alpha: Float,
    textures: List<TextureBinding>,
    uniforms: List<UniformPayload<*>>
) : DrawCommand(pipeline, textures, uniforms) {
    override fun generateVertices(consumer: VertexConsumer, pose: PoseStack.Pose) {
        consumer.addVertex(pose, 0.0f, 0.0f, 0.0f).setUv(u0, v0).setColor(red, green, blue, alpha)
        consumer.addVertex(pose, 0.0f, height, 0.0f).setUv(u0, v1).setColor(red, green, blue, alpha)
        consumer.addVertex(pose, width, height, 0.0f).setUv(u1, v1).setColor(red, green, blue, alpha)
        consumer.addVertex(pose, width, 0.0f, 0.0f).setUv(u1, v0).setColor(red, green, blue, alpha)
    }
}