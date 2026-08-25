package org.academy.api.client.gui.serialize

import org.academy.api.client.gui.state.UiState
import org.academy.api.client.gui.widget.LabelWidget
import org.academy.api.client.gui.widget.ProgressBarWidget
import org.academy.api.client.gui.widget.Widget
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test

class SerializationV2Test {

    @Test
    fun `single-source schema clamps out of range values on decode`() {
        val json = """
            {
              "version": 2,
              "root": {
                "type": "label", "name": "title",
                "props": { "text": "Hello", "base_font_size": 999 }
              }
            }
        """.trimIndent()

        val decoded = WidgetSerializer.fromJsonString(json) as LabelWidget
        assertEquals("Hello", decoded.text)
        assertEquals(64f, decoded.baseFontSize, "base_font_size must be clamped to its schema max")
    }

    @Test
    fun `bind_text wires a label to a UiState`() {
        val playerName = UiState("Alice")
        val bindings = UiBindingContext().register("player.name", playerName)
        val json = """
            {
              "version": 2,
              "root": {
                "type": "label", "name": "title",
                "common": { "bind_text": "${'$'}player.name" }
              }
            }
        """.trimIndent()

        val decoded = WidgetSerializer.fromJsonString(json, bindings) as LabelWidget
        assertEquals("Alice", decoded.text)

        playerName.value = "Bob"
        assertEquals("Bob", decoded.text)
    }

    @Test
    fun `visible_when wires visibility to a UiState`() {
        val unlocked = UiState(false)
        val bindings = UiBindingContext().register("model.isUnlocked", unlocked)
        val json = """
            {
              "version": 2,
              "root": {
                "type": "label", "name": "x",
                "common": { "visible_when": "${'$'}model.isUnlocked" }
              }
            }
        """.trimIndent()

        val decoded = WidgetSerializer.fromJsonString(json, bindings) as LabelWidget
        assertEquals(Widget.Visibility.INVISIBLE, decoded.visibility)

        unlocked.value = true
        assertEquals(Widget.Visibility.VISIBLE, decoded.visibility)
    }

    @Test
    fun `bindings without a context leave default values`() {
        val json = """
            {
              "version": 2,
              "root": {
                "type": "label", "name": "x",
                "common": { "bind_text": "${'$'}player.name" }
              }
            }
        """.trimIndent()

        val decoded = WidgetSerializer.fromJsonString(json) as LabelWidget
        assertEquals("", decoded.text)
    }

    @Test
    fun `v1 document still decodes`() {
        val json = """
            {
              "version": 1,
              "root": {
                "type": "label", "name": "root",
                "props": { "text": "legacy" }
              }
            }
        """.trimIndent()

        val decoded = WidgetSerializer.fromJsonString(json)
        assertEquals("legacy", (decoded as LabelWidget).text)
    }

    @Test
    fun `unsupported version is rejected`() {
        val json = """
            {
              "version": 0,
              "root": { "type": "label", "name": "root" }
            }
        """.trimIndent()
        assertThrows(IllegalArgumentException::class.java) {
            WidgetSerializer.fromJsonString(json)
        }
    }

    @Test
    fun `progress_when wires progress to a UiState`() {
        val progress = UiState(0f)
        val bindings = UiBindingContext().register("model.progress", progress)
        val json = """
            {
              "version": 2,
              "root": {
                "type": "progress_bar", "name": "p",
                "common": { "progress_when": "${'$'}model.progress" }
              }
            }
        """.trimIndent()

        val decoded = WidgetSerializer.fromJsonString(json, bindings) as ProgressBarWidget
        assertEquals(0f, decoded.progress)

        progress.value = 42f
        assertEquals(42f, decoded.progress)
    }
}
