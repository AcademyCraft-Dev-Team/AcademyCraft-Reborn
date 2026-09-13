package org.academy.internal.client.gui.layout

import org.academy.api.client.gui.layout.MeasureSpec
import org.academy.api.client.gui.widget.BlendQuadWidget
import org.academy.api.client.gui.widget.FillWidget
import org.academy.api.client.gui.widget.Widget
import org.academy.api.client.gui.widget.WidgetContainer
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class UiLayoutsTest {

    @Test
    fun `location layout keeps Academy projection and control geometry`() {
        val layout = buildLocationTeleportLayout()
        place(layout.root, 854f, 480f)

        assertEquals(420f, layout.panel.width)
        assertEquals(236f, layout.panel.height)
        assertEquals(217f, layout.panel.getAbsoluteX())
        assertEquals(122f, layout.panel.getAbsoluteY())

        val background = find(layout.root, "panel_background")
        assertTrue(background is BlendQuadWidget)
        background as BlendQuadWidget
        assertEquals(0.12f, background.alpha)
        assertFalse(background.drawLine)

        assertEquals(229f, layout.nameInput.getAbsoluteX())
        assertEquals(154f, layout.nameInput.getAbsoluteY())
        assertEquals(396f, layout.nameInput.width)
        assertEquals(20f, layout.nameInput.height)

        assertEquals(229f, layout.marks.getAbsoluteX())
        assertEquals(230f, layout.marks.getAbsoluteY())
        assertEquals(396f, layout.marks.width)
        assertEquals(94f, layout.marks.height)

        assertEquals(0xFF7680DE.toInt(), fill(layout.root, "border_top").color)
        assertEquals(0xFF7680DE.toInt(), fill(layout.root, "border_bottom").color)
        assertEquals(0xFF7680DE.toInt(), fill(layout.root, "border_left").color)
        assertEquals(0xFF7680DE.toInt(), fill(layout.root, "border_right").color)
        assertEquals(0xFF7680DE.toInt(), fill(layout.root, "title_divider").color)
    }

    @Test
    fun `reflection filter variants keep interaction columns`() {
        val compact = buildReflectionFilterLayout(true)
        place(compact.root, 854f, 480f)
        assertEquals(460f, compact.panel.width)
        assertEquals(238f, compact.panel.height)
        assertEquals(140f, compact.leftColumn.width)
        assertEquals(74f, compact.middleColumn.width)
        assertEquals(202f, compact.rightColumn.width)

        val wide = buildReflectionFilterLayout(false)
        place(wide.root, 1280f, 720f)
        assertEquals(520f, wide.panel.width)
        assertEquals(260f, wide.panel.height)
        assertEquals(160f, wide.leftColumn.width)
        assertEquals(74f, wide.middleColumn.width)
        assertEquals(242f, wide.rightColumn.width)

        val background = find(compact.root, "panel_background")
        assertTrue(background is BlendQuadWidget)
        background as BlendQuadWidget
        assertEquals(0.5f, background.alpha)
        assertTrue(background.drawLine)
        assertEquals(0x60FFFFFF, fill(compact.root, "border_left").color)
        assertEquals(0x60FFFFFF, fill(compact.root, "border_right").color)
        assertEquals(0x60FFFFFF, fill(compact.root, "title_divider").color)
    }

    @Test
    fun `program editor variants keep responsive rails`() {
        val compact = buildProgramEditorLayout(ProgramEditorVariant.COMPACT)
        place(compact.root, 480f, 360f)
        assertEquals(18f, compact.palette.width)
        assertEquals(18f, compact.inspector.width)
        assertTrue(compact.canvas.width >= 40f)

        val wide = buildProgramEditorLayout(ProgramEditorVariant.WIDE)
        place(wide.root, 1000f, 640f)
        assertEquals(112f, wide.palette.width)
        assertEquals(128f, wide.inspector.width)
        assertTrue(wide.canvas.width >= 40f)

        assertEquals(0xD9FFFFFF.toInt(), fill(wide.root, "border_top").color)
        assertEquals(0xD9FFFFFF.toInt(), fill(wide.root, "border_bottom").color)
        assertEquals(0x55FFFFFF, fill(wide.root, "border_left").color)
        assertEquals(0x55FFFFFF, fill(wide.root, "border_right").color)
        assertEquals(0x80FFFFFF.toInt(), fill(wide.root, "title_divider").color)
        assertEquals(0xFFFF6C00.toInt(), fill(wide.root, "title_accent").color)
    }

    private fun fill(root: Widget, name: String): FillWidget {
        val widget = find(root, name)
        assertTrue(widget is FillWidget, "expected '$name' to be a FillWidget")
        return widget as FillWidget
    }

    private fun place(root: Widget, width: Float, height: Float) {
        root.measure(
            MeasureSpec(MeasureSpec.Mode.EXACTLY, width),
            MeasureSpec(MeasureSpec.Mode.EXACTLY, height)
        )
        root.layout(0f, 0f, width, height)
    }

    private fun find(root: Widget, name: String): Widget? {
        if (root.name == name) return root
        if (root !is WidgetContainer) return null
        for (child in root.children.values) {
            find(child, name)?.let { return it }
        }
        return null
    }
}
