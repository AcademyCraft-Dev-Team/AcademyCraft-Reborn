package org.academy.api.client.gui.dsl

import org.academy.api.client.gui.layout.Gravity
import org.academy.api.client.gui.layout.MeasureSpec
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
            label("Title") {
                baseFontSize = 12f
                weight(1f)
                height = 0f
                gravity(Gravity.CENTER_LEFT)
            }
            row("controls", spacing = 4f) {
                button("go") {
                    onClick { clicks++ }
                    add("text", LabelWidget("Go"))
                }
                toggle(true) { onCheckedChange { } }
            }
        }

        val content = root.children["content"] as LinearLayoutWidget
        assertEquals(2, content.children.size)
        val title = content.children["label"] as LabelWidget
        assertEquals("Title", title.text)
        assertEquals(12f, title.baseFontSize)

        val controls = content.children["controls"] as LinearLayoutWidget
        val button = controls.children["go"] as ButtonWidget
        button.onClickListener?.onClick(button)
        assertEquals(1, clicks)
        val toggle = controls.children["toggle"] as ToggleButtonWidget
        assertTrue(toggle.isChecked)
    }

    @Test
    fun `dsl default names are unique`() {
        val root = FrameLayoutWidget()
        root.label("a")
        root.label("b")
        assertEquals(listOf("label", "label_1"), root.children.keys.toList())
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
}
