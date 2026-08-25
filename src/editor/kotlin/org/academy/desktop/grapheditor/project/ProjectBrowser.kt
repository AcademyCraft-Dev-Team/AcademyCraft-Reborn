package org.academy.desktop.grapheditor.project

import imgui.ImGui
import java.nio.file.Files
import java.nio.file.Path
import java.util.stream.Stream

/**
 * 项目/资产浏览器：列出可编辑图目录（run/academy/graphs）与额外的打包资产根目录
 * （如 src/main/resources/assets/academy/vfxgraph）下的 .json 图，双击打开 + 刷新。
 */
class ProjectBrowser(
    private val graphDir: () -> Path,
    private val onOpen: (Path) -> Unit,
    private val extraRoots: () -> List<Path> = { emptyList() },
) {
    private var files: List<Path> = emptyList()
    private var packagedFiles: List<Path> = emptyList()
    private var refreshTick = 0L

    /** 最近一次 refresh 的顶层图资产列表（供测试/预览）。 */
    val listedFiles: List<Path> get() = files

    /** 最近一次 refresh 的打包资产列表（共享 main resources，M21）。 */
    val packagedAssets: List<Path> get() = packagedFiles

    fun refresh() {
        files = listJson(graphDir())
        packagedFiles = extraRoots().flatMap { listJson(it) }.distinct()
        refreshTick = System.currentTimeMillis()
    }

    private fun listJson(dir: Path): List<Path> = try {
        if (!Files.isDirectory(dir)) {
            emptyList()
        } else {
            Files.list(dir).use { stream: Stream<Path> ->
                stream.filter { Files.isRegularFile(it) && it.fileName.toString().endsWith(".json") }
                    .sorted()
                    .toList()
            }
        }
    } catch (_: Exception) {
        emptyList()
    }

    fun render() {
        if (System.currentTimeMillis() - refreshTick > 1000) refresh()
        ImGui.text("Project")
        ImGui.sameLine()
        if (ImGui.button("Refresh##project")) refresh()

        ImGui.separator()
        if (files.isEmpty()) {
            ImGui.textDisabled("No graph assets")
        } else {
            for (file in files) {
                renderSelectable(file)
            }
        }
        if (packagedFiles.isNotEmpty()) {
            ImGui.separator()
            ImGui.textDisabled("Packaged Assets")
            for (file in packagedFiles) {
                renderSelectable(file)
            }
        }
    }

    private fun renderSelectable(file: Path) {
        val name = file.fileName.toString()
        if (ImGui.selectable(name, false)) {
            onOpen(file)
        }
        if (ImGui.isItemHovered() && ImGui.isMouseDoubleClicked(0)) {
            onOpen(file)
        }
    }
}
