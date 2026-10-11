package org.academy.desktop.platform

import org.academy.api.client.gui.environment.UiEnvironment
import org.academy.api.client.gui.widget.FrameLayoutWidget
import org.academy.api.client.gui.widget.WidgetContainer
import org.junit.jupiter.api.Assertions.assertDoesNotThrow
import org.junit.jupiter.api.Test
import java.nio.file.Path

class DesktopUiHostBootstrapTest {
    @Test
    fun windowHandlerCanBeConstructedAndClosedBeforeGpuDeviceInitialization() {
        val previous = UiEnvironment.get()
        val environment = DesktopEnvironment(Path.of("."), 1280, 720)
        UiEnvironment.set(environment)
        try {
            val app = object : EditorApp {
                override var title = "Bootstrap regression"
                override fun createRoot(): WidgetContainer = FrameLayoutWidget()
            }
            assertDoesNotThrow {
                // DesktopApplication needs this handler to create the window which provides its GPU device.
                val host = DesktopUiHost(app, environment)
                host.close()
            }
        } finally {
            UiEnvironment.set(previous)
        }
    }
}
