package org.academy.desktop.platform

import com.mojang.blaze3d.systems.RenderSystem
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.ConcurrentHashMap

/**
 * 编辑器着色器热重载（M21r）：由 [GraphEditorApp] 每帧在渲染线程调 [scanNow]，
 * 递归统计项目 `assets/academy/shaders` 下 `.fsh/.vsh/.glsl` 的 mtime，任一变化则调
 * `GpuDevice.clearPipelineCache()` —— 设备级 shader/pipeline 缓存清空 → 下一帧
 * 从源目录（`ClasspathShaderSource.sourceDir`）重新编译，保存即生效。
 *
 * 相比 WatchService：mtime 轮询简单可靠，300ms 节流避免每帧全量 stat。
 */
class ShaderHotReload(private val root: Path) {
    private val mtimes = ConcurrentHashMap<Path, Long>()
    private var lastScanNanos = 0L

    /** 渲染线程调用：扫描着色器目录，有变更则清设备缓存。 */
    fun scanNow() {
        val now = System.nanoTime()
        if (now - lastScanNanos < 300_000_000L) return
        lastScanNanos = now
        if (!Files.isDirectory(root)) return

        val snapshot = HashMap<Path, Long>()
        val changed = scan(root, snapshot)
        mtimes.clear()
        mtimes.putAll(snapshot)
        if (changed) {
            try {
                RenderSystem.getDevice().clearPipelineCache()
                println("[shader-hot-reload] shaders changed, pipeline cache cleared")
            } catch (_: Exception) {
            }
        }
    }

    private fun scan(dir: Path, out: MutableMap<Path, Long>): Boolean {
        var changed = false
        try {
            Files.list(dir).use { stream ->
                stream.forEach { p ->
                    if (Files.isDirectory(p)) {
                        changed = scan(p, out) || changed
                    } else {
                        val name = p.fileName.toString()
                        if (name.endsWith(".fsh") || name.endsWith(".vsh") || name.endsWith(".glsl")) {
                            val m = Files.getLastModifiedTime(p).toMillis()
                            out[p] = m
                            if (mtimes[p] != m) changed = true
                        }
                    }
                }
            }
        } catch (_: Exception) {
        }
        return changed
    }

    fun close() {
        mtimes.clear()
    }
}
