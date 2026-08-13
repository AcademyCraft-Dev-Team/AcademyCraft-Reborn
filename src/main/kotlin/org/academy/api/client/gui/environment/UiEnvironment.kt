package org.academy.api.client.gui.environment

import com.mojang.blaze3d.textures.GpuTextureView
import net.minecraft.resources.Identifier
import java.io.InputStream
import java.nio.file.Path

/**
 * Environment abstraction for the UI framework's runtime dependencies on Minecraft.
 *
 * The in-game default is [MinecraftUiEnvironment], which delegates to
 * [net.minecraft.client.Minecraft]. Out-of-game desktop tools (see the `editor`
 * source set) install their own implementation before booting the UI framework,
 * so the same widget/layout/render/serialize stack runs standalone on a bare
 * Blaze3D window.
 */
interface UiEnvironment {
    /** Logical (GUI-scaled) viewport width used for measure/layout. */
    val guiScaledWidth: Int

    /** Logical (GUI-scaled) viewport height used for measure/layout. */
    val guiScaledHeight: Int

    /** GUI scale factor (physical pixels per GUI unit). */
    val guiScale: Float

    /** Physical (framebuffer) width in pixels. */
    val physicalWidth: Int

    /** Physical (framebuffer) height in pixels. */
    val physicalHeight: Int

    /** Game / working directory. */
    val gameDirectory: Path

    /** Runs [task] on the main (game) thread. */
    fun runOnMainThread(task: Runnable)

    /** Frame delta in game ticks (roughly 1.0 per tick at real-time speed). */
    fun frameDeltaTicks(): Float

    /** Opens a bundled resource like `assets/<namespace>/<path>`, or null if missing. */
    fun openResource(namespace: String, path: String): InputStream?

    /** Resolves a texture by identifier to its GPU view. */
    fun loadTexture(identifier: Identifier): GpuTextureView

    /** Reads the clipboard text (empty string when unavailable). */
    fun clipboard(): String = ""

    /** Writes [text] to the clipboard (no-op when unavailable). */
    fun setClipboard(text: String) {
    }

    /** Notification that a text input gained/lost keyboard focus (IME). */
    fun textInputFocusChanged(focused: Boolean) {
    }

    /** Writable directory for layout overrides, `<gameDir>/academy/ui` by default. */
    fun layoutDir(): Path

    companion object {
        @Volatile
        private var delegate: UiEnvironment = MinecraftUiEnvironment()

        @JvmStatic
        fun get(): UiEnvironment = delegate

        @JvmStatic
        fun set(environment: UiEnvironment) {
            delegate = environment
        }
    }
}
