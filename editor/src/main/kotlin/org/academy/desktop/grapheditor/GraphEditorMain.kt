package org.academy.desktop.grapheditor

import org.academy.api.client.gui.environment.UiEnvironment
import org.academy.desktop.grapheditor.app.GraphEditorApp
import org.academy.desktop.platform.DesktopApplication
import org.academy.desktop.platform.DesktopEnvironment
import java.nio.file.Path

/**
 * 桌面 Shader Graph 编辑器入口。Args: `--project-root=<path>`（默认仓库根）、`--gui-scale=<1..4>`。
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
        ?: 1f

    val environment = DesktopEnvironment(projectRoot, 1600, 900, guiScale)
    UiEnvironment.set(environment)
    DesktopApplication.run(GraphEditorApp(environment), environment)
}
