package org.academy.internal.client.gui.imgui

import com.mojang.blaze3d.pipeline.RenderTarget
import net.minecraft.client.Minecraft
import org.academy.Dev
import org.academy.api.client.gui.imgui.ImGuiUtilApi
import org.jetbrains.annotations.ApiStatus
import org.lwjgl.sdl.SDL_Event

/**
 * 游戏内 ImGui 门面：把 [ImGuiBackend] 绑定到游戏窗口，保持原 [org.academy.api.client.gui.imgui.ImGuiUtilApi]
 * 行为不变。独立桌面编辑器直接使用 [ImGuiBackend]。
 */
@ApiStatus.Internal
object ImGuiUtilInternal {
    private var backend: ImGuiBackend? = null

    /** Binds this implementation to [ImGuiUtilApi]; call once during client start. */
    fun bootstrap() {
        if (!Dev.HAS_IM_GUI) return
        ImGuiUtilApi.register(
            init = ::init,
            close = ::dispose,
            clearEventsQueue = ::clearEventsQueue,
            render = ::render,
            wantCaptureMouse = ::wantCaptureMouse,
            wantCaptureKeyboard = ::wantCaptureKeyboard,
        )
    }

    fun init() {
        val minecraft = Minecraft.getInstance()
        backend = ImGuiBackend(
            minecraft.window.handle(),
            ImGuiImplSdl(),
        ).also { it.init() }
    }

    fun render(renderTarget: RenderTarget, renderCommand: () -> Unit) {
        backend?.render(renderTarget, renderCommand)
    }

    fun clearEventsQueue() {
        backend?.clearEventsQueue()
    }

    fun onSdlEvent(event: SDL_Event) {
        backend?.onSdlEvent(event)
    }

    fun wantCaptureMouse(): Boolean = backend?.wantCaptureMouse() ?: false

    fun wantCaptureKeyboard(): Boolean = backend?.wantCaptureKeyboard() ?: false

    fun dispose() {
        backend?.dispose()
        backend = null
    }
}
