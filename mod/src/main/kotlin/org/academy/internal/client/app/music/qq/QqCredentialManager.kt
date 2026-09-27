package org.academy.internal.client.app.music.qq

import com.google.gson.Gson
import com.google.gson.GsonBuilder
import net.neoforged.fml.loading.FMLPaths
import org.academy.AcademyCraft
import java.io.IOException
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path

object QqCredentialManager {
    private val GSON: Gson = GsonBuilder().setPrettyPrinting().create()
    private val DIRECTORY: Path by lazy { FMLPaths.CONFIGDIR.get().resolve("academy").resolve("music") }
    private val CREDENTIAL_FILE: Path by lazy { DIRECTORY.resolve("qq_credential.json") }

    @Volatile
    private var credential: QqCredential? = null

    @Volatile
    private var initialized = false

    @Synchronized
    fun init() {
        if (initialized) {
            return
        }
        try {
            Files.createDirectories(DIRECTORY)
        } catch (e: IOException) {
            AcademyCraft.LOGGER.error("Failed to create QQ music credential directory", e)
        }
        load()
        initialized = true
    }

    @Synchronized
    fun load() {
        if (!Files.exists(CREDENTIAL_FILE)) {
            credential = null
            return
        }
        try {
            Files.newBufferedReader(CREDENTIAL_FILE, StandardCharsets.UTF_8).use { reader ->
                credential = GSON.fromJson(reader, QqCredential::class.java)
            }
        } catch (e: Exception) {
            AcademyCraft.LOGGER.error("Failed to load QQ music credential", e)
            credential = null
        }
    }

    @Synchronized
    fun save(newCredential: QqCredential) {
        credential = newCredential
        try {
            Files.createDirectories(DIRECTORY)
            Files.newBufferedWriter(CREDENTIAL_FILE, StandardCharsets.UTF_8).use { writer ->
                GSON.toJson(newCredential, writer)
            }
        } catch (e: IOException) {
            AcademyCraft.LOGGER.error("Failed to save QQ music credential", e)
        }
    }

    @Synchronized
    fun clear() {
        credential = null
        try {
            Files.deleteIfExists(CREDENTIAL_FILE)
        } catch (e: IOException) {
            AcademyCraft.LOGGER.error("Failed to clear QQ music credential", e)
        }
    }

    fun getCredential(): QqCredential? {
        init()
        return credential
    }

    fun hasValidCredential(): Boolean = getCredential()?.isValid() == true

}
