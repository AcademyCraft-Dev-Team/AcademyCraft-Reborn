package org.academy.api.client.gui.widget

import com.mojang.blaze3d.buffers.GpuBufferSlice
import com.mojang.blaze3d.vertex.PoseStack
import net.minecraft.client.renderer.DynamicUniformStorage.DynamicUniform
import org.academy.api.client.gui.command.FillRectDrawCommand
import org.academy.api.client.gui.command.PosColorRectDrawCommand
import org.academy.api.client.gui.command.SubmittedCommand
import org.academy.api.client.gui.layout.MeasureSpec
import org.academy.api.client.gui.render.BatchProcessor
import org.academy.api.client.gui.render.Canvas
import org.academy.api.client.gui.render.ScissorRect
import org.academy.api.client.render.Render
import org.academy.api.client.render.UniformPayload
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * 验证 AOSP 式画家顺序: 记录顺序即绘制顺序, 合并 (BatchProcessor) 不得破坏重叠内容的先后.
 */
class PainterOrderTest {
    private val tagOrder = mutableListOf<String>()

    private inner class TaggedFill(private val tag: String) : AbstractWidget() {
        override fun render(context: Canvas) {
            tagOrder.add(tag)
            context.submit(FillRectDrawCommand(10f, 10f, 1f, 1f, 1f, 1f))
        }
    }

    private fun FrameLayoutWidget.measureAndLayout(w: Float, h: Float) {
        measure(MeasureSpec(MeasureSpec.Mode.EXACTLY, w), MeasureSpec(MeasureSpec.Mode.EXACTLY, h))
        layout(0f, 0f, w, h)
    }

    @Test
    fun `later sibling subtree draws above earlier sibling subtree`() {
        tagOrder.clear()
        val root = FrameLayoutWidget()
        val a = FrameLayoutWidget()
        val x = FrameLayoutWidget()
        val y = FrameLayoutWidget()
        y.addChild("z", TaggedFill("Z"))
        x.addChild("y", y)
        a.addChild("x", x)
        root.addChild("a", a)
        val b = FrameLayoutWidget()
        b.addChild("c", TaggedFill("C"))
        root.addChild("b", b)
        root.measureAndLayout(300f, 300f)

        val ctx = Canvas()
        root.render(ctx)

        assertEquals(listOf("Z", "C"), tagOrder, "B 的子树必须在 A 的整棵子树之后绘制")
        val indices = ctx.commands.map { it.commandIndex }
        assertTrue(indices == indices.sorted(), "commandIndex 必须单调: $indices")
    }

    // ---- BatchProcessor 合并语义 ----

    private class TestRect(width: Float, height: Float, private val overlap: Boolean) :
        PosColorRectDrawCommand(
            Render.RenderPipelines.POS_COLOR, width, height,
            1f, 1f, 1f, 1f, emptyList(), emptyList()
        ) {
        override fun allowsOverlapMerge(): Boolean = overlap
    }

    private object NoopUploader : BatchProcessor.UboUploader {
        override fun <T : DynamicUniform> upload(payload: UniformPayload<T>): GpuBufferSlice =
            error("no uniforms expected")
    }

    private fun poseAt(x: Float, y: Float): PoseStack.Pose {
        val stack = PoseStack()
        stack.last().translate(x, y, 0f)
        return stack.last()
    }

    private fun op(width: Float, x: Float, overlap: Boolean = false): SubmittedCommand =
        SubmittedCommand(TestRect(width, width, overlap), poseAt(x, 0f), null, 0)

    @Test
    fun `non-overlapping same pipeline merge into one batch`() {
        val commands = mutableListOf(op(10f, 0f), op(10f, 100f))
        val batches = BatchProcessor.process(commands, NoopUploader)
        assertEquals(1, batches.size)
    }

    @Test
    fun `overlapping non-text ops become separate ordered batches`() {
        val commands = mutableListOf(op(10f, 0f), op(10f, 0f))
        val batches = BatchProcessor.process(commands, NoopUploader)
        assertEquals(2, batches.size)
    }

    @Test
    fun `overlap-allowed ops merge even when overlapping`() {
        val commands = mutableListOf(op(10f, 0f, overlap = true), op(10f, 0f, overlap = true))
        val batches = BatchProcessor.process(commands, NoopUploader)
        assertEquals(1, batches.size)
    }

    private fun scissoredOp(width: Float, x: Float, y: Float, scissor: ScissorRect): SubmittedCommand =
        SubmittedCommand(TestRect(width, width, false), poseAt(x, y), scissor, 0)

    @Test
    fun `clipped ops merge when clip sides are compatible`() {
        val clip = ScissorRect(0f, 0f, 100f, 50f)
        val commands = mutableListOf(
            scissoredOp(20f, 0f, -10f, clip),
            scissoredOp(20f, 50f, -10f, clip)
        )
        assertEquals(1, BatchProcessor.process(commands, NoopUploader).size)
    }

    @Test
    fun `op extending beyond another clip is not merged`() {
        val commands = mutableListOf(
            scissoredOp(20f, 0f, 0f, ScissorRect(0f, 0f, 10f, 100f)),
            scissoredOp(10f, 15f, 0f, ScissorRect(0f, 0f, 100f, 100f))
        )
        assertEquals(2, BatchProcessor.process(commands, NoopUploader).size)
    }
}
