package org.academy.internal.client.gui.debug

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path

class UiDebugFilePublisherTest {
    @TempDir
    lateinit var tempDir: Path

    @Test
    fun `batch publish writes files and keeps backups`() {
        val target = Files.createDirectories(tempDir.resolve("target"))
        val backup = Files.createDirectories(tempDir.resolve("backup"))
        Files.writeString(target.resolve("existing.json"), "old\n")

        UiDebugFilePublisher.writeBatch(
            target,
            linkedMapOf("existing.json" to "new\n", "added.json" to "added\n"),
            backup,
            "working-"
        )

        assertEquals("new\n", Files.readString(target.resolve("existing.json")))
        assertEquals("added\n", Files.readString(target.resolve("added.json")))
        assertEquals("old\n", Files.readString(backup.resolve("working-existing.json")))
    }

    @Test
    fun `failed replacement restores files already replaced`() {
        val target = Files.createDirectories(tempDir.resolve("target"))
        val backup = Files.createDirectories(tempDir.resolve("backup"))
        Files.writeString(target.resolve("first.json"), "old\n")
        Files.createDirectories(target.resolve("blocked.json"))

        assertThrows(Exception::class.java) {
            UiDebugFilePublisher.writeBatch(
                target,
                linkedMapOf("first.json" to "new\n", "blocked.json" to "blocked\n"),
                backup,
                "source-"
            )
        }

        assertEquals("old\n", Files.readString(target.resolve("first.json")))
        assertFalse(Files.list(target).use { paths -> paths.anyMatch { it.fileName.toString().endsWith(".tmp") } })
    }
}
