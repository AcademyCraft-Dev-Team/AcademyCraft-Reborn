package org.academy.desktop.grapheditor.project

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.nio.file.Files
import java.nio.file.Path

class RecentFilesTest {

    @Test
    fun addInsertsNewestFirst() {
        val file = tempFile("recent.json")
        val recent = RecentFiles(file, 4)
        recent.add(Path.of("/a/g.json"))
        recent.add(Path.of("/b/g.json"))
        recent.add(Path.of("/c/g.json"))
        assertEquals(listOf(key("/c/g.json"), key("/b/g.json"), key("/a/g.json")), recent.recent())
    }

    @Test
    fun reAddMovesToFront() {
        val file = tempFile("recent.json")
        val recent = RecentFiles(file, 4)
        recent.add(Path.of("/a/g.json"))
        recent.add(Path.of("/b/g.json"))
        recent.add(Path.of("/a/g.json"))
        assertEquals(listOf(key("/a/g.json"), key("/b/g.json")), recent.recent())
    }

    @Test
    fun trimsOldestBeyondMax() {
        val file = tempFile("recent.json")
        val recent = RecentFiles(file, 3)
        recent.add(Path.of("/a/g.json"))
        recent.add(Path.of("/b/g.json"))
        recent.add(Path.of("/c/g.json"))
        recent.add(Path.of("/d/g.json"))
        assertEquals(listOf(key("/d/g.json"), key("/c/g.json"), key("/b/g.json")), recent.recent())
    }

    @Test
    fun persistsAcrossInstances() {
        val file = tempFile("recent.json")
        val first = RecentFiles(file, 4)
        first.add(Path.of("/a/g.json"))
        first.add(Path.of("/b/g.json"))

        val second = RecentFiles(file, 4)
        second.load()
        assertEquals(first.recent(), second.recent())
    }

    @Test
    fun removeAndClear() {
        val file = tempFile("recent.json")
        val recent = RecentFiles(file, 4)
        recent.add(Path.of("/a/g.json"))
        recent.add(Path.of("/b/g.json"))
        recent.remove(Path.of("/a/g.json"))
        assertEquals(listOf(key("/b/g.json")), recent.recent())
        recent.clear()
        assertTrue(recent.recent().isEmpty())
    }

    private fun tempFile(name: String): Path {
        val dir = Files.createTempDirectory("recent-test")
        return dir.resolve(name)
    }

    private fun key(path: String): String = Path.of(path).toAbsolutePath().normalize().toString()
}
