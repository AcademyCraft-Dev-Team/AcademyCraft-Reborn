package org.academy.desktop.platform

import com.mojang.blaze3d.pipeline.TextureTarget
import com.mojang.blaze3d.platform.Window
import com.mojang.blaze3d.platform.WindowEventHandler
import com.mojang.renderpearl.api.GpuFormat
import org.academy.api.client.gui.event.*
import org.academy.api.client.gui.frame.UiFrame
import org.academy.api.client.gui.render.UiContext
import org.academy.api.client.gui.widget.WidgetContainer
import org.academy.internal.client.gui.imgui.ImGuiBackend
import org.academy.internal.client.gui.imgui.ImGuiImplSdl
import org.lwjgl.sdl.SDLClipboard
import org.lwjgl.sdl.SDLEvents
import org.lwjgl.sdl.SDLKeycode
import org.lwjgl.sdl.SDL_Event

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

    private val sdlEvent = SDL_Event.malloc()

    private var mouseX = 0.0
    private var mouseY = 0.0
    private var ctrlDown = false
    private val pressedButtons = HashSet<Int>()
    private var autoTuned = false
    var surfaceNeedsReconfigure = false

    init {
        if (!root.isAttached()) root.dispatchAttached()
    }

    fun bind(window: Window) {
        this.window = window
        environment.clipboardGetter = { SDLClipboard.SDL_GetClipboardText() ?: "" }
        environment.clipboardSetter = { text -> SDLClipboard.SDL_SetClipboardText(text) }
        framebufferSizeChanged()
        if (app.usesImGui) {
            imGuiBackend = ImGuiBackend(
                window.handle(),
                ImGuiImplSdl { false }
            ).also { it.init() }
            environment.imguiBackend = imGuiBackend
        }
    }

    /** 由主循环每帧调用：泵出 SDL3 事件并分发给窗口/UI/ImGui。 */
    fun pollEvents() {
        while (SDLEvents.SDL_PollEvent(sdlEvent)) {
            window?.handleEvent(sdlEvent)
            when (sdlEvent.type()) {
                SDL_EVENT_KEY_DOWN, SDL_EVENT_KEY_UP -> onKey(sdlEvent)
                SDL_EVENT_TEXT_INPUT -> onChar(sdlEvent)
                SDL_EVENT_MOUSE_MOTION -> {
                    val motion = sdlEvent.motion()
                    onCursorPos(motion.x().toDouble(), motion.y().toDouble())
                }

                SDL_EVENT_MOUSE_BUTTON_DOWN, SDL_EVENT_MOUSE_BUTTON_UP -> onMouseButton(sdlEvent)
                SDL_EVENT_MOUSE_WHEEL -> {
                    val wheel = sdlEvent.wheel()
                    onScroll(wheel.x().toDouble(), wheel.y().toDouble())
                }
            }
            imGuiBackend?.onSdlEvent(sdlEvent)
        }
    }

    private fun createTarget() {
        target?.destroyBuffers()
        target = TextureTarget(
            "Editor", environment.physicalWidth, environment.physicalHeight, GpuFormat.RGBA8_UNORM, GpuFormat.D32_FLOAT
        )
    }

    fun frame(partialTick: Float) {
        environment.frameDeltaTicks = partialTick
        environment.drainMainThreadTasks()
        environment.drainRenderThreadTasks()
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
        sdlEvent.free()
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

    override fun fullscreenStateChanged(fullscreen: Boolean) {
        surfaceNeedsReconfigure = true
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

    private fun onMouseButton(event: SDL_Event) {
        val button = event.button().button().toInt() and 0xFF
        if (event.type() == SDL_EVENT_MOUSE_BUTTON_DOWN) {
            pressedButtons.add(button)
            root.dispatchEvent(MouseEvent.createPressEvent(mouseX, mouseY, button))
        } else {
            pressedButtons.remove(button)
            root.dispatchEvent(MouseEvent.createReleaseEvent(mouseX, mouseY, button))
        }
    }

    private fun onScroll(x: Double, y: Double) {
        root.dispatchEvent(ScrollEvent(mouseX, mouseY, y, x, ctrlDown))
    }

    private fun onKey(event: SDL_Event) {
        val key = event.key()
        val action = when {
            event.type() == SDL_EVENT_KEY_UP -> KEY_RELEASE
            key.repeat() -> KEY_REPEAT
            else -> KEY_PRESS
        }
        val modifiers = key.mod().toInt() and 0xFFFF
        ctrlDown = (modifiers and SDLKeycode.SDL_KMOD_CTRL) != 0
        if (app.onKey(key.key(), action, modifiers)) return
        val type = when (action) {
            KEY_PRESS, KEY_REPEAT -> EventType.KEY_PRESSED
            KEY_RELEASE -> EventType.KEY_RELEASED
            else -> return
        }
        // 与游戏内 UiScreen 一致：widget KeyEvent 的 keyCode 存 SDL scancode，scanCode 存 SDL keycode。
        root.dispatchEvent(KeyEvent(type, key.scancode(), key.key(), modifiers))
    }

    private fun onChar(event: SDL_Event) {
        val text = event.text().textString() ?: return
        if (text.isEmpty()) return
        text.codePoints().toArray().forEach { codepoint -> root.dispatchEvent(CharTypedEvent(codepoint)) }
    }

    private companion object {
        const val TARGET_LOGICAL_WIDTH = 720f

        const val SDL_EVENT_KEY_DOWN = 0x300
        const val SDL_EVENT_KEY_UP = 0x301
        const val SDL_EVENT_TEXT_INPUT = 0x303
        const val SDL_EVENT_MOUSE_MOTION = 0x400
        const val SDL_EVENT_MOUSE_BUTTON_DOWN = 0x401
        const val SDL_EVENT_MOUSE_BUTTON_UP = 0x402
        const val SDL_EVENT_MOUSE_WHEEL = 0x403

        // SDL / InputConstants action values.
        const val KEY_RELEASE = 0
        const val KEY_PRESS = 1
        const val KEY_REPEAT = -1
    }
}
