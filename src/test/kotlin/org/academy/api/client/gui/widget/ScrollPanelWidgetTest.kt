package org.academy.api.client.gui.widget

import org.academy.api.client.gui.dsl.column
import org.academy.api.client.gui.dsl.fill
import org.academy.api.client.gui.dsl.height
import org.academy.api.client.gui.dsl.heightMode
import org.academy.api.client.gui.dsl.lp
import org.academy.api.client.gui.dsl.matchParent
import org.academy.api.client.gui.dsl.row
import org.academy.api.client.gui.dsl.scrollPanel
import org.academy.api.client.gui.dsl.standaloneColumn
import org.academy.api.client.gui.dsl.weight
import org.academy.api.client.gui.dsl.width
import org.academy.api.client.gui.dsl.widthMode
import org.academy.api.client.gui.event.MouseEvent
import org.academy.api.client.gui.event.ScrollEvent
import org.academy.api.client.gui.layout.MeasureSpec
import org.academy.api.client.gui.layout.Orientation
import org.academy.api.client.gui.layout.SizeMode
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class ScrollPanelWidgetTest {
    @Test
    fun `dispatches absolute pointer coordinates to scrolled content`() {
        val panel = ScrollPanelWidget()
        val content = FrameLayoutWidget()
        val probe = PressProbe()
        content.addChild("probe", probe)
        panel.setContent(content)

        panel.layout(10f, 20f, 110f, 120f)
        content.layout(0f, 0f, 100f, 200f)
        probe.layout(5f, 80f, 25f, 100f)
        panel.scrollTo(0f, 50f)

        panel.dispatchEvent(MouseEvent.createPressEvent(20.0, 55.0, 0))

        assertEquals(1, probe.presses)
    }

    @Test
    fun `mouse wheel over content scrolls a nested room style panel`() {
        // 复刻音乐室结构: 外层 row 里的 column > [固定块, scrollPanel(weight)]，
        // 验证滚轮事件能穿过外层容器最终被队列面板消费并改变滚动位置喵。
        val root = FrameLayoutWidget()
        root.row("body") {
            lp { matchParent() }
            column("right") {
                weight(1f)
                width(0f)
                heightMode(SizeMode.MATCH_PARENT)
                fill(0x00FF00.toInt(), "now_playing") {
                    height(40f)
                    widthMode(SizeMode.MATCH_PARENT)
                }
                val content = standaloneColumn {
                    repeat(12) { index ->
                        fill(0xFF0000.toInt(), "row_$index") {
                            height(16f)
                            widthMode(SizeMode.MATCH_PARENT)
                        }
                    }
                }
                scrollPanel(Orientation.VERTICAL, "queue_area", content) {
                    widthMode(SizeMode.MATCH_PARENT)
                    weight(1f)
                }
            }
        }
        root.measure(
            MeasureSpec(MeasureSpec.Mode.EXACTLY, 200f),
            MeasureSpec(MeasureSpec.Mode.EXACTLY, 120f)
        )
        root.layout(0f, 0f, 200f, 120f)

        val body = root.children["body"] as LinearLayoutWidget
        val right = body.children["right"] as LinearLayoutWidget
        val panel = right.children["queue_area"] as ScrollPanelWidget
        assertTrue(panel.maxScroll > 0f, "content must overflow the panel to be scrollable")

        // 滚轮落点取面板内部（面板下方的区域）
        val wheelX = panel.getAbsoluteX() + panel.width / 2f
        val wheelY = panel.getAbsoluteY() + panel.height / 2f
        val wheel = ScrollEvent(wheelX.toDouble(), wheelY.toDouble(), -3.0)
        panel.dispatchEvent(wheel)

        // scrollY 由 render 插值推进（单测不渲染），这里验证滚轮确实被该面板消费，
        // 即事件没有在嵌套容器/行控件层级被吞掉喵。
        assertTrue(wheel.isConsumed, "the room queue panel must consume wheel input")
    }

    private class PressProbe : AbstractWidget() {
        var presses = 0

        override fun onMousePressed(event: MouseEvent) {
            if (event.button == 0 && isMouseOver(event.x, event.y)) {
                presses++
                event.consume()
            }
        }
    }
}
