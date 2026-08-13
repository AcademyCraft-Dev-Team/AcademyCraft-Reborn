package org.academy.desktop.hudeditor

import org.academy.desktop.platform.DesktopApplication
import org.academy.desktop.platform.DesktopEnvironment
import java.nio.file.Path

/**
 * Out-of-game HUD region layout editor entry point, launched via the FML
 * [org.academy.desktop.launch.EditorEntrypoint].
 */
fun main(args: Array<String>) {
    val projectRoot = args.firstOrNull { it.startsWith("--project-root=") }
        ?.substringAfter('=')
        ?.let(Path::of)
        ?: Path.of("").toAbsolutePath()
    val guiScale = args.firstOrNull { it.startsWith("--gui-scale=") }
        ?.substringAfter('=')
        ?.toFloatOrNull()
        ?.coerceIn(0.5f, 4f)
        ?: 2f
    val environment = DesktopEnvironment(projectRoot, 1600, 900, guiScale)
    DesktopApplication.run(HudEditorApp(environment), environment)
}
