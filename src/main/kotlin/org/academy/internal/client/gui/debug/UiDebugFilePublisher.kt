package org.academy.internal.client.gui.debug

import org.academy.AcademyCraft
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption

internal object UiDebugFilePublisher {
    private val logger = AcademyCraft.getLogger()

    fun writeBatch(
        directory: Path,
        files: Map<String, String>,
        backupDirectory: Path,
        backupPrefix: String
    ) {
        if (files.isEmpty()) return
        Files.createDirectories(directory)
        Files.createDirectories(backupDirectory)
        val existed = files.keys.associateWith { Files.isRegularFile(directory.resolve(it)) }
        val temporaryFiles = linkedMapOf<String, Path>()
        val replaced = mutableListOf<String>()
        try {
            for (name in files.keys) {
                val target = directory.resolve(name)
                if (existed[name] == true) {
                    Files.copy(
                        target,
                        backupDirectory.resolve("$backupPrefix$name"),
                        StandardCopyOption.REPLACE_EXISTING
                    )
                }
            }
            for ((name, contents) in files) {
                val temp = Files.createTempFile(directory, name, ".tmp")
                Files.writeString(temp, contents)
                temporaryFiles[name] = temp
            }
            for ((name, temp) in temporaryFiles) {
                val target = directory.resolve(name)
                try {
                    Files.move(temp, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
                } catch (_: AtomicMoveNotSupportedException) {
                    Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING)
                }
                replaced.add(name)
            }
        } catch (exception: Exception) {
            restoreBatch(directory, backupDirectory, backupPrefix, existed, replaced)
            throw exception
        } finally {
            temporaryFiles.values.forEach { temp ->
                runCatching { Files.deleteIfExists(temp) }
                    .onFailure { logger.warn("[UiDebug] Failed to delete temporary file {}", temp, it) }
            }
        }
    }

    private fun restoreBatch(
        directory: Path,
        backupDirectory: Path,
        backupPrefix: String,
        existed: Map<String, Boolean>,
        replaced: List<String>
    ) {
        for (name in replaced.asReversed()) {
            val target = directory.resolve(name)
            val backup = backupDirectory.resolve("$backupPrefix$name")
            runCatching {
                if (existed[name] == true && Files.isRegularFile(backup)) {
                    Files.copy(backup, target, StandardCopyOption.REPLACE_EXISTING)
                } else if (existed[name] != true) {
                    Files.deleteIfExists(target)
                }
            }.onFailure { logger.error("[UiDebug] Failed to roll back {}", target, it) }
        }
    }
}
