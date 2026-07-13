package org.academy.api.client.gui.command

import com.mojang.blaze3d.systems.RenderSystem
import com.mojang.blaze3d.textures.FilterMode
import com.mojang.blaze3d.textures.GpuTextureView
import com.mojang.blaze3d.vertex.PoseStack
import org.academy.api.client.Render
import org.academy.api.client.gui.render.VertexWriter
import org.academy.api.client.render.MsdfUniformData
import org.academy.api.client.render.TextureBinding
import org.academy.api.client.render.UniformPayload
import org.joml.Vector3f
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
            MsdfUniformData(range, thickness, 0.0f, Vector4f(0f)),
            MsdfUniformData.UBO_SIZE
        )
    )
) {
    override fun isGeometryFixed(): Boolean = true

    override fun generateVertices(writer: VertexWriter, pose: PoseStack.Pose) {
        writer.beginVertex()
        writer.putVec3f(0f, 0f, 0f)

        writer.beginVertex()
        writer.putVec3f(0f, 1f, 0f)

        writer.beginVertex()
        writer.putVec3f(1f, 1f, 0f)

        writer.beginVertex()
        writer.putVec3f(1f, 0f, 0f)
    }

    override fun generateInstanceData(slot: Int, writer: VertexWriter, instanceIndex: Int, pose: PoseStack.Pose) {
        if (slot == 1) {
            val matrix = pose.pose()
            val start = Vector3f()
            val end = Vector3f()
            matrix.transformPosition(x, y, 0f, start)
            matrix.transformPosition(x + quadWidth, y + quadHeight, 0f, end)

            val tx = start.x
            val ty = start.y
            val tw = end.x - start.x
            val th = end.y - start.y

            writer.beginVertex()
            writer.putVec3f(tx, ty, start.z)
            writer.putVec2f(tw, th)
            writer.putVec2f(u0, v0)
            writer.putVec2f(u1, v1)
            writer.putVec4f(red, green, blue, alpha)
        }
    }
}
