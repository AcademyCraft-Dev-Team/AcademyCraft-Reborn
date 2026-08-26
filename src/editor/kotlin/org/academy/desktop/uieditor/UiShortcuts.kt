package org.academy.desktop.uieditor

import imgui.ImGui

/** 快捷键修饰键掩码（与 ImGui IO keyMods 一致）。 */
object UiKeyMods {
    const val NONE = 0
    const val CTRL = 1
    const val SHIFT = 2
    const val ALT = 4
    const val SUPER = 8
}

/**
 * 快捷键注册表：chord（修饰键 + 主键）→ 动作，每帧统一分发。
 * 修饰键要求完全匹配（含无修饰），避免 Ctrl+V 与 Ctrl+Alt+V 等绑定互相触发。
 */
class UiShortcutRegistry {
    private val entries = mutableListOf<Entry>()

    fun register(mods: Int, key: Int, action: () -> Unit): UiShortcutRegistry {
        entries.add(Entry(mods, key, action))
        return this
    }

    fun handle() {
        for (entry in entries) {
            if (!ImGui.isKeyPressed(entry.key)) continue
            if (ImGui.getIO().keyMods == entry.mods) entry.action()
        }
    }

    private class Entry(val mods: Int, val key: Int, val action: () -> Unit)
}
