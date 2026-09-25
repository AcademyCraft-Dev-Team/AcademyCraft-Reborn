package org.academy.desktop.grapheditor.project

import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.nio.file.Files
import java.nio.file.Path
import java.util.*

/**
 * 最近文件 LRU 列表，持久化到本地 JSON（新增在前，淘汰最旧）。
 */
class RecentFiles(
    private val persistenceFile: Path,
    private val maxEntries: Int = 8,
) {
    private val entries = ArrayDeque<String>()
    private val gson = Gson()

    fun recent(): List<String> = entries.toList()

    fun add(path: Path) {
        val key = path.toAbsolutePath().normalize().toString()
        entries.remove(key)
        entries.addFirst(key)
        while (entries.size > maxEntries) entries.removeLast()
        save()
    }

    fun remove(path: Path) {
        val key = path.toAbsolutePath().normalize().toString()
        if (entries.remove(key)) save()
    }

    fun clear() {
        entries.clear()
        save()
    }

    fun load() {
        entries.clear()
        if (!Files.isRegularFile(persistenceFile)) return
        try {
            val text = Files.readString(persistenceFile)
            val list =
                gson.fromJson<List<String>>(text, TypeToken.getParameterized(List::class.java, String::class.java).type)
            list?.take(maxEntries)?.forEach { entries.addLast(it) }
        } catch (_: Exception) {
            entries.clear()
        }
    }

    fun save() {
        try {
            Files.createDirectories(persistenceFile.parent)
            Files.writeString(persistenceFile, gson.toJson(entries.toList()))
        } catch (_: Exception) {
            // 忽略持久化失败
        }
    }
}
