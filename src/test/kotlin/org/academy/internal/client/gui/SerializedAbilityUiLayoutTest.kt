package org.academy.internal.client.gui

import org.academy.api.client.gui.layout.MeasureSpec
import org.academy.api.client.gui.serialize.WidgetSerializer
import org.academy.api.client.gui.widget.FrameLayoutWidget
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
    fun `location panel remains centered after deserialization`() {
        val root = load("location_teleport")
        layout(root, 854f, 480f)
        val panel = SerializedUiLayout.require(root, "panel")
        assertEquals(420f, panel.width)
        assertEquals(212f, panel.height)
        assertEquals(217f, panel.x)
        assertEquals(134f, panel.y)
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
