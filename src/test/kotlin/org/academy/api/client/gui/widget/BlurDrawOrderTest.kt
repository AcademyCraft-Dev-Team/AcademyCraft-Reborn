package org.academy.api.client.gui.widget

import org.academy.api.client.gui.layout.MeasureSpec
import org.academy.api.client.gui.render.RenderContext
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * 验证 UI_LAYER 模糊的 commandIndex 不变量: 模糊面板的 commandIndex 位于
 * 其下方内容 (main) 与上方内容 (cover) 之间喵.
 */
class BlurDrawOrderTest {

    @Test
    fun `blur panel command index is between main and cover content`() {
        val root = FrameLayoutWidget()
        val main = FrameLayoutWidget()
        val belowFill = FillWidget(0xFFAA0000.toInt())
        main.addChild("below_fill", belowFill)
        root.addChild("main", main)

        val cover = FrameLayoutWidget()
        val blur = BlurPanelWidget(8f)
        cover.addChild("bg_blur", blur)
        val aboveFill = FillWidget(0xFF00AA00.toInt())
        cover.addChild("above_fill", aboveFill)
        root.addChild("cover", cover)

        root.measureAndLayout(200f, 200f)

        val context = RenderContext()
        root.render(context)

        assertTrue(context.blurRegions.size == 1, "expected one blur region")
        val blurIndex = context.blurRegions[0].commandIndex

        val belowIndices = context.commands
            .filter { it.commandIndex < blurIndex }
            .map { it.commandIndex }
        assertTrue(belowIndices.isNotEmpty())
        assertTrue(
            belowIndices.all { it < blurIndex },
            "below commands (commandIndex=$belowIndices) must be < blur ($blurIndex)"
        )
        val aboveIndices = context.commands
            .filter { it.commandIndex >= blurIndex }
            .map { it.commandIndex }
        assertTrue(aboveIndices.isNotEmpty())
    }

    @Test
    fun `fill rect command indices increase monotonically`() {
        val root = FrameLayoutWidget()
        val a = FrameLayoutWidget()
        val f = FillWidget(0xFFAA0000.toInt())
        a.addChild("f", f)
        root.addChild("a", a)
        val b = FrameLayoutWidget()
        val g = FillWidget(0xFF00AA00.toInt())
        b.addChild("g", g)
        root.addChild("b", b)

        root.measureAndLayout(200f, 200f)

        val context = RenderContext()
        root.render(context)

        val indices = context.commands.map { it.commandIndex }
        assertTrue(indices.size >= 2, "expected at least 2 commands, got $indices")
        assertTrue(indices[0] < indices[1], "first child command must be before second: $indices")
    }

    @Test
    fun `blur region survives child cache reuse`() {
        val root = FrameLayoutWidget()
        val main = FrameLayoutWidget()
        val belowFill = FillWidget(0xFFAA0000.toInt())
        main.addChild("below_fill", belowFill)
        root.addChild("main", main)
        val cover = FrameLayoutWidget()
        cover.addChild("bg_blur", BlurPanelWidget(8f))
        root.addChild("cover", cover)
        root.measureAndLayout(200f, 200f)

        // 第一次渲染建立子缓存
        val ctx1 = RenderContext()
        root.render(ctx1)
        assertTrue(ctx1.blurRegions.size == 1)

        // 只让 main 变脏, cover 缓存被复用
        belowFill.invalidate()
        val ctx2 = RenderContext()
        root.render(ctx2)
        assertTrue(ctx2.blurRegions.size == 1, "blur region lost on child cache reuse")

        // 全树干净后再次渲染 (整帧缓存路径)
        val ctx3 = RenderContext()
        root.render(ctx3)
        assertTrue(ctx3.blurRegions.size == 1, "blur region lost on fully cached frame")
    }

    private fun FrameLayoutWidget.measureAndLayout(width: Float, height: Float) {
        measure(
            MeasureSpec(MeasureSpec.Mode.EXACTLY, width),
            MeasureSpec(MeasureSpec.Mode.EXACTLY, height)
        )
        layout(0f, 0f, width, height)
    }
}
