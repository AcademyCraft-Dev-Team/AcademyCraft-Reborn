package org.academy.internal.client.gui.debug

import org.academy.api.client.gui.serialize.WidgetSerializer
import org.academy.api.client.gui.widget.FrameLayoutWidget
import org.academy.internal.client.gui.SerializedUiLayout
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class UiDebugLayoutRegistryTest {
    @Test
    fun `all registered layouts decode with typed bindings`() {
        assertEquals(6, UiDebugLayoutRegistry.gui().size)
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
}
