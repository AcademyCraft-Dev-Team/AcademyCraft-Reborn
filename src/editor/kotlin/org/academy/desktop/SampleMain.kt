package org.academy.desktop

import net.minecraft.resources.Identifier
import org.academy.api.client.gui.drawable.ColorDrawable
import org.academy.api.client.gui.layout.SizeMode
import org.academy.api.client.gui.serialize.WidgetSerializer
import org.academy.api.client.gui.widget.FrameLayoutWidget
import org.academy.api.client.gui.widget.Widget
import org.academy.api.client.gui.widget.WidgetContainer
import org.academy.desktop.platform.DesktopApplication
import org.academy.desktop.platform.DesktopEnvironment
import org.academy.desktop.platform.EditorApp
import java.nio.file.Path

/** Smoke test: boots the desktop platform and renders a bundled layout. */
class SampleApp : EditorApp {
    override var title = "AcademyCraft Desktop Sample"

    private lateinit var host: FrameLayoutWidget
    private var preview: Widget? = null

    override fun createRoot(): WidgetContainer {
        host = FrameLayoutWidget().apply {
            layoutParams = FrameLayoutWidget.LayoutParams().sizeMode(SizeMode.MATCH_PARENT)
            background = ColorDrawable(0xFF111111.toInt())
        }
        preview = try {
            WidgetSerializer.loadLayout(
                Identifier.fromNamespaceAndPath("academy", "ui/layout/location_teleport.json")
            )
        } catch (e: Exception) {
            println("Failed to load layout: ${e.message}")
            null
        }
        val loaded = preview
        if (loaded != null) host.addChild("preview", loaded)
        return host
    }
}

fun main(args: Array<String>) {
    val projectRoot = args.firstOrNull { it.startsWith("--project-root=") }
        ?.substringAfter('=')
        ?.let(Path::of)
        ?: Path.of("").toAbsolutePath()
    val environment = DesktopEnvironment(projectRoot, 1280, 720)
    DesktopApplication.run(SampleApp(), environment)
}
