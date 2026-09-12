package org.academy.api.client.gui.command

import com.mojang.blaze3d.pipeline.RenderPipeline
import com.mojang.blaze3d.systems.RenderSystem
import com.mojang.blaze3d.textures.FilterMode
import com.mojang.blaze3d.textures.GpuTextureView
import com.mojang.blaze3d.vertex.PoseStack
import org.academy.api.client.gui.render.VertexWriter
import org.academy.api.client.render.TextureBinding
import org.academy.api.client.render.UniformPayload
import org.joml.Vector3f
import kotlin.math.max
import kotlin.math.min

/**
 * 一段文本的实例化四边形命令：共享同一管线、图集页与 uniform，内部持有 1..N 个
 * [GlyphQuad]（对标 Skia 的 `AtlasSubRun`）。一整段文本因此只提交一条命令，由
 * [BatchProcessor] 通过 [instanceCount]/[appendInstances] 展开为多个 instance。
 */
abstract class TextDrawCommand protected constructor(
    pipeline: RenderPipeline,
    textureView: GpuTextureView,
    private val quads: List<GlyphQuad>,
    uniforms: List<UniformPayload<*>>
) : DrawCommand(
    pipeline,
    listOf(
        TextureBinding(
            "Sampler0",
            textureView,
            RenderSystem.getSamplerCache().getClampToEdge(FilterMode.LINEAR)
        )
    ),
    uniforms
) {
    final override fun isGeometryFixed(): Boolean = true

    override fun allowsOverlapMerge(): Boolean = true

    /** 文本多实例可安全重叠合并 (对标 AOSP Text batch 的 multiDraw overdraw). */
    override fun localBounds(): LocalBounds {
        if (quads.isEmpty()) return LocalBounds(0f, 0f, 0f, 0f)
        var left = Float.MAX_VALUE
        var top = Float.MAX_VALUE
        var right = -Float.MAX_VALUE
        var bottom = -Float.MAX_VALUE
        for (quad in quads) {
            left = min(left, quad.x)
            top = min(top, quad.y)
            right = max(right, quad.x + quad.width)
            bottom = max(bottom, quad.y + quad.height)
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
        for (quad in quads) {
            matrix.transformPosition(quad.x, quad.y, 0f, start)
            matrix.transformPosition(quad.x + quad.width, quad.y + quad.height, 0f, end)

            writer.beginVertex()
            writer.putVec3f(start.x, start.y, start.z)
            writer.putVec2f(end.x - start.x, end.y - start.y)
            writer.putVec2f(quad.u0, quad.v0)
            writer.putVec2f(quad.u1, quad.v1)
            writer.putVec4f(quad.red, quad.green, quad.blue, quad.alpha * alphaMul)
            writer.putVec2f(quad.fadeLeft, quad.fadeRight)
        }
    }
}
