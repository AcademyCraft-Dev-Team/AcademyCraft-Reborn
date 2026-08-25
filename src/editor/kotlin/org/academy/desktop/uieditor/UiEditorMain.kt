package org.academy.desktop.uieditor

import org.academy.api.client.gui.environment.UiEnvironment
import org.academy.desktop.platform.DesktopApplication
import org.academy.desktop.platform.DesktopEnvironment
import java.nio.file.Path

/**
 * Out-of-game UI layout editor entry point, launched via the FML
 * [org.academy.desktop.launch.EditorEntrypoint].
 *
 * Args: `--project-root=<path>` (default: repo root), `--layout=<name>`
 * (default: `location_teleport`) and `--gui-scale=<1..4>` (default: 2).
 */
fun main(args: Array<String>) {
    val projectRoot = args.firstOrNull { it.startsWith("--project-root=") }
        ?.substringAfter('=')
        ?.let(Path::of)
        ?: Path.of("").toAbsolutePath()
    val layout = args.firstOrNull { it.startsWith("--layout=") }
        ?.substringAfter('=')
        ?: "location_teleport"
    val guiScale = args.firstOrNull { it.startsWith("--gui-scale=") }
        ?.substringAfter('=')
        ?.toFloatOrNull()
        ?.coerceIn(0.5f, 4f)
        ?: 2f
    args.firstOrNull { it.startsWith("--dump-png=") }?.substringAfter('=')?.let {
        System.setProperty("academy.desktop.dumpPng", it)
    }
    val environment = DesktopEnvironment(projectRoot, 1600, 900, guiScale)
    UiEnvironment.set(environment)
    DesktopApplication.run(UiEditorApp(environment, layout), environment)
}
