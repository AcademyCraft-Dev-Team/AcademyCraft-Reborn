package org.academy.desktop.platform

import com.mojang.blaze3d.pipeline.RenderTarget
import org.academy.api.client.gui.widget.WidgetContainer

/**
 * Contract for any out-of-game desktop editor hosted by [DesktopApplication].
 * A future editor (skill tree, HUD, models, ...) only needs to implement this
 * interface and register a run task pointing at its main class.
 */
interface EditorApp {
    /** Window title shown in the title bar. Apps may mutate it while running. */
    var title: String

    /** Builds the root widget tree (framework widgets) shown in the window. */
    fun createRoot(): WidgetContainer

    /**
     * Whether this app uses ImGui. When true, the host initializes and renders an
     * [org.academy.internal.client.gui.imgui.ImGuiBackend] and invokes [renderImGui].
     */
    val usesImGui: Boolean get() = false

    /** Called once per frame on the main (render) thread before rendering. */
    fun onFrame(partialTick: Float) {
    }

    /**
     * Called after the widget tree renders and before ImGui, to render app-owned
     * content (e.g. a shader preview) directly into [target].
     */
    fun renderBackground(target: RenderTarget) {
    }

    /** Called inside the ImGui frame when [usesImGui] is true. */
    fun renderImGui() {
    }

    /** Called when the window's GUI-scaled size changes. */
    fun onResize(guiScaledWidth: Int, guiScaledHeight: Int) {
    }

    /**
     * Global key hook invoked before the key event reaches the widget tree.
     * Return true to consume (handled as a shortcut, e.g. Ctrl+Z).
     *
     * [key]/[action]/[modifiers] use the raw GLFW key codes and modifier bits.
     */
    fun onKey(key: Int, action: Int, modifiers: Int): Boolean = false

    /** Return true to request the application loop to shut down (menu Quit). */
    fun quitRequested(): Boolean = false

    /** Called on shutdown to release app-owned resources. */
    fun onDispose() {
    }
}
