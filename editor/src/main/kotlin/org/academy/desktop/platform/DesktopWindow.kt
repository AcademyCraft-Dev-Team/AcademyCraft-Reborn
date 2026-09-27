package org.academy.desktop.platform

import com.mojang.renderpearl.api.device.GpuBackend
import org.lwjgl.sdl.SDLError
import org.lwjgl.sdl.SDLVideo
import org.lwjgl.sdl.SDL_Event
import org.lwjgl.system.MemoryStack
import org.slf4j.Logger
import org.slf4j.LoggerFactory

/**
 * 编辑器自有的 SDL 窗口。
 *
 * 不能复用 `com.mojang.blaze3d.platform.Window`：它的 `onResize`/`onIconified` 会解引用
 * `Minecraft.getInstance()`，而桌面编辑器进程里没有 `Minecraft` 实例，收到窗口事件即 NPE。
 * 这里只实现编辑器所需的状态（尺寸 / framebuffer / 关闭 / 最小化 / 焦点）。
 */
class DesktopWindow(
    backend: GpuBackend,
    title: String,
    initialWidth: Int,
    initialHeight: Int,
) : AutoCloseable {
    val handle: Long

    /** 逻辑（窗口）尺寸。 */
    var width: Int = 0
        private set
    var height: Int = 0
        private set

    /** framebuffer（像素）尺寸，用于渲染目标与 surface。 */
    var framebufferWidth: Int = 0
        private set
    var framebufferHeight: Int = 0
        private set

    var shouldClose: Boolean = false
        private set
    var isIconified: Boolean = false
        private set
    var isFocused: Boolean = true
        private set

    init {
        width = maxOf(initialWidth, MIN_WIDTH)
        height = maxOf(initialHeight, MIN_HEIGHT)
        handle = backend.createWindow(title, width, height, FLAGS)
        if (handle == 0L) {
            throw IllegalStateException("Failed to create window: ${SDLError.SDL_GetError()}")
        }
        SDLVideo.SDL_SetWindowMinimumSize(handle, MIN_WIDTH, MIN_HEIGHT)
        shouldClose = false
        isIconified = false
        isFocused = true
        refreshFramebufferSize()
        LOGGER.info("Created window using SDL video driver: {}", SDLVideo.SDL_GetCurrentVideoDriver())
    }

    fun setTitle(title: String) {
        SDLVideo.SDL_SetWindowTitle(handle, title)
    }

    /**
     * 处理一个 SDL 事件，返回 true 表示 framebuffer 尺寸发生变化，宿主需重建渲染目标 / surface。
     */
    fun handleEvent(event: SDL_Event): Boolean {
        when (event.type()) {
            SDL_EVENT_QUIT, SDL_EVENT_TERMINATING, SDL_EVENT_WINDOW_CLOSE_REQUESTED -> shouldClose = true
            SDL_EVENT_WINDOW_RESIZED -> {
                width = event.window().data1()
                height = event.window().data2()
            }

            SDL_EVENT_WINDOW_PIXEL_SIZE_CHANGED -> {
                val w = event.window().data1()
                val h = event.window().data2()
                if (w > 0 && h > 0) {
                    framebufferWidth = w
                    framebufferHeight = h
                    return true
                }
            }

            SDL_EVENT_WINDOW_MINIMIZED -> isIconified = true
            SDL_EVENT_WINDOW_RESTORED, SDL_EVENT_WINDOW_MAXIMIZED -> isIconified = false
            SDL_EVENT_WINDOW_FOCUS_GAINED -> isFocused = true
            SDL_EVENT_WINDOW_FOCUS_LOST -> isFocused = false
            SDL_EVENT_WINDOW_DISPLAY_SCALE_CHANGED -> {
                refreshFramebufferSize()
                return true
            }
        }
        return false
    }

    fun refreshFramebufferSize() {
        MemoryStack.stackPush().use { stack ->
            val outWidth = stack.mallocInt(1)
            val outHeight = stack.mallocInt(1)
            if (!SDLVideo.SDL_GetWindowSizeInPixels(handle, outWidth, outHeight)) {
                throw IllegalStateException("Failed to query window size in pixels: ${SDLError.SDL_GetError()}")
            }
            framebufferWidth = maxOf(outWidth.get(0), 1)
            framebufferHeight = maxOf(outHeight.get(0), 1)
        }
    }

    override fun close() {
        SDLVideo.SDL_DestroyWindow(handle)
    }

    private companion object {
        private val LOGGER: Logger = LoggerFactory.getLogger(DesktopWindow::class.java)

        const val MIN_WIDTH = 320
        const val MIN_HEIGHT = 240

        /** SDL_WINDOW_RESIZABLE | SDL_WINDOW_HIGH_PIXEL_DENSITY. */
        const val FLAGS = 8224L

        const val SDL_EVENT_QUIT = 0x100
        const val SDL_EVENT_TERMINATING = 0x210
        const val SDL_EVENT_WINDOW_CLOSE_REQUESTED = 0x101
        const val SDL_EVENT_WINDOW_RESIZED = 0x206
        const val SDL_EVENT_WINDOW_PIXEL_SIZE_CHANGED = 0x207
        const val SDL_EVENT_WINDOW_MINIMIZED = 0x209
        const val SDL_EVENT_WINDOW_MAXIMIZED = 0x20A
        const val SDL_EVENT_WINDOW_RESTORED = 0x20B
        const val SDL_EVENT_WINDOW_FOCUS_GAINED = 0x20E
        const val SDL_EVENT_WINDOW_FOCUS_LOST = 0x20F
        const val SDL_EVENT_WINDOW_DISPLAY_SCALE_CHANGED = 0x213
    }
}
