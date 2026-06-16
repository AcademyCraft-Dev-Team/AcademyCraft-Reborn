package org.academy.api.client.gui.command

import com.mojang.blaze3d.systems.RenderSystem
import com.mojang.blaze3d.textures.FilterMode
import com.mojang.blaze3d.textures.GpuTextureView
import com.mojang.blaze3d.vertex.PoseStack
import com.mojang.blaze3d.vertex.VertexConsumer
import org.academy.api.client.Render
import org.academy.api.client.render.MsdfUniformData
import org.academy.api.client.render.TextureBinding
import org.academy.api.client.render.UniformPayload
import org.joml.Vector4f

class GlyphDrawCommand(
    textureView: GpuTextureView,
    private val x: Float,
    private val y: Float,
    private val quadWidth: Float, private val quadHeight: Float,
    private val u0: Float, private val v0: Float, private val u1: Float, private val v1: Float,
    private val red: Float, private val green: Float, private val blue: Float, private val alpha: Float,
    range: Float,
    thickness: Float
) : DrawCommand(
    Render.RenderPipelines.MSDF_TEXT,
    listOf(
        TextureBinding(
            "Sampler0",
            textureView,
            RenderSystem.getSamplerCache().getClampToEdge(FilterMode.LINEAR)
        )
    ),
    listOf(
        UniformPayload(
            "MsdfUniforms",
            MsdfUniformData::class.java,
            MsdfUniformData(
                range,
                thickness,
                0.0f,
                Vector4f(0f, 0f, 0f, 0f)
            ),
            MsdfUniformData.UBO_SIZE
        )
    )
) {
    override fun generateVertices(consumer: VertexConsumer, pose: PoseStack.Pose) {
        val x0 = x
        val y0 = y
        val y1 = y + quadHeight
        val x1 = x + quadWidth

        consumer.addVertex(pose, x0, y0, 0f).setUv(u0, v0).setColor(red, green, blue, alpha)
        consumer.addVertex(pose, x0, y1, 0f).setUv(u0, v1).setColor(red, green, blue, alpha)
        consumer.addVertex(pose, x1, y1, 0f).setUv(u1, v1).setColor(red, green, blue, alpha)
        consumer.addVertex(pose, x1, y0, 0f).setUv(u1, v0).setColor(red, green, blue, alpha)
    }
}