package org.academy.api.client.gui.imgui

import com.mojang.blaze3d.pipeline.RenderTarget

object ImGuiUtilApi {
    val EMPTY_RUNNABLE: () -> Unit = {}

    private var initImpl: () -> Unit = EMPTY_RUNNABLE
    private var closeImpl: () -> Unit = EMPTY_RUNNABLE
    private var clearEventsQueueImpl: () -> Unit = EMPTY_RUNNABLE
    private var renderImpl: (RenderTarget, () -> Unit) -> Unit = { _, _ -> }
    private var beginFrameImpl: () -> Unit = EMPTY_RUNNABLE
    private var ensureFrameImpl: () -> Unit = EMPTY_RUNNABLE
    private var submitImpl: (() -> Unit) -> Unit = {}
    private var endFrameImpl: (RenderTarget) -> Unit = {}
    private var wantCaptureMouseImpl: () -> Boolean = { false }
    private var wantCaptureKeyboardImpl: () -> Boolean = { false }

    fun register(
        init: () -> Unit,
        close: () -> Unit,
        clearEventsQueue: () -> Unit,
        render: (RenderTarget, () -> Unit) -> Unit,
        wantCaptureMouse: () -> Boolean,
        wantCaptureKeyboard: () -> Boolean,
        beginFrame: () -> Unit = EMPTY_RUNNABLE,
        ensureFrame: () -> Unit = EMPTY_RUNNABLE,
        submit: (() -> Unit) -> Unit = {},
        endFrame: (RenderTarget) -> Unit = {},
    ) {
        initImpl = init
        closeImpl = close
        clearEventsQueueImpl = clearEventsQueue
        renderImpl = render
        wantCaptureMouseImpl = wantCaptureMouse
        wantCaptureKeyboardImpl = wantCaptureKeyboard
        beginFrameImpl = beginFrame
        ensureFrameImpl = ensureFrame
        submitImpl = submit
        endFrameImpl = endFrame
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

    fun beginFrame() {
        beginFrameImpl()
    }

    fun ensureFrame() {
        ensureFrameImpl()
    }

    fun submit(renderCommand: () -> Unit) {
        submitImpl(renderCommand)
    }

    fun endFrame(renderTarget: RenderTarget) {
        endFrameImpl(renderTarget)
    }

    fun wantCaptureMouse(): Boolean {
        return wantCaptureMouseImpl()
    }

    fun wantCaptureKeyboard(): Boolean {
        return wantCaptureKeyboardImpl()
    }
}
