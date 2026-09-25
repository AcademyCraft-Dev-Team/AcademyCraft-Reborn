package org.academy.desktop.grapheditor.project

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.nio.file.Files
import java.nio.file.Path

class ProjectBrowserTest {

    @Test
    fun listsTopLevelJsonOnly() {
        val dir = Files.createTempDirectory("project-test")
        Files.writeString(dir.resolve("a.json"), "{}")
        Files.writeString(dir.resolve("b.json"), "{}")
        Files.writeString(dir.resolve("note.txt"), "x")
        Files.createDirectories(dir.resolve("sub"))
        Files.writeString(dir.resolve("sub/c.json"), "{}")

        val browser = ProjectBrowser({ dir }, {})
        browser.refresh()

        val names = browser.listedFiles.map { it.fileName.toString() }
        assertEquals(setOf("a.json", "b.json"), names.toSet())
    }

    @Test
    fun missingDirectoryYieldsEmpty() {
        val browser = ProjectBrowser({ Path.of("/nonexistent-dir-xyz") }, {})
        browser.refresh()
        assertTrue(browser.listedFiles.isEmpty())
        assertTrue(browser.packagedAssets.isEmpty())
    }

    @Test
    fun listsPackagedAssetsFromExtraRoots() {
        val dir = Files.createTempDirectory("project-local")
        Files.writeString(dir.resolve("local.json"), "{}")
        val pkg = Files.createTempDirectory("project-packaged")
        Files.writeString(pkg.resolve("demo_fire.json"), "{}")
        Files.writeString(pkg.resolve("demo_burst.json"), "{}")
        Files.writeString(pkg.resolve("note.txt"), "x")

        val browser = ProjectBrowser({ dir }, {}, { listOf(pkg) })
        browser.refresh()

        assertEquals(setOf("local.json"), browser.listedFiles.map { it.fileName.toString() }.toSet())
        assertEquals(
            setOf("demo_fire.json", "demo_burst.json"),
            browser.packagedAssets.map { it.fileName.toString() }.toSet()
        )
    }
}
