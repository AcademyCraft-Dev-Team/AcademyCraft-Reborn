package org.academy.internal.client.gui.imgui

import com.mojang.blaze3d.pipeline.RenderTarget
import net.minecraft.client.Minecraft
import org.academy.Dev
import org.academy.api.client.gui.imgui.ImGuiUtilApi
import org.jetbrains.annotations.ApiStatus
import org.lwjgl.sdl.SDL_Event

@ApiStatus.Internal
object ImGuiUtilInternal {
    private var backend: ImGuiBackend? = null

    fun bootstrap() {
        if (!Dev.HAS_IM_GUI) return
        ImGuiUtilApi.register(
            init = ::init,
            close = ::dispose,
            clearEventsQueue = ::clearEventsQueue,
            render = ::render,
            wantCaptureMouse = ::wantCaptureMouse,
            wantCaptureKeyboard = ::wantCaptureKeyboard,
            beginFrame = ::beginFrame,
            ensureFrame = ::ensureFrame,
            submit = ::submit,
            endFrame = ::endFrame,
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

    fun beginFrame() {
        backend?.beginFrame()
    }

    fun ensureFrame() {
        backend?.ensureFrame()
    }

    fun submit(renderCommand: () -> Unit) {
        backend?.submit(renderCommand)
    }

    fun endFrame(renderTarget: RenderTarget) {
        backend?.endFrame(renderTarget)
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
