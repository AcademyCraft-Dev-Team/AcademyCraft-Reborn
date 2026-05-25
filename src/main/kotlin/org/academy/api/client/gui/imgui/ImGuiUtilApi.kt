package org.academy.api.client.gui.imgui

import com.mojang.blaze3d.pipeline.RenderTarget
import imgui.gl3.ImGuiImplGl3
import imgui.glfw.ImGuiImplGlfw
import org.academy.Dev
import org.academy.internal.client.gui.imgui.ImGuiUtilInternal

object ImGuiUtilApi {
    val EMPTY_RUNNABLE: () -> Unit = {}
    val NULL_SUPPLIER: () -> Any? = { null }
    private val IM_GUI_INIT: () -> Unit
    private val IM_GUI_CLOSE: () -> Unit
    private val CLEAR_EVENTS_QUEUE: () -> Unit
    private val RENDER: (RenderTarget, () -> Unit) -> Unit
    private val IM_GUI_IMPL_GL_3_GETTER: () -> Any?
    private val IM_GUI_IMPL_GLFW_GETTER: () -> Any?
    private val WANT_CAPTURE_MOUSE_GETTER: () -> Boolean
    private val WANT_CAPTURE_KEYBOARD_GETTER: () -> Boolean

    init {
        var imGuiInit = EMPTY_RUNNABLE
        var imGuiClose = EMPTY_RUNNABLE
        var clearEventsQueue = EMPTY_RUNNABLE
        var render = { _: RenderTarget, _: () -> Unit -> }

        var imGuiImplGl3Getter = NULL_SUPPLIER
        var imGuiImplGlfwGetter = NULL_SUPPLIER
        var wantCaptureMouseGetter = { false }
        var wantCaptureKeyboardGetter = { false }

        if (Dev.HAS_IM_GUI) {
            imGuiInit = { ImGuiUtilInternal.init() }
            imGuiClose = { ImGuiUtilInternal.dispose() }
            clearEventsQueue = { ImGuiUtilInternal.clearEventsQueue() }
            render = { renderTarget, renderCommand ->
                ImGuiUtilInternal.render(
                    renderTarget,
                    renderCommand
                )
            }

            imGuiImplGl3Getter = { ImGuiUtilInternal.imGuiImplGl3 }
            imGuiImplGlfwGetter = { ImGuiUtilInternal.imGuiImplGlfw }
            wantCaptureMouseGetter = { ImGuiUtilInternal.wantCaptureMouse() }
            wantCaptureKeyboardGetter = { ImGuiUtilInternal.wantCaptureKeyboard() }
        }

        IM_GUI_INIT = imGuiInit
        IM_GUI_CLOSE = imGuiClose
        CLEAR_EVENTS_QUEUE = clearEventsQueue
        RENDER = render
        IM_GUI_IMPL_GL_3_GETTER = imGuiImplGl3Getter
        IM_GUI_IMPL_GLFW_GETTER = imGuiImplGlfwGetter
        WANT_CAPTURE_MOUSE_GETTER = wantCaptureMouseGetter
        WANT_CAPTURE_KEYBOARD_GETTER = wantCaptureKeyboardGetter
    }

    fun init() {
        IM_GUI_INIT()
    }

    fun close() {
        IM_GUI_CLOSE()
    }

    fun clearEventsQueue() {
        CLEAR_EVENTS_QUEUE()
    }

    fun render(renderTarget: RenderTarget, renderCommand: () -> Unit) {
        RENDER.invoke(renderTarget, renderCommand)
    }

    fun wantCaptureMouse(): Boolean {
        return WANT_CAPTURE_MOUSE_GETTER()
    }

    fun wantCaptureKeyboard(): Boolean {
        return WANT_CAPTURE_KEYBOARD_GETTER()
    }

    val imGuiImplGl3: ImGuiImplGl3?
        get() = IM_GUI_IMPL_GL_3_GETTER() as ImGuiImplGl3?

    val imGuiImplGlfw: ImGuiImplGlfw?
        get() = IM_GUI_IMPL_GLFW_GETTER() as ImGuiImplGlfw?
}