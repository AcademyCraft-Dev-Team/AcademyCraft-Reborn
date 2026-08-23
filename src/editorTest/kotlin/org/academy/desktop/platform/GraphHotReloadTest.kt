package org.academy.desktop.platform

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.nio.file.Files
import java.nio.file.Path

class GraphHotReloadTest {
    @Test
    fun detectsModifiedJsonAndCallsBack() {
        val dir = Files.createTempDirectory("graph-hotreload")
        val file = dir.resolve("demo.json")
        Files.writeString(file, "{}")

        val changed = mutableListOf<Path>()
        val reload = GraphHotReload({ listOf(dir) }, { changed.add(it) }, throttleNanos = 0L)

        // 首次扫描只初始化，不回调
        reload.scanNow()
        assertEquals(0, changed.size)

        // 修改后扫描 → 回调一次
        Files.writeString(file, "{ \"x\": 1 }")
        reload.scanNow()
        assertEquals(1, changed.size)
        assertEquals(file.toAbsolutePath(), changed[0].toAbsolutePath())

        // 未再修改 → 不再回调
        reload.scanNow()
        assertEquals(1, changed.size)

        // acknowledge 后即使修改也不回调（模拟编辑器自身保存）
        Files.writeString(file, "{ \"x\": 2 }")
        reload.acknowledge(file)
        reload.scanNow()
        assertEquals(1, changed.size)
    }
}
