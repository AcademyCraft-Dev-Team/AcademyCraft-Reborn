package org.academy.internal.client.gui.debug

import com.google.gson.JsonObject
import org.academy.api.client.gui.widget.FrameLayoutWidget

/** A live screen whose static serialized subtree can be inspected without capturing code-owned state. */
interface SerializedUiDebugHost {
    fun debugLayoutId(): String

    fun debugLayoutRoot(): FrameLayoutWidget

    fun alwaysShowDebugEditor(): Boolean = false

    fun sanitizeDebugCapture(json: JsonObject): JsonObject = json
}
