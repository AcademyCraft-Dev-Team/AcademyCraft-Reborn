package org.academy.desktop.platform

import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.ConcurrentHashMap

/**
 * 图资产热重载（M21s）：由 [GraphEditorApp] 每帧调 [scanNow]（渲染线程，~300ms 节流），
 * 递归扫描给定目录下的 `.json` mtime，变化则回调 [onChanged]（宿主判断是否已打开并就地重载）。
 * 与 ShaderHotReload 同为 mtime 轮询，简单可靠。
 */
class GraphHotReload(
    private val dirs: () -> List<Path>,
    private val onChanged: (Path) -> Unit,
    private val throttleNanos: Long = 300_000_000L,
) {
    private val mtimes = ConcurrentHashMap<Path, Long>()
    private var lastScanNanos = 0L

    fun scanNow() {
        val now = System.nanoTime()
        if (now - lastScanNanos < throttleNanos) return
        lastScanNanos = now
        for (dir in dirs()) {
            scanDir(dir)
        }
    }

    /** 本编辑器写入后确认 mtime，避免自己保存触发重载。 */
    fun acknowledge(file: Path) {
        try {
            mtimes[file.toAbsolutePath()] = Files.getLastModifiedTime(file).toMillis()
        } catch (_: Exception) {
        }
    }

    private fun scanDir(dir: Path) {
        if (!Files.isDirectory(dir)) return
        try {
            Files.list(dir).use { stream ->
                stream.filter { p ->
                    val n = p.fileName.toString()
                    n.endsWith(".json") && !n.endsWith(".editor.json")
                }.forEach { p ->
                    val m = Files.getLastModifiedTime(p).toMillis()
                    val key = p.toAbsolutePath()
                    val prev = mtimes[key]
                    if (prev == null) {
                        // 首次见到该文件：仅记录 mtime，不触发（避免启动即重载）
                        mtimes[key] = m
                    } else if (prev != m) {
                        mtimes[key] = m
                        onChanged(p)
                    }
                }
            }
        } catch (_: Exception) {
        }
    }

    fun close() {
        mtimes.clear()
    }
}
