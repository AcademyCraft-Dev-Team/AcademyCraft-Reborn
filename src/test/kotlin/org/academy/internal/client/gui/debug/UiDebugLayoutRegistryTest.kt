package org.academy.internal.client.gui.debug

import org.academy.api.client.gui.serialize.WidgetSerializer
import org.academy.api.client.gui.widget.FrameLayoutWidget
import org.academy.internal.client.gui.SerializedUiLayout
import org.academy.internal.client.hud.HudLayoutDefaults
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class UiDebugLayoutRegistryTest {
    @Test
    fun `all registered layouts decode with typed bindings`() {
        assertEquals(6, UiDebugLayoutRegistry.gui().size)
        assertEquals(4, UiDebugLayoutRegistry.hud().size)
        for (definition in UiDebugLayoutRegistry.all()) {
            val path = "/assets/academy/${definition.resource.path}"
            val json = javaClass.getResourceAsStream(path)?.bufferedReader()?.use { it.readText() }
            assertNotNull(json, "Missing debug layout resource $path")
            val root = WidgetSerializer.fromJsonString(json!!) as FrameLayoutWidget
            for (binding in definition.bindings) {
                val widget = SerializedUiLayout.find(root, binding.name)
                assertNotNull(widget, "${definition.id} is missing ${binding.name}")
                assertTrue(binding.widgetClass.isInstance(widget), "${definition.id}/${binding.name} has wrong type")
            }
        }
    }

    @Test
    fun `hud defaults preserve anchors and round trip`() {
        val defaults = HudLayoutDefaults.defaults()
        assertEquals(HudLayoutDefaults.Anchor.TOP_LEFT, defaults.regions.getValue("toggle_status").anchor)
        assertEquals(HudLayoutDefaults.Anchor.CENTER_LEFT, defaults.regions.getValue("mental_control").anchor)
        assertEquals(HudLayoutDefaults.Anchor.TOP_RIGHT, defaults.regions.getValue("cp").anchor)
        assertEquals(HudLayoutDefaults.Anchor.CENTER_RIGHT, defaults.regions.getValue("skill_wheel").anchor)

        val decoded = HudLayoutDefaults.loadJson(HudLayoutDefaults.toJson(defaults))
        assertEquals(defaults, decoded)
    }

    @Test
    fun `invalid hud default values fall back or clamp`() {
        val json = HudLayoutDefaults.toJson(HudLayoutDefaults.defaults())
        val regions = json.getAsJsonObject("regions")
        regions.getAsJsonObject("cp").addProperty("scale", 100f)
        regions.getAsJsonObject("skill_wheel").addProperty("anchor", "INVALID")
        val decoded = HudLayoutDefaults.loadJson(json)
        assertEquals(2f, decoded.regions.getValue("cp").scale)
        assertEquals(HudLayoutDefaults.Anchor.CENTER_RIGHT, decoded.regions.getValue("skill_wheel").anchor)
    }
}
