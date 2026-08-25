package org.academy.internal.client.gui.imgui

import com.mojang.blaze3d.pipeline.RenderTarget
import net.minecraft.client.Minecraft
import org.jetbrains.annotations.ApiStatus

/**
 * 游戏内 ImGui 门面：把 [ImGuiBackend] 绑定到游戏窗口，保持原 [org.academy.api.client.gui.imgui.ImGuiUtilApi]
 * 行为不变。独立桌面编辑器直接使用 [ImGuiBackend]。
 */
@ApiStatus.Internal
object ImGuiUtilInternal {
    private var backend: ImGuiBackend? = null

    val imGuiImplGlfw: Any?
        get() = backend?.imGuiImplGlfw

    fun init() {
        val minecraft = Minecraft.getInstance()
        backend = ImGuiBackend(
            minecraft.window.handle()
        ).also { it.init() }
    }

    fun render(renderTarget: RenderTarget, renderCommand: () -> Unit) {
        backend?.render(renderTarget, renderCommand)
    }

    fun clearEventsQueue() {
        backend?.clearEventsQueue()
    }

    fun wantCaptureMouse(): Boolean = backend?.wantCaptureMouse() ?: false

    fun wantCaptureKeyboard(): Boolean = backend?.wantCaptureKeyboard() ?: false

    fun dispose() {
        backend?.dispose()
        backend = null
    }
}
