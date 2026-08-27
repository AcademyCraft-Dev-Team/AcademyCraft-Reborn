package org.academy.api.client.gui.widget

import org.academy.api.client.gui.command.FillRectDrawCommand
import org.academy.api.client.gui.layout.MeasureSpec
import org.academy.api.client.gui.render.RenderContext
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * 复现 coverAllPrev + advance(recordedMax+1) 导致的 drawOrder 指数级增长与 Int 溢出.
 * 修复前: 技能树约 15 个节点使 drawOrder 涨到 13 亿, wireless 内容回绕成负数,
 *         排序时负数内容排在正数 SDF 之后, back(黑色SDF) 盖到内容之上.
 */
class CoverAllPrevOverflowTest {

    // 提交一个 POS_COLOR 命令 (无需 GPU, 可在单测跑)
    private class Fill : AbstractWidget() {
        override fun render(context: RenderContext) {
            context.submit(FillRectDrawCommand(10f, 10f, 1f, 1f, 1f, 1f))
        }
    }

    // 模拟一个 SkillNode: 多层嵌套, 每层多个 coverAllPrev 子容器, 使 drawOrder 每节点近似翻倍.
    // levels=3 children=1 nodes=15 时 total 约 1.36e10: 远超 Int 上限(证明原 Int 溢出 bug),
    // 但低于 2^40 封顶(层级顺序不受影响).
    private fun skillNodeLike(): FrameLayoutWidget {
        val node = FrameLayoutWidget()
        var current = node
        repeat(3) {
            val level = FrameLayoutWidget()
            repeat(1) { idx ->
                val leaf = FrameLayoutWidget()
                leaf.addChild("f$idx", Fill())
                level.addChild("child$idx", leaf)
            }
            current.addChild("level", level)
            current = level
        }
        return node
    }

    private fun FrameLayoutWidget.measureAndLayout(w: Float, h: Float) {
        measure(
            MeasureSpec(MeasureSpec.Mode.EXACTLY, w),
            MeasureSpec(MeasureSpec.Mode.EXACTLY, h)
        )
        layout(0f, 0f, w, h)
    }

    @Test
    fun `deep coverAllPrev tree must not overflow drawOrder to negative`() {
        val root = FrameLayoutWidget()
        for (i in 0 until 15) {
            root.addChild("node_$i", skillNodeLike())
        }
        root.measureAndLayout(300f, 300f)

        val ctx = RenderContext()
        root.render(ctx)

        val maxOrder = ctx.commands.maxOf { it.drawOrder }
        println("max drawOrder after 15 nodes = $maxOrder")
        // 修复前: drawOrder 指数增长, 溢出 Int 回绕成负数
        assertTrue(ctx.commands.all { it.drawOrder >= 0 }, "存在负 drawOrder(溢出)")
        // 修复后: 应低于 2^40 封顶, 未触发饱和(层级顺序完整)
        assertTrue(
            maxOrder < (1L shl 40),
            "drawOrder 触发 2^40 封顶(饱和), 层级顺序将损坏: max=$maxOrder"
        )
    }

    @Test
    fun `wireless back must stay below wireless content - no overflow`() {
        // root: [mainWidget(技能树), cover(wireless: back + content)]
        val root = FrameLayoutWidget()
        val main = FrameLayoutWidget()
        for (i in 0 until 15) {
            main.addChild("node_$i", skillNodeLike())
        }
        root.addChild("main", main)

        val cover = FrameLayoutWidget()
        val wireless = FrameLayoutWidget()
        wireless.addChild("back", object : AbstractWidget() {
            override fun render(context: RenderContext) {
                context.drawOrder().push()
                run {
                    context.submit(FillRectDrawCommand(50f, 50f, 0f, 0f, 0f, 0.5f)) // SDF
                    context.drawOrder().advance()
                    context.submit(FillRectDrawCommand(50f, 4f, 1f, 1f, 1f, 1f)) // line
                }
                context.drawOrder().pop()
            }
        })
        wireless.addChild("content", object : AbstractWidget() {
            override fun render(context: RenderContext) {
                context.drawOrder().push()
                run {
                    context.drawOrder().advance()
                    context.submit(FillRectDrawCommand(20f, 20f, 1f, 1f, 1f, 1f))
                }
                context.drawOrder().pop()
            }
        })
        cover.addChild("wireless", wireless)
        root.addChild("cover", cover)

        root.measureAndLayout(300f, 300f)

        val ctx = RenderContext()
        root.render(ctx)

        val orders = ctx.commands.map { it.drawOrder }
        println("drawOrders=$orders")

        // 无线面板的 3 条命令在末尾: [back_SDF, back_line, content]
        assertTrue(orders.size >= 3, "缺少无线面板命令: $orders")
        val sdfOrder = orders[orders.size - 3]
        val contentOrder = orders.last()

        // 修复前: drawOrder 溢出成负数, 排序时负数 content 排在正数 SDF 之后
        assertTrue(orders.all { it >= 0 }, "存在负 drawOrder(溢出): $orders")
        assertTrue(
            sdfOrder < contentOrder,
            "back(SDF) order=$sdfOrder 必须 < content=$contentOrder: $orders"
        )
    }
}
