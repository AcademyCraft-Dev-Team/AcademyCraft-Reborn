package org.academy.internal.client.app.music.netease

import com.google.gson.Gson
import com.google.gson.GsonBuilder
import net.neoforged.fml.loading.FMLPaths
import org.academy.AcademyCraft
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path

object NeteaseCredentialManager {
    private val CREDENTIAL_PATH: Path by lazy {
        FMLPaths.GAMEDIR.get()
            .resolve("config").resolve("academy").resolve("music").resolve("netease_credential.json")
    }
    private val GSON: Gson = GsonBuilder().setPrettyPrinting().create()

    private var credential: NeteaseCredential? = null
    private var cookieHeader = ""

    fun init() {
        load()
    }

    private fun load() {
        if (!Files.exists(CREDENTIAL_PATH)) {
            return
        }
        try {
            Files.newBufferedReader(CREDENTIAL_PATH, StandardCharsets.UTF_8).use { reader ->
                credential = GSON.fromJson(reader, NeteaseCredential::class.java)
            }
        } catch (e: Exception) {
            AcademyCraft.LOGGER.warn("Failed to load NetEase credential", e)
        }
    }

    fun save(newCredential: NeteaseCredential) {
        credential = newCredential
        try {
            Files.createDirectories(CREDENTIAL_PATH.parent)
            Files.newBufferedWriter(CREDENTIAL_PATH, StandardCharsets.UTF_8).use { writer ->
                GSON.toJson(credential, writer)
            }
        } catch (e: Exception) {
            AcademyCraft.LOGGER.error("Failed to save NetEase credential", e)
        }
    }

    fun clear() {
        credential = null
        cookieHeader = ""
        try {
            Files.deleteIfExists(CREDENTIAL_PATH)
        } catch (e: Exception) {
            AcademyCraft.LOGGER.warn("Failed to delete NetEase credential file", e)
        }
    }

    fun getCredential(): NeteaseCredential? = credential

    fun hasValidCredential(): Boolean = credential?.isValid() == true

    fun getEffectiveCookie(): String = cookieHeader

    fun setCookieHeader(cookie: String?) {
        cookieHeader = cookie ?: ""
    }
}
