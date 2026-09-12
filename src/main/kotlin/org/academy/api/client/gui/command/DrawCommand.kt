package org.academy.api.client.gui.command

import com.mojang.blaze3d.pipeline.RenderPipeline
import com.mojang.blaze3d.vertex.PoseStack
import org.academy.api.client.gui.render.VertexWriter
import org.academy.api.client.render.TextureBinding
import org.academy.api.client.render.UniformPayload

/**
 * 命令在本地坐标下的轴对齐包围盒, 对标 AOSP `RecordedOp.unmappedBounds` 喵.
 *
 * 必须**保守精确**: 需覆盖实际绘制范围 (含 SDF 外扩/阴影/描边/AA), 宁可偏大不可偏小,
 * 否则批次重排会漏裁. 返回 null 表示无界, 该命令不可与其他命令合并 (对标
 * `BakedOpState::tryConstructUnbounded`, clipSideFlags=Full).
 */
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

    /** 本地坐标保守包围盒; null 表示无界 (不参与合并). */
    open fun localBounds(): LocalBounds? = null

    /**
     * 是否允许在包围盒重叠时仍与其他命令合并 (对标 AOSP Text batch 的 multiDraw overdraw 特例).
     * 仅对自身顶点顺序可安全重叠的命令 (如文本实例) 返回 true.
     */
    open fun allowsOverlapMerge(): Boolean = false

    open fun isGeometryFixed(): Boolean = false

    open fun generateInstanceData(slot: Int, writer: VertexWriter, instanceIndex: Int, pose: PoseStack.Pose, alphaMul: Float) {
    }

    /**
     * Number of instances this command contributes when [isGeometryFixed] is true.
     *
     * Commands that represent a whole run (e.g. a multi-glyph text run) override this
     * together with [appendInstances] so one [DrawCommand] can emit many instances,
     * mirroring Skia's `AtlasSubRun`.
     */
    open fun instanceCount(pose: PoseStack.Pose, alphaMul: Float): Int = 1

    /**
     * Appends [instanceCount] instances for vertex [slot]. Default emits exactly one,
     * preserving the one-command-one-instance contract for existing commands.
     */
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
