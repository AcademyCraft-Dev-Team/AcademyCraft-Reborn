package org.academy.api.client.gui.editor

import com.google.gson.JsonObject
import org.academy.api.client.gui.serialize.UiJson
import org.academy.api.client.gui.serialize.WidgetSerializer
import org.academy.internal.client.gui.debug.UiDebugLayoutRegistry
import java.nio.file.Files

/**
 * 编辑器入口, 供命令/调试工具调用.
 */
object UiLayoutEditor {
    @JvmStatic
    fun open() {
        UiLayoutEditorScreen.open(null)
    }

    @JvmStatic
    fun open(fileName: String) {
        if (UiDebugLayoutRegistry.find(fileName) != null) {
            UiLayoutEditorScreen.openDebug(fileName)
            return
        }
        val json = try {
            val path = WidgetSerializer.layoutDir().resolve("$fileName.json")
            UiJson.GSON.fromJson(Files.readString(path), JsonObject::class.java)
        } catch (e: Exception) {
            null
        }
        UiLayoutEditorScreen.open(json)
    }
}
