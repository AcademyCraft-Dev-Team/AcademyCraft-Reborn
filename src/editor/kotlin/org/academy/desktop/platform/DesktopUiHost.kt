package org.academy.desktop.platform

import com.mojang.blaze3d.GpuFormat
import com.mojang.blaze3d.pipeline.TextureTarget
import com.mojang.blaze3d.platform.Window
import com.mojang.blaze3d.platform.WindowEventHandler
import org.academy.api.client.gui.event.*
import org.academy.api.client.gui.frame.UiFrame
import org.academy.api.client.gui.render.UiContext
import org.academy.api.client.gui.widget.WidgetContainer
import org.academy.internal.client.gui.imgui.ImGuiBackend
import org.lwjgl.glfw.GLFW

class DesktopUiHost(
    private val app: EditorApp,
    private val environment: DesktopEnvironment,
) : WindowEventHandler {

    private val uiContext = UiContext()
    private var window: Window? = null
    var target: TextureTarget? = null
        private set

    val root: WidgetContainer = app.createRoot()

    private var imGuiBackend: ImGuiBackend? = null

    val imgui: ImGuiBackend?
        get() = imGuiBackend

    private var mouseX = 0.0
    private var mouseY = 0.0
    private var ctrlDown = false
    private val pressedButtons = HashSet<Int>()
    private var autoTuned = false
    var surfaceNeedsReconfigure = false

    private companion object {
        const val TARGET_LOGICAL_WIDTH = 720f
    }

    init {
        if (!root.isAttached()) root.dispatchAttached()
    }

    fun bind(window: Window) {
        this.window = window
        environment.clipboardGetter = { GLFW.glfwGetClipboardString(window.handle()) ?: "" }
        environment.clipboardSetter = { text -> GLFW.glfwSetClipboardString(window.handle(), text) }
        GLFW.glfwSetCursorPosCallback(window.handle()) { _, x, y -> onCursorPos(x, y) }
        GLFW.glfwSetMouseButtonCallback(window.handle()) { _, button, action, _ -> onMouseButton(button, action) }
        GLFW.glfwSetScrollCallback(window.handle()) { _, x, y -> onScroll(x, y) }
        GLFW.glfwSetKeyCallback(window.handle()) { _, key, scancode, action, mods ->
            onKey(
                key,
                scancode,
                action,
                mods
            )
        }
        GLFW.glfwSetCharCallback(window.handle()) { _, codepoint -> onChar(codepoint) }
        framebufferSizeChanged()
        if (app.usesImGui) {
            imGuiBackend = ImGuiBackend(
                window.handle()
            ).also { it.init() }
            environment.imguiBackend = imGuiBackend
        }
    }

    private fun createTarget() {
        target?.destroyBuffers()
        target = TextureTarget(
            "Editor", environment.physicalWidth, environment.physicalHeight, true, GpuFormat.RGBA8_UNORM
        )
    }

    fun frame(partialTick: Float) {
        environment.frameDeltaTicks = partialTick
        environment.drainMainThreadTasks()
        UiFrame.onFrame()
        app.onFrame(partialTick)
        uiContext.perform(root, mouseX, mouseY, partialTick)
        uiContext.upload(target!!, true)
        app.renderBackground(target!!)
        imGuiBackend?.render(target!!) { app.renderImGui() }
    }

    fun close() {
        imGuiBackend?.dispose()
        imGuiBackend = null
        target?.destroyBuffers()
        target = null
        uiContext.close()
    }

    override fun framebufferSizeChanged() {
        val w = window ?: return
        environment.physicalWidth = w.width
        environment.physicalHeight = w.height
        if (!autoTuned) {
            autoTuned = true
            environment.guiScale = (w.width / TARGET_LOGICAL_WIDTH).coerceIn(0.75f, 4f)
        }
        createTarget()
        root.requestLayout()
        app.onResize(environment.guiScaledWidth, environment.guiScaledHeight)
        surfaceNeedsReconfigure = true
    }

    override fun resizeGui() {
    }

    override fun cursorEntered() {
    }

    private fun onCursorPos(x: Double, y: Double) {
        val gx = x / environment.guiScale
        val gy = y / environment.guiScale
        mouseX = gx
        mouseY = gy
        val event = if (pressedButtons.isNotEmpty()) {
            val button = pressedButtons.first()
            MouseEvent.createDragEvent(gx, gy, button, gx, gy)
        } else {
            MouseEvent.createMoveEvent(gx, gy)
        }
        root.dispatchEvent(event)
    }

    private fun onMouseButton(button: Int, action: Int) {
        if (action == GLFW.GLFW_PRESS) {
            pressedButtons.add(button)
            val event = MouseEvent.createPressEvent(mouseX, mouseY, button)
            root.dispatchEvent(event)
        } else if (action == GLFW.GLFW_RELEASE) {
            pressedButtons.remove(button)
            root.dispatchEvent(MouseEvent.createReleaseEvent(mouseX, mouseY, button))
        }
    }

    private fun onScroll(x: Double, y: Double) {
        root.dispatchEvent(ScrollEvent(mouseX, mouseY, y, x, ctrlDown))
    }

    private fun onKey(key: Int, scancode: Int, action: Int, modifiers: Int) {
        ctrlDown = (modifiers and GLFW.GLFW_MOD_CONTROL) != 0
        if (app.onKey(key, action, modifiers)) return
        val type = when (action) {
            GLFW.GLFW_PRESS, GLFW.GLFW_REPEAT -> EventType.KEY_PRESSED
            GLFW.GLFW_RELEASE -> EventType.KEY_RELEASED
            else -> return
        }
        root.dispatchEvent(KeyEvent(type, key, scancode, modifiers))
    }

    private fun onChar(codepoint: Int) {
        root.dispatchEvent(CharTypedEvent(codepoint))
    }
}
