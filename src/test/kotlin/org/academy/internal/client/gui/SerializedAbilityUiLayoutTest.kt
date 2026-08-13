package org.academy.internal.client.gui

import org.academy.api.client.gui.layout.MeasureSpec
import org.academy.api.client.gui.serialize.WidgetSerializer
import org.academy.api.client.gui.widget.BlendQuadWidget
import org.academy.api.client.gui.widget.FillWidget
import org.academy.api.client.gui.widget.FrameLayoutWidget
import org.academy.api.client.gui.widget.Widget
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class SerializedAbilityUiLayoutTest {
    @Test
    fun `ability layouts decode and expose required binding slots`() {
        assertLayout("mental_control_hud", "mental_control", "content")
        assertLayout("ability_cp_hud", "cp")
        assertLayout("ability_skill_wheel_hud", "skill_wheel")
        assertLayout("toggle_status_hud", "toggle_statuses")
        assertLayout(
            "location_teleport",
            "panel", "name_input", "coordinates", "mark_current", "add_mark", "marks", "refresh", "done"
        )
        assertLayout("precision_operation_wide", "panel", "palette", "canvas", "inspector")
        assertLayout("precision_operation_medium", "panel", "palette", "canvas", "inspector")
        assertLayout("precision_operation_compact", "panel", "palette", "canvas", "inspector")
        assertLayout("reflection_filter_wide", "panel", "left_column", "middle_column", "right_column")
        assertLayout("reflection_filter_compact", "panel", "left_column", "middle_column", "right_column")
    }

    @Test
    fun `precision variants preserve responsive canvas rails`() {
        val wide = load("precision_operation_wide")
        layout(wide, 854f, 480f)
        assertEquals(112f, SerializedUiLayout.require(wide, "palette").width)
        assertEquals(128f, SerializedUiLayout.require(wide, "inspector").width)
        assertTrue(SerializedUiLayout.require(wide, "canvas").width >= 580f)

        val compact = load("precision_operation_compact")
        layout(compact, 480f, 270f)
        assertEquals(18f, SerializedUiLayout.require(compact, "palette").width)
        assertEquals(18f, SerializedUiLayout.require(compact, "inspector").width)
        assertTrue(SerializedUiLayout.require(compact, "canvas").width >= 430f)
    }

    @Test
    fun `location panel keeps Academy projection and safe control geometry`() {
        val root = load("location_teleport")
        layout(root, 854f, 480f)
        val panel = SerializedUiLayout.require(root, "panel")
        val projection = SerializedUiLayout.require(root, "panel_background")
        val borderTop = SerializedUiLayout.require(root, "border_top")
        val borderBottom = SerializedUiLayout.require(root, "border_bottom")
        val borderLeft = SerializedUiLayout.require(root, "border_left")
        val borderRight = SerializedUiLayout.require(root, "border_right")
        val titleDivider = SerializedUiLayout.require(root, "title_divider")
        val nameInput = SerializedUiLayout.require(root, "name_input")
        val coordinates = SerializedUiLayout.require(root, "coordinates")
        val markCurrent = SerializedUiLayout.require(root, "mark_current")
        val addMark = SerializedUiLayout.require(root, "add_mark")
        val marks = SerializedUiLayout.require(root, "marks")
        val refresh = SerializedUiLayout.require(root, "refresh")
        val done = SerializedUiLayout.require(root, "done")

        assertEquals(420f, panel.width)
        assertEquals(236f, panel.height)
        assertEquals(217f, panel.x)
        assertEquals(122f, panel.y)
        assertNull(SerializedUiLayout.find(root, "title_accent"))
        assertTrue(projection is BlendQuadWidget)
        assertEquals(0.12f, projection.alpha)
        assertFalse((projection as BlendQuadWidget).drawLine)
        assertEquals(panel.getAbsoluteX() + 1f, projection.getAbsoluteX())
        assertEquals(418f, projection.width)
        assertEquals(panel.getAbsoluteX() + 4f, borderTop.getAbsoluteX())
        assertEquals(panel.getAbsoluteY(), borderTop.getAbsoluteY())
        assertEquals(412f, borderTop.width)
        assertEquals(panel.getAbsoluteX() + 4f, borderBottom.getAbsoluteX())
        assertEquals(panel.getAbsoluteY() + 235f, borderBottom.getAbsoluteY())
        assertEquals(412f, borderBottom.width)
        assertEquals(panel.getAbsoluteY() + 4f, borderLeft.getAbsoluteY())
        assertEquals(228f, borderLeft.height)
        assertEquals(panel.getAbsoluteX() + 419f, borderRight.getAbsoluteX())
        assertEquals(panel.getAbsoluteY() + 4f, borderRight.getAbsoluteY())
        assertEquals(228f, borderRight.height)
        val phaseViolet = 0xFF7680DE.toInt()
        assertEquals(phaseViolet, (borderTop as FillWidget).color)
        assertEquals(phaseViolet, (borderBottom as FillWidget).color)
        assertEquals(phaseViolet, (borderLeft as FillWidget).color)
        assertEquals(phaseViolet, (borderRight as FillWidget).color)
        assertEquals(phaseViolet, (titleDivider as FillWidget).color)
        assertBounds(nameInput, panel, 12f, 32f, 396f, 20f)
        assertBounds(coordinates, panel, 12f, 58f, 396f, 20f)
        assertBounds(markCurrent, panel, 12f, 84f, 194f, 20f)
        assertBounds(addMark, panel, 214f, 84f, 194f, 20f)
        assertBounds(marks, panel, 12f, 108f, 396f, 94f)
        assertBounds(refresh, panel, 12f, 208f, 194f, 20f)
        assertBounds(done, panel, 214f, 208f, 194f, 20f)
    }

    private fun assertBounds(
        widget: Widget,
        panel: Widget,
        x: Float,
        y: Float,
        width: Float,
        height: Float
    ) {
        assertEquals(panel.getAbsoluteX() + x, widget.getAbsoluteX())
        assertEquals(panel.getAbsoluteY() + y, widget.getAbsoluteY())
        assertEquals(width, widget.width)
        assertEquals(height, widget.height)
    }

    @Test
    fun `reflection filter variants keep interaction columns on the classic projection`() {
        assertReflectionLayout(
            name = "reflection_filter_wide",
            viewportWidth = 854f,
            viewportHeight = 480f,
            panelWidth = 520f,
            panelHeight = 260f,
            leftOffset = 12f,
            leftWidth = 160f,
            middleOffset = 182f,
            rightOffset = 266f
        )
        assertReflectionLayout(
            name = "reflection_filter_compact",
            viewportWidth = 480f,
            viewportHeight = 270f,
            panelWidth = 460f,
            panelHeight = 238f,
            leftOffset = 12f,
            leftWidth = 140f,
            middleOffset = 162f,
            rightOffset = 246f
        )
    }

    private fun assertReflectionLayout(
        name: String,
        viewportWidth: Float,
        viewportHeight: Float,
        panelWidth: Float,
        panelHeight: Float,
        leftOffset: Float,
        leftWidth: Float,
        middleOffset: Float,
        rightOffset: Float
    ) {
        val root = load(name)
        layout(root, viewportWidth, viewportHeight)
        val panel = SerializedUiLayout.require(root, "panel")
        val projection = SerializedUiLayout.require(root, "panel_background")
        val borderLeft = SerializedUiLayout.require(root, "border_left")
        val borderRight = SerializedUiLayout.require(root, "border_right")
        val left = SerializedUiLayout.require(root, "left_column")
        val middle = SerializedUiLayout.require(root, "middle_column")
        val right = SerializedUiLayout.require(root, "right_column")

        assertNull(SerializedUiLayout.find(root, "title_accent"))
        assertTrue(projection is BlendQuadWidget)
        assertEquals(0.5f, projection.alpha)
        assertEquals(panel.getAbsoluteX() + 1f, projection.getAbsoluteX())
        assertEquals(panelWidth - 2f, projection.width)
        assertEquals(panel.getAbsoluteY() + 4f, borderLeft.getAbsoluteY())
        assertEquals(panelHeight - 8f, borderLeft.height)
        assertEquals(panel.getAbsoluteX() + panelWidth - 1f, borderRight.getAbsoluteX())
        assertEquals(panel.getAbsoluteY() + 4f, borderRight.getAbsoluteY())
        assertEquals(panelHeight - 8f, borderRight.height)
        assertEquals(panelWidth, panel.width)
        assertEquals(panelHeight, panel.height)
        assertEquals(panel.getAbsoluteX() + leftOffset, left.getAbsoluteX())
        assertEquals(leftWidth, left.width)
        assertEquals(panel.getAbsoluteX() + middleOffset, middle.getAbsoluteX())
        assertEquals(panel.getAbsoluteX() + rightOffset, right.getAbsoluteX())
    }

    private fun assertLayout(name: String, vararg required: String) {
        val root = load(name)
        for (widgetName in required) {
            assertNotNull(SerializedUiLayout.find(root, widgetName), "$name is missing $widgetName")
        }
        assertTrue(WidgetSerializer.encode(root).has("root"))
    }

    private fun load(name: String): FrameLayoutWidget {
        val path = "/assets/academy/ui/layout/$name.json"
        val json = javaClass.getResourceAsStream(path)?.bufferedReader()?.use { it.readText() }
        assertNotNull(json, "Missing layout resource $path")
        return WidgetSerializer.fromJsonString(json!!) as FrameLayoutWidget
    }

    private fun layout(root: FrameLayoutWidget, width: Float, height: Float) {
        root.measure(
            MeasureSpec(MeasureSpec.Mode.EXACTLY, width),
            MeasureSpec(MeasureSpec.Mode.EXACTLY, height)
        )
        root.layout(0f, 0f, width, height)
    }
}
