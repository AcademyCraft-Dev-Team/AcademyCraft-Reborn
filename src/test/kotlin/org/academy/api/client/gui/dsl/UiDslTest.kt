package org.academy.api.client.gui.dsl

import net.minecraft.resources.Identifier
import org.academy.api.client.gui.layout.Gravity
import org.academy.api.client.gui.layout.MeasureSpec
import org.academy.api.client.gui.layout.Orientation
import org.academy.api.client.gui.layout.SizeMode
import org.academy.api.client.gui.widget.*
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class UiDslTest {

    @Test
    fun `dsl builds a nested tree`() {
        val root = FrameLayoutWidget()
        var clicks = 0

        root.column("content", spacing = 1f) {
            lp { matchParent() }
            text("Title") {
                textSize = 12f
                weight(1f)
                height = 0f
                gravity(Gravity.CENTER_LEFT)
            }
            row("controls", spacing = 4f) {
                button("go") {
                    onClick { clicks++ }
                    add("text", TextWidget("Go"))
                }
                toggle(true) { onCheckedChange { } }
            }
        }

        val content = root.children["content"] as LinearLayoutWidget
        assertEquals(2, content.children.size)
        val title = content.children["text"] as TextWidget
        assertEquals("Title", title.text)
        assertEquals(12f, title.textSize)

        val controls = content.children["controls"] as LinearLayoutWidget
        val button = controls.children["go"] as ButtonWidget
        button.onClickListener?.onClick(button)
        assertEquals(1, clicks)
        val toggle = controls.children["toggle"] as ToggleButtonWidget
        assertTrue(toggle.isChecked)
    }

    @Test
    fun `scroll panel with weight fills remaining height and can scroll`() {
        val root = FrameLayoutWidget()
        root.column("col", spacing = 0f) {
            lp { matchParent() }
            fill(0x00FF00.toInt(), "title") {
                height(10f)
                widthMode(SizeMode.MATCH_PARENT)
            }
            val content = standaloneColumn {
                repeat(10) { index ->
                    fill(0xFF0000.toInt(), "row_$index") {
                        height(12f)
                        widthMode(SizeMode.MATCH_PARENT)
                    }
                }
            }
            scrollPanel(Orientation.VERTICAL, "list", content) {
                widthMode(SizeMode.MATCH_PARENT)
                weight(1f)
            }
        }
        root.measure(
            MeasureSpec(MeasureSpec.Mode.EXACTLY, 100f),
            MeasureSpec(MeasureSpec.Mode.EXACTLY, 100f)
        )
        root.layout(0f, 0f, 100f, 100f)

        val column = root.children["col"] as LinearLayoutWidget
        val panel = column.children["list"] as ScrollPanelWidget
        assertEquals(90f, panel.height, 0.01f, "weight(1f) must shrink the panel to the remaining height")
        assertTrue(panel.maxScroll > 0f, "panel must expose a positive scroll range for 120 tall content")
    }

    @Test
    fun `room style nested scroll panel keeps a positive scroll range`() {
        // 复刻音乐室结构: row(body, weight) > column(weight, MATCH_PARENT) > [上部固定块, scrollPanel(weight)]
        val root = FrameLayoutWidget()
        root.row("body", spacing = 0f) {
            lp { matchParent() }
            fill(0x0000FF.toInt(), "members") {
                width(76f)
                heightMode(SizeMode.MATCH_PARENT)
            }
            column("right") {
                weight(1f)
                width(0f)
                heightMode(SizeMode.MATCH_PARENT)
                fill(0x00FF00.toInt(), "now_playing") {
                    height(40f)
                    widthMode(SizeMode.MATCH_PARENT)
                }
                fill(0x00FFFF.toInt(), "search_area") {
                    height(14f)
                    widthMode(SizeMode.MATCH_PARENT)
                }
                fill(0xFFFF00.toInt(), "queue_title") {
                    height(10f)
                    widthMode(SizeMode.MATCH_PARENT)
                }
                val content = standaloneColumn {
                    repeat(8) { index ->
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
        assertTrue(panel.height in 1f..56f, "queue panel height should be the leftover space, was ${panel.height}")
        assertTrue(panel.maxScroll > 0f, "queue panel must be scrollable, maxScroll=${panel.maxScroll}")
    }

    @Test
    fun `weighted spacers center room transport controls`() {
        // 复刻音乐室控制行: [spacer(weight), prev, play, next, spacer(weight)]，
        // 验证三个按钮整体水平居中（不再靠左）喵。
        val root = FrameLayoutWidget()
        root.row("controls", spacing = 0f) {
            lp { matchParent() }
            empty("lead") { weight(1f) }
            fill(0xFF0000.toInt(), "prev") { size(14f, 14f) }
            fill(0xFF0000.toInt(), "play") { size(14f, 14f) }
            fill(0xFF0000.toInt(), "next") { size(14f, 14f) }
            empty("tail") { weight(1f) }
        }
        root.measure(
            MeasureSpec(MeasureSpec.Mode.EXACTLY, 200f),
            MeasureSpec(MeasureSpec.Mode.EXACTLY, 20f)
        )
        root.layout(0f, 0f, 200f, 20f)

        val controls = root.children["controls"] as LinearLayoutWidget
        val prev = controls.children["prev"] as FillWidget
        val next = controls.children["next"] as FillWidget
        val leftGap = prev.x
        val rightGap = 200f - (next.x + next.width)
        // 自由空间 = 200 - 3*14 = 158，两个 spacer 各占一半 79
        assertEquals(79f, leftGap, 0.01f, "left spacer should take the free half, was $leftGap")
        assertEquals(leftGap, rightGap, 0.01f, "both sides must have equal gaps for centering")
        val groupCenter = (prev.x + next.x + next.width) / 2f
        assertEquals(100f, groupCenter, 0.01f, "control group must sit on the horizontal center")
    }

    @Test
    fun `dsl default names are unique`() {
        val root = FrameLayoutWidget()
        root.text("a")
        root.text("b")
        assertEquals(listOf("text", "text_1"), root.children.keys.toList())
    }

    @Test
    fun `dsl layout params apply correctly`() {
        val root = FrameLayoutWidget()
        root.column {
            fill(0xFF0000.toInt()) {
                size(40f, 10f)
            }
        }
        val column = root.children["column"] as LinearLayoutWidget
        val fill = column.children["fill"] as FillWidget
        root.measure(
            MeasureSpec(MeasureSpec.Mode.EXACTLY, 100f),
            MeasureSpec(MeasureSpec.Mode.EXACTLY, 100f)
        )
        root.layout(0f, 0f, 100f, 100f)
        assertEquals(40f, fill.width)
        assertEquals(10f, fill.height)
    }

    @Test
    fun `standaloneColumn inside WidgetContainer lambda does not leak into parent`() {
        val textArea = LinearLayoutWidget()
        textArea.orientation = org.academy.api.client.gui.layout.Orientation.VERTICAL
        textArea.lp { gravity(Gravity.CENTER) }

        textArea.column("probe") {
            val details = standaloneColumn(spacing = 2f) {
                fill(0xFF0000.toInt(), "desc") { height = 18f; matchWidth() }
            }
            assertEquals(null, details.parent, "standalone column must be unattached")
            scrollPanel(name = "details", content = details) {
                gravity(Gravity.CENTER)
                size(240f, 112f)
            }
        }

        val probe = textArea.children["probe"] as LinearLayoutWidget
        assertEquals(listOf("details"), probe.children.keys.toList(), "no phantom child should leak into parent")
        val scroll = probe.children["details"] as ScrollPanelWidget
        assertEquals("content", scroll.children.keys.single(), "scrollPanel hosts exactly one content")

        textArea.measure(
            MeasureSpec(MeasureSpec.Mode.EXACTLY, 400f),
            MeasureSpec(MeasureSpec.Mode.EXACTLY, 300f)
        )
        textArea.layout(0f, 0f, 400f, 300f)
    }

    @Test
    fun `sprite sheet builder attaches a configured frame widget`() {
        val root = FrameLayoutWidget()
        val sheet = root.spriteSheet(
            Identifier.parse("academy:test_sheet"),
            Orientation.VERTICAL,
            20, 40,
            20, 20,
            2,
            "effect"
        ) {
            size(10f, 10f)
        }

        assertEquals("effect", sheet.name)
        assertTrue(root.children["effect"] === sheet)
        assertEquals(0, sheet.frameIndex)
        assertEquals(Orientation.VERTICAL, sheet.getSpriteSheetOrientation())
        assertEquals(2, sheet.getSpriteSheetFrameCount())

        root.measure(
            MeasureSpec(MeasureSpec.Mode.EXACTLY, 50f),
            MeasureSpec(MeasureSpec.Mode.EXACTLY, 50f)
        )
        root.layout(0f, 0f, 50f, 50f)
        assertEquals(10f, sheet.width)
        assertEquals(10f, sheet.height)

        sheet.nextFrame()
        assertEquals(1, sheet.frameIndex)
        sheet.nextFrame()
        assertEquals(0, sheet.frameIndex)
    }
}
