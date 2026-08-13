package org.academy.desktop.platform

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

    /** Called once per frame on the main (render) thread before rendering. */
    fun onFrame(partialTick: Float) {
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
