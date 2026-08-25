package org.academy.api.client.gui.serialize

import com.google.gson.JsonObject
import org.academy.api.client.gui.widget.LinearLayoutWidget
import org.academy.api.client.gui.widget.LabelWidget
import org.academy.api.client.gui.widget.WidgetContainer
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test

class UiTemplateTest {

    private fun node(type: String, name: String, props: JsonObject = JsonObject()): WidgetNode {
        return WidgetNode(type, name, props = props)
    }

    @Test
    fun `include expands a function template`() {
        val templates = UiTemplateRegistry().register("skill_row") { params ->
            node("label", "row", JsonObject().apply { addProperty("text", params.get("title").asString) })
        }
        val json = """
            {
              "version": 2,
              "root": {
                "type": "linear_layout", "name": "list",
                "children": [
                  { "type": "include", "name": "row_a", "template": "skill_row",
                    "props": { "title": "Hello" } }
                ]
              }
            }
        """.trimIndent()

        val decoded = WidgetSerializer.fromJsonString(json, null, templates) as LinearLayoutWidget
        val row = decoded.children["row_a"] as LabelWidget
        assertEquals("Hello", row.text)
    }

    @Test
    fun `raw node template substitutes param placeholders`() {
        val templateNode = node(
            "label", "entry",
            JsonObject().apply { addProperty("text", "\$param.title") }
        )
        val templates = UiTemplateRegistry().register("entry", templateNode)
        val json = """
            {
              "version": 2,
              "root": {
                "type": "linear_layout", "name": "list",
                "children": [
                  { "type": "include", "name": "e", "template": "entry",
                    "props": { "title": "World" } }
                ]
              }
            }
        """.trimIndent()

        val decoded = WidgetSerializer.fromJsonString(json, null, templates) as LinearLayoutWidget
        assertEquals("World", (decoded.children["e"] as LabelWidget).text)
    }

    @Test
    fun `repeat with count expands item copies`() {
        val json = """
            {
              "version": 2,
              "root": {
                "type": "linear_layout", "name": "list",
                "repeat": {
                  "count": 3,
                  "item": { "type": "label", "name": "entry", "props": { "text": "x" } }
                }
              }
            }
        """.trimIndent()

        val decoded = WidgetSerializer.fromJsonString(json) as LinearLayoutWidget
        assertEquals(3, decoded.children.size)
        assertEquals("x", (decoded.children["entry_0"] as LabelWidget).text)
        assertEquals("x", (decoded.children["entry_2"] as LabelWidget).text)
    }

    @Test
    fun `repeat with source resolves count via binding context`() {
        val bindings = UiBindingContext().registerRepeatCount("model.skills") { 4 }
        val json = """
            {
              "version": 2,
              "root": {
                "type": "linear_layout", "name": "list",
                "repeat": {
                  "source": "${'$'}model.skills",
                  "item": { "type": "label", "name": "entry", "props": { "text": "y" } }
                }
              }
            }
        """.trimIndent()

        val decoded = WidgetSerializer.fromJsonString(json, bindings) as LinearLayoutWidget
        assertEquals(4, decoded.children.size)
    }

    @Test
    fun `unknown template is rejected`() {
        val json = """
            {
              "version": 2,
              "root": {
                "type": "include", "name": "x", "template": "missing"
              }
            }
        """.trimIndent()
        assertThrows(IllegalArgumentException::class.java) {
            WidgetSerializer.fromJsonString(json, null, UiTemplateRegistry())
        }
    }

    @Test
    fun `include and repeat survive node round trip`() {
        val json = """
            {
              "version": 2,
              "root": {
                "type": "linear_layout", "name": "list",
                "repeat": {
                  "count": 2,
                  "item": { "type": "label", "name": "entry" }
                },
                "children": [
                  { "type": "include", "name": "tpl", "template": "t" }
                ]
              }
            }
        """.trimIndent()
        val node = WidgetNode.fromJson(UiJson.GSON.fromJson(json, JsonObject::class.java).getAsJsonObject("root"))
        val reparsed = WidgetNode.fromJson(node.toJson())
        assertEquals(2, reparsed.repeatCount)
        assertEquals("entry", reparsed.repeatItem?.name)
        assertEquals("t", reparsed.children[0].template)
    }
}
