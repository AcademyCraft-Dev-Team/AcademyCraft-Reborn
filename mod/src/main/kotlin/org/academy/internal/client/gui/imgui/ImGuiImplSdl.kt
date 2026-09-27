package org.academy.internal.client.gui.imgui

import imgui.ImGui
import imgui.flag.ImGuiKey
import net.minecraft.client.Minecraft
import org.jetbrains.annotations.ApiStatus
import org.lwjgl.sdl.*
import org.lwjgl.system.MemoryStack

/**
 * 基于 SDL3 的 ImGui 平台输入后端，替代 26.3 已移除的 GLFW 后端。
 *
 * <p>事件由 [org.academy.mixin.client.MixinSDLEventHandler] 在 MC 消费 SDL 事件时转发到
 * [ImGuiUtilInternal.onSdlEvent]，保证只处理真正进入游戏循环的事件。窗口尺寸直接查询 SDL，
 * 因此独立桌面编辑器无需 Minecraft 实例也能复用本后端；鼠标抓取状态由
 * [mouseGrabbed] 提供，编辑器固定为 `false`。</p>
 */
@ApiStatus.Internal
class ImGuiImplSdl(
    private val mouseGrabbed: () -> Boolean = {
        runCatching { Minecraft.getInstance().mouseHandler.isMouseGrabbed }.getOrDefault(false)
    },
) : ImGuiInputBackend {
    private var windowHandle = 0L
    private var initialized = false
    private var lastFrameNanos = 0L

    private var mouseX = 0f
    private var mouseY = 0f
    private var haveMousePos = false

    private val io get() = ImGui.getIO()

    override fun init(windowHandle: Long) {
        if (initialized) return
        this.windowHandle = windowHandle
        this.lastFrameNanos = System.nanoTime()
        initialized = true
    }

    override fun newFrame() {
        if (!initialized) return
        var logicalWidth = 1
        var logicalHeight = 1
        var pixelWidth = 1
        var pixelHeight = 1
        MemoryStack.stackPush().use { stack ->
            val w = stack.mallocInt(1)
            val h = stack.mallocInt(1)
            if (SDLVideo.SDL_GetWindowSize(windowHandle, w, h)) {
                logicalWidth = w.get(0).coerceAtLeast(1)
                logicalHeight = h.get(0).coerceAtLeast(1)
            }
            w.clear()
            h.clear()
            if (SDLVideo.SDL_GetWindowSizeInPixels(windowHandle, w, h)) {
                pixelWidth = w.get(0)
                pixelHeight = h.get(0)
            }
        }
        io.setDisplaySize(logicalWidth.toFloat(), logicalHeight.toFloat())
        io.setDisplayFramebufferScale(
            pixelWidth.toFloat() / logicalWidth,
            pixelHeight.toFloat() / logicalHeight
        )

        val now = System.nanoTime()
        io.setDeltaTime(((now - lastFrameNanos) / 1_000_000_000.0).toFloat().coerceIn(1f / 1000f, 0.25f))
        lastFrameNanos = now

        val mod = SDLKeyboard.SDL_GetModState().toInt() and 0xFFFF
        io.addKeyEvent(ImGuiKey.ModShift, mod and SDLKeycode.SDL_KMOD_SHIFT != 0)
        io.addKeyEvent(ImGuiKey.ModCtrl, mod and SDLKeycode.SDL_KMOD_CTRL != 0)
        io.addKeyEvent(ImGuiKey.ModAlt, mod and SDLKeycode.SDL_KMOD_ALT != 0)
        io.addKeyEvent(ImGuiKey.ModSuper, mod and SDLKeycode.SDL_KMOD_GUI != 0)

        if (io.wantSetMousePos) {
            mouseX = io.mousePosX
            mouseY = io.mousePosY
        }
    }

    override fun shutdown() {
        if (!initialized) return
        windowHandle = 0L
        initialized = false
    }

    @Suppress("UNUSED_PARAMETER")
    override fun processEvent(event: SDL_Event) {
        if (!initialized) return
        when (event.type()) {
            SDL_EVENT_KEY_DOWN, SDL_EVENT_KEY_UP -> onKey(event)
            SDL_EVENT_TEXT_INPUT -> onText(event)
            SDL_EVENT_MOUSE_MOTION -> onMouseMotion(event)
            SDL_EVENT_MOUSE_BUTTON_DOWN, SDL_EVENT_MOUSE_BUTTON_UP -> onMouseButton(event)
            SDL_EVENT_MOUSE_WHEEL -> onMouseWheel(event)
            SDL_EVENT_WINDOW_FOCUS_GAINED, SDL_EVENT_WINDOW_FOCUS_LOST ->
                io.addFocusEvent(event.type() == SDL_EVENT_WINDOW_FOCUS_GAINED)
        }
    }

    private fun onKey(event: SDL_Event) {
        val key = event.key()
        val imguiKey = keyFromScancode(key.scancode())
        if (imguiKey == ImGuiKey.None) return
        val down = event.type() == SDL_EVENT_KEY_DOWN
        io.addKeyEvent(imguiKey, down)
        io.setKeyEventNativeData(imguiKey, key.key(), key.scancode())
    }

    private fun onText(event: SDL_Event) {
        val text = event.text().textString() ?: return
        if (text.isNotEmpty()) io.addInputCharactersUTF8(text)
    }

    private fun onMouseMotion(event: SDL_Event) {
        val motion = event.motion()
        val grabbed = mouseGrabbed()
        if (grabbed || !haveMousePos) {
            mouseX += motion.xrel()
            mouseY += motion.yrel()
        } else {
            mouseX = motion.x()
            mouseY = motion.y()
        }
        haveMousePos = true
        io.addMousePosEvent(mouseX, mouseY)
    }

    private fun onMouseButton(event: SDL_Event) {
        val button = event.button()
        val imguiButton = when (button.button().toInt()) {
            SDL_BUTTON_LEFT -> 0
            SDL_BUTTON_RIGHT -> 1
            SDL_BUTTON_MIDDLE -> 2
            SDL_BUTTON_X1 -> 3
            SDL_BUTTON_X2 -> 4
            else -> return
        }
        io.addMouseButtonEvent(imguiButton, event.type() == SDL_EVENT_MOUSE_BUTTON_DOWN)
    }

    private fun onMouseWheel(event: SDL_Event) {
        val wheel = event.wheel()
        io.addMouseWheelEvent(wheel.x(), wheel.y())
    }

    private fun keyFromScancode(scancode: Int): Int = when (scancode) {
        SDLScancode.SDL_SCANCODE_A -> ImGuiKey.A
        SDLScancode.SDL_SCANCODE_B -> ImGuiKey.B
        SDLScancode.SDL_SCANCODE_C -> ImGuiKey.C
        SDLScancode.SDL_SCANCODE_D -> ImGuiKey.D
        SDLScancode.SDL_SCANCODE_E -> ImGuiKey.E
        SDLScancode.SDL_SCANCODE_F -> ImGuiKey.F
        SDLScancode.SDL_SCANCODE_G -> ImGuiKey.G
        SDLScancode.SDL_SCANCODE_H -> ImGuiKey.H
        SDLScancode.SDL_SCANCODE_I -> ImGuiKey.I
        SDLScancode.SDL_SCANCODE_J -> ImGuiKey.J
        SDLScancode.SDL_SCANCODE_K -> ImGuiKey.K
        SDLScancode.SDL_SCANCODE_L -> ImGuiKey.L
        SDLScancode.SDL_SCANCODE_M -> ImGuiKey.M
        SDLScancode.SDL_SCANCODE_N -> ImGuiKey.N
        SDLScancode.SDL_SCANCODE_O -> ImGuiKey.O
        SDLScancode.SDL_SCANCODE_P -> ImGuiKey.P
        SDLScancode.SDL_SCANCODE_Q -> ImGuiKey.Q
        SDLScancode.SDL_SCANCODE_R -> ImGuiKey.R
        SDLScancode.SDL_SCANCODE_S -> ImGuiKey.S
        SDLScancode.SDL_SCANCODE_T -> ImGuiKey.T
        SDLScancode.SDL_SCANCODE_U -> ImGuiKey.U
        SDLScancode.SDL_SCANCODE_V -> ImGuiKey.V
        SDLScancode.SDL_SCANCODE_W -> ImGuiKey.W
        SDLScancode.SDL_SCANCODE_X -> ImGuiKey.X
        SDLScancode.SDL_SCANCODE_Y -> ImGuiKey.Y
        SDLScancode.SDL_SCANCODE_Z -> ImGuiKey.Z
        SDLScancode.SDL_SCANCODE_1 -> ImGuiKey._1
        SDLScancode.SDL_SCANCODE_2 -> ImGuiKey._2
        SDLScancode.SDL_SCANCODE_3 -> ImGuiKey._3
        SDLScancode.SDL_SCANCODE_4 -> ImGuiKey._4
        SDLScancode.SDL_SCANCODE_5 -> ImGuiKey._5
        SDLScancode.SDL_SCANCODE_6 -> ImGuiKey._6
        SDLScancode.SDL_SCANCODE_7 -> ImGuiKey._7
        SDLScancode.SDL_SCANCODE_8 -> ImGuiKey._8
        SDLScancode.SDL_SCANCODE_9 -> ImGuiKey._9
        SDLScancode.SDL_SCANCODE_0 -> ImGuiKey._0
        SDLScancode.SDL_SCANCODE_RETURN -> ImGuiKey.Enter
        SDLScancode.SDL_SCANCODE_ESCAPE -> ImGuiKey.Escape
        SDLScancode.SDL_SCANCODE_BACKSPACE -> ImGuiKey.Backspace
        SDLScancode.SDL_SCANCODE_TAB -> ImGuiKey.Tab
        SDLScancode.SDL_SCANCODE_SPACE -> ImGuiKey.Space
        SDLScancode.SDL_SCANCODE_MINUS -> ImGuiKey.Minus
        SDLScancode.SDL_SCANCODE_EQUALS -> ImGuiKey.Equal
        SDLScancode.SDL_SCANCODE_LEFTBRACKET -> ImGuiKey.LeftBracket
        SDLScancode.SDL_SCANCODE_RIGHTBRACKET -> ImGuiKey.RightBracket
        SDLScancode.SDL_SCANCODE_BACKSLASH -> ImGuiKey.Backslash
        SDLScancode.SDL_SCANCODE_SEMICOLON -> ImGuiKey.Semicolon
        SDLScancode.SDL_SCANCODE_APOSTROPHE -> ImGuiKey.Apostrophe
        SDLScancode.SDL_SCANCODE_GRAVE -> ImGuiKey.GraveAccent
        SDLScancode.SDL_SCANCODE_COMMA -> ImGuiKey.Comma
        SDLScancode.SDL_SCANCODE_PERIOD -> ImGuiKey.Period
        SDLScancode.SDL_SCANCODE_SLASH -> ImGuiKey.Slash
        SDLScancode.SDL_SCANCODE_CAPSLOCK -> ImGuiKey.CapsLock
        SDLScancode.SDL_SCANCODE_F1 -> ImGuiKey.F1
        SDLScancode.SDL_SCANCODE_F2 -> ImGuiKey.F2
        SDLScancode.SDL_SCANCODE_F3 -> ImGuiKey.F3
        SDLScancode.SDL_SCANCODE_F4 -> ImGuiKey.F4
        SDLScancode.SDL_SCANCODE_F5 -> ImGuiKey.F5
        SDLScancode.SDL_SCANCODE_F6 -> ImGuiKey.F6
        SDLScancode.SDL_SCANCODE_F7 -> ImGuiKey.F7
        SDLScancode.SDL_SCANCODE_F8 -> ImGuiKey.F8
        SDLScancode.SDL_SCANCODE_F9 -> ImGuiKey.F9
        SDLScancode.SDL_SCANCODE_F10 -> ImGuiKey.F10
        SDLScancode.SDL_SCANCODE_F11 -> ImGuiKey.F11
        SDLScancode.SDL_SCANCODE_F12 -> ImGuiKey.F12
        SDLScancode.SDL_SCANCODE_PRINTSCREEN -> ImGuiKey.PrintScreen
        SDLScancode.SDL_SCANCODE_SCROLLLOCK -> ImGuiKey.ScrollLock
        SDLScancode.SDL_SCANCODE_PAUSE -> ImGuiKey.Pause
        SDLScancode.SDL_SCANCODE_INSERT -> ImGuiKey.Insert
        SDLScancode.SDL_SCANCODE_HOME -> ImGuiKey.Home
        SDLScancode.SDL_SCANCODE_PAGEUP -> ImGuiKey.PageUp
        SDLScancode.SDL_SCANCODE_DELETE -> ImGuiKey.Delete
        SDLScancode.SDL_SCANCODE_END -> ImGuiKey.End
        SDLScancode.SDL_SCANCODE_PAGEDOWN -> ImGuiKey.PageDown
        SDLScancode.SDL_SCANCODE_RIGHT -> ImGuiKey.RightArrow
        SDLScancode.SDL_SCANCODE_LEFT -> ImGuiKey.LeftArrow
        SDLScancode.SDL_SCANCODE_DOWN -> ImGuiKey.DownArrow
        SDLScancode.SDL_SCANCODE_UP -> ImGuiKey.UpArrow
        SDLScancode.SDL_SCANCODE_NUMLOCKCLEAR -> ImGuiKey.NumLock
        SDLScancode.SDL_SCANCODE_KP_DIVIDE -> ImGuiKey.KeypadDivide
        SDLScancode.SDL_SCANCODE_KP_MULTIPLY -> ImGuiKey.KeypadMultiply
        SDLScancode.SDL_SCANCODE_KP_MINUS -> ImGuiKey.KeypadSubtract
        SDLScancode.SDL_SCANCODE_KP_PLUS -> ImGuiKey.KeypadAdd
        SDLScancode.SDL_SCANCODE_KP_ENTER -> ImGuiKey.KeypadEnter
        SDLScancode.SDL_SCANCODE_KP_1 -> ImGuiKey.Keypad1
        SDLScancode.SDL_SCANCODE_KP_2 -> ImGuiKey.Keypad2
        SDLScancode.SDL_SCANCODE_KP_3 -> ImGuiKey.Keypad3
        SDLScancode.SDL_SCANCODE_KP_4 -> ImGuiKey.Keypad4
        SDLScancode.SDL_SCANCODE_KP_5 -> ImGuiKey.Keypad5
        SDLScancode.SDL_SCANCODE_KP_6 -> ImGuiKey.Keypad6
        SDLScancode.SDL_SCANCODE_KP_7 -> ImGuiKey.Keypad7
        SDLScancode.SDL_SCANCODE_KP_8 -> ImGuiKey.Keypad8
        SDLScancode.SDL_SCANCODE_KP_9 -> ImGuiKey.Keypad9
        SDLScancode.SDL_SCANCODE_KP_0 -> ImGuiKey.Keypad0
        SDLScancode.SDL_SCANCODE_KP_PERIOD -> ImGuiKey.KeypadDecimal
        SDLScancode.SDL_SCANCODE_KP_EQUALS -> ImGuiKey.KeypadEqual
        SDLScancode.SDL_SCANCODE_APPLICATION -> ImGuiKey.Menu
        SDLScancode.SDL_SCANCODE_LCTRL -> ImGuiKey.LeftCtrl
        SDLScancode.SDL_SCANCODE_LSHIFT -> ImGuiKey.LeftShift
        SDLScancode.SDL_SCANCODE_LALT -> ImGuiKey.LeftAlt
        SDLScancode.SDL_SCANCODE_LGUI -> ImGuiKey.LeftSuper
        SDLScancode.SDL_SCANCODE_RCTRL -> ImGuiKey.RightCtrl
        SDLScancode.SDL_SCANCODE_RSHIFT -> ImGuiKey.RightShift
        SDLScancode.SDL_SCANCODE_RALT -> ImGuiKey.RightAlt
        SDLScancode.SDL_SCANCODE_RGUI -> ImGuiKey.RightSuper
        else -> ImGuiKey.None
    }

    private companion object {
        const val SDL_EVENT_KEY_DOWN = 0x300
        const val SDL_EVENT_KEY_UP = 0x301
        const val SDL_EVENT_TEXT_INPUT = 0x303
        const val SDL_EVENT_MOUSE_MOTION = 0x400
        const val SDL_EVENT_MOUSE_BUTTON_DOWN = 0x401
        const val SDL_EVENT_MOUSE_BUTTON_UP = 0x402
        const val SDL_EVENT_MOUSE_WHEEL = 0x403
        const val SDL_EVENT_WINDOW_FOCUS_GAINED = 0x20C
        const val SDL_EVENT_WINDOW_FOCUS_LOST = 0x20D

        const val SDL_BUTTON_LEFT = 1
        const val SDL_BUTTON_MIDDLE = 2
        const val SDL_BUTTON_RIGHT = 3
        const val SDL_BUTTON_X1 = 4
        const val SDL_BUTTON_X2 = 5
    }
}
