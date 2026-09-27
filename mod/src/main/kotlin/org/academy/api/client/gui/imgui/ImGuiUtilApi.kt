package org.academy.api.client.gui.imgui

import com.mojang.blaze3d.pipeline.RenderTarget

object ImGuiUtilApi {
    val EMPTY_RUNNABLE: () -> Unit = {}
    val NULL_SUPPLIER: () -> Any? = { null }

    private var initImpl: () -> Unit = EMPTY_RUNNABLE
    private var closeImpl: () -> Unit = EMPTY_RUNNABLE
    private var clearEventsQueueImpl: () -> Unit = EMPTY_RUNNABLE
    private var renderImpl: (RenderTarget, () -> Unit) -> Unit = { _, _ -> }
    private var wantCaptureMouseImpl: () -> Boolean = { false }
    private var wantCaptureKeyboardImpl: () -> Boolean = { false }

    /** Called once by the ImGui implementation to bind the real backend. */
    fun register(
        init: () -> Unit,
        close: () -> Unit,
        clearEventsQueue: () -> Unit,
        render: (RenderTarget, () -> Unit) -> Unit,
        wantCaptureMouse: () -> Boolean,
        wantCaptureKeyboard: () -> Boolean,
    ) {
        initImpl = init
        closeImpl = close
        clearEventsQueueImpl = clearEventsQueue
        renderImpl = render
        wantCaptureMouseImpl = wantCaptureMouse
        wantCaptureKeyboardImpl = wantCaptureKeyboard
    }

    fun init() {
        initImpl()
    }

    fun close() {
        closeImpl()
    }

    fun clearEventsQueue() {
        clearEventsQueueImpl()
    }

    fun render(renderTarget: RenderTarget, renderCommand: () -> Unit) {
        renderImpl(renderTarget, renderCommand)
    }

    fun wantCaptureMouse(): Boolean {
        return wantCaptureMouseImpl()
    }

    fun wantCaptureKeyboard(): Boolean {
        return wantCaptureKeyboardImpl()
    }
}
