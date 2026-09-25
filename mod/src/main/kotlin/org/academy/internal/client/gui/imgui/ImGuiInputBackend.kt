package org.academy.internal.client.gui.imgui

import org.jetbrains.annotations.ApiStatus
import org.lwjgl.sdl.SDL_Event

/**
 * ImGui 平台输入后端抽象。游戏内使用 SDL3 实现（[ImGuiImplSdl]），
 * 独立桌面编辑器可使用 GLFW 实现。
 */
@ApiStatus.Internal
interface ImGuiInputBackend {
    fun init(windowHandle: Long)

    fun newFrame()

    fun shutdown()

    /** 转发一个平台原生事件；非 SDL 平台默认忽略。 */
    fun processEvent(event: SDL_Event) {
    }
}
