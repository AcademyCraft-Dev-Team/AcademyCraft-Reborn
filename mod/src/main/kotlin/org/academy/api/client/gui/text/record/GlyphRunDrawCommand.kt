package org.academy.api.client.gui.text.record

import com.mojang.blaze3d.vertex.PoseStack
import com.mojang.renderpearl.api.pipeline.RenderPipeline
import com.mojang.renderpearl.api.textures.GpuSampler
import com.mojang.renderpearl.api.textures.GpuTextureView
import org.academy.api.client.gui.command.DrawCommand
import org.academy.api.client.gui.command.LocalBounds
import org.academy.api.client.gui.command.PosColorRectDrawCommand
import org.academy.api.client.gui.render.VertexWriter
import org.academy.api.client.render.TextureBinding
import org.academy.api.client.render.UniformPayload
import org.joml.Vector3f
import kotlin.math.max
import kotlin.math.min

abstract class GlyphRunDrawCommand protected constructor(
    pipeline: RenderPipeline,
    textureView: GpuTextureView,
    private val quads: List<GlyphQuad>,
    uniforms: List<UniformPayload<*>>,
    sampler: GpuSampler
) : DrawCommand(
    pipeline,
    listOf(
        TextureBinding(
            "Sampler0",
            textureView,
            sampler
        )
    ),
    uniforms
) {
    final override fun isGeometryFixed(): Boolean = true

    override fun allowsOverlapMerge(): Boolean = true

    override fun localBounds(): LocalBounds {
        if (quads.isEmpty()) return LocalBounds(0f, 0f, 0f, 0f)
        var left = Float.MAX_VALUE
        var top = Float.MAX_VALUE
        var right = -Float.MAX_VALUE
        var bottom = -Float.MAX_VALUE
        for ((x, y, width, height) in quads) {
            left = min(left, x)
            top = min(top, y)
            right = max(right, x + width)
            bottom = max(bottom, y + height)
        }
        val aa = PosColorRectDrawCommand.AA
        return LocalBounds(left - aa, top - aa, right + aa, bottom + aa)
    }

    final override fun generateVertices(writer: VertexWriter, pose: PoseStack.Pose, alphaMul: Float) {
        writer.beginVertex()
        writer.putVec3f(0f, 0f, 0f)

        writer.beginVertex()
        writer.putVec3f(0f, 1f, 0f)

        writer.beginVertex()
        writer.putVec3f(1f, 1f, 0f)

        writer.beginVertex()
        writer.putVec3f(1f, 0f, 0f)
    }

    final override fun instanceCount(pose: PoseStack.Pose, alphaMul: Float): Int = quads.size

    final override fun appendInstances(slot: Int, writer: VertexWriter, pose: PoseStack.Pose, alphaMul: Float) {
        if (slot != 1) return
        val matrix = pose.pose()
        val start = Vector3f()
        val end = Vector3f()
        for ((x, y, width, height, u0, v0, u1, v1, red, green, blue, alpha, fadeLeft, fadeRight) in quads) {
            matrix.transformPosition(x, y, 0f, start)
            matrix.transformPosition(x + width, y + height, 0f, end)

            writer.beginVertex()
            writer.putVec3f(start.x, start.y, start.z)
            writer.putVec2f(end.x - start.x, end.y - start.y)
            writer.putVec2f(u0, v0)
            writer.putVec2f(u1, v1)
            writer.putVec4f(red, green, blue, alpha * alphaMul)
            writer.putVec2f(fadeLeft, fadeRight)
        }
    }
}
