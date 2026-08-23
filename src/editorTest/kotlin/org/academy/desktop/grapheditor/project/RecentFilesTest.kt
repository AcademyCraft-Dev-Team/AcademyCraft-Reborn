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
        assertEquals(listOf("/c/g.json", "/b/g.json", "/a/g.json"), recent.recent())
    }

    @Test
    fun reAddMovesToFront() {
        val file = tempFile("recent.json")
        val recent = RecentFiles(file, 4)
        recent.add(Path.of("/a/g.json"))
        recent.add(Path.of("/b/g.json"))
        recent.add(Path.of("/a/g.json"))
        assertEquals(listOf("/a/g.json", "/b/g.json"), recent.recent())
    }

    @Test
    fun trimsOldestBeyondMax() {
        val file = tempFile("recent.json")
        val recent = RecentFiles(file, 3)
        recent.add(Path.of("/a/g.json"))
        recent.add(Path.of("/b/g.json"))
        recent.add(Path.of("/c/g.json"))
        recent.add(Path.of("/d/g.json"))
        assertEquals(listOf("/d/g.json", "/c/g.json", "/b/g.json"), recent.recent())
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
        assertEquals(listOf("/b/g.json"), recent.recent())
        recent.clear()
        assertTrue(recent.recent().isEmpty())
    }

    private fun tempFile(name: String): Path {
        val dir = Files.createTempDirectory("recent-test")
        return dir.resolve(name)
    }
}
