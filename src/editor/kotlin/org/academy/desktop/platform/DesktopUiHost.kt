package org.academy.desktop.platform

import com.mojang.blaze3d.GpuFormat
import com.mojang.blaze3d.pipeline.TextureTarget
import com.mojang.blaze3d.platform.Window
import com.mojang.blaze3d.platform.WindowEventHandler
import org.academy.api.client.gui.event.CharTypedEvent
import org.academy.api.client.gui.event.EventType
import org.academy.api.client.gui.event.KeyEvent
import org.academy.api.client.gui.event.MouseEvent
import org.academy.api.client.gui.event.ScrollEvent
import org.academy.api.client.gui.render.UiContext
import org.academy.api.client.gui.widget.Widget
import org.academy.api.client.gui.widget.WidgetContainer
import org.academy.internal.client.gui.imgui.ImGuiBackend
import org.lwjgl.glfw.GLFW

/**
 * Hosts an [EditorApp]'s widget tree on the desktop window: owns the offscreen
 * [TextureTarget], drives [UiContext], forwards GLFW input into framework events,
 * and reacts to window resizes.
 */
class DesktopUiHost(
    private val app: EditorApp,
    private val environment: DesktopEnvironment,
) : WindowEventHandler {

    private val uiContext = UiContext()
    private var window: Window? = null
    var target: TextureTarget? = null
        private set

    val root: WidgetContainer

    private var imGuiBackend: ImGuiBackend? = null

    /** 共享 ImGui 后端（供编辑器注册任意纹理显示，M11-02）。 */
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
        root = app.createRoot()
        if (!root.isAttached()) root.dispatchAttached()
    }

    /** Called after the [Window] is created; registers input callbacks. */
    fun bind(window: Window) {
        this.window = window
        environment.clipboardGetter = { GLFW.glfwGetClipboardString(window.handle()) ?: "" }
        environment.clipboardSetter = { text -> GLFW.glfwSetClipboardString(window.handle(), text) }
        GLFW.glfwSetCursorPosCallback(window.handle()) { _, x, y -> onCursorPos(x, y) }
        GLFW.glfwSetMouseButtonCallback(window.handle()) { _, button, action, _ -> onMouseButton(button, action) }
        GLFW.glfwSetScrollCallback(window.handle()) { _, x, y -> onScroll(x, y) }
        GLFW.glfwSetKeyCallback(window.handle()) { _, key, scancode, action, mods -> onKey(key, scancode, action, mods) }
        GLFW.glfwSetCharCallback(window.handle()) { _, codepoint -> onChar(codepoint) }
        // 同步实际 GLFW framebuffer 尺寸：环境 + 离屏 target 必须在首帧前与真实
        // framebuffer 对齐（framebuffer-size 回调因 old==new 不会在启动时触发），否则
        // ImGui 的 scissor 会越出 target 导致 "Scissor ... out of bounds"。
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

    /** Called once per frame; performs layout, command generation and GPU upload. */
    fun frame(partialTick: Float) {
        environment.frameDeltaTicks = partialTick
        environment.drainMainThreadTasks()
        app.onFrame(partialTick)
        root.tick()
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

    // ---- WindowEventHandler ----

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

    // ---- GLFW input ----

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
