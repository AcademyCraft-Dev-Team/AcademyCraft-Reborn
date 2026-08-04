package org.academy.internal.client.app.music.netease;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.neoforged.fml.loading.FMLPaths;
import org.academy.AcademyCraft;

import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

public final class NeteaseCredentialManager {
    private static final Path CREDENTIAL_PATH = FMLPaths.GAMEDIR.get()
            .resolve("config").resolve("academy").resolve("music").resolve("netease_credential.json");
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private static NeteaseCredential credential;
    private static String cookieHeader = "";

    private NeteaseCredentialManager() {
    }

    public static void init() {
        load();
    }

    private static void load() {
        if (!Files.exists(CREDENTIAL_PATH)) {
            return;
        }
        try (Reader reader = Files.newBufferedReader(CREDENTIAL_PATH, StandardCharsets.UTF_8)) {
            credential = GSON.fromJson(reader, NeteaseCredential.class);
        } catch (Exception e) {
            AcademyCraft.LOGGER.warn("Failed to load NetEase credential", e);
        }
    }

    public static void save(NeteaseCredential newCredential) {
        credential = newCredential;
        try {
            Files.createDirectories(CREDENTIAL_PATH.getParent());
            try (Writer writer = Files.newBufferedWriter(CREDENTIAL_PATH, StandardCharsets.UTF_8)) {
                GSON.toJson(credential, writer);
            }
        } catch (Exception e) {
            AcademyCraft.LOGGER.error("Failed to save NetEase credential", e);
        }
    }

    public static void clear() {
        credential = null;
        cookieHeader = "";
        try {
            Files.deleteIfExists(CREDENTIAL_PATH);
        } catch (Exception e) {
            AcademyCraft.LOGGER.warn("Failed to delete NetEase credential file", e);
        }
    }

    public static NeteaseCredential getCredential() {
        return credential;
    }

    public static boolean hasValidCredential() {
        return credential != null && credential.isValid();
    }

    public static String getEffectiveCookie() {
        return cookieHeader;
    }

    public static void setCookieHeader(String cookie) {
        cookieHeader = cookie == null ? "" : cookie;
    }
}
