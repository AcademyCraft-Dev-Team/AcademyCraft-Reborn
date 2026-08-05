package org.academy.internal.client.app.music.qq;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.neoforged.fml.loading.FMLPaths;
import org.academy.AcademyCraft;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

public final class QqCredentialManager {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Path DIRECTORY = FMLPaths.CONFIGDIR.get().resolve("academy").resolve("music");
    private static final Path CREDENTIAL_FILE = DIRECTORY.resolve("qq_credential.json");
    private static volatile QqCredential credential;
    private static volatile boolean initialized;

    private QqCredentialManager() {
    }

    public static synchronized void init() {
        if (initialized) {
            return;
        }
        try {
            Files.createDirectories(DIRECTORY);
        } catch (IOException e) {
            AcademyCraft.LOGGER.error("Failed to create QQ music credential directory", e);
        }
        load();
        initialized = true;
    }

    public static synchronized void load() {
        if (!Files.exists(CREDENTIAL_FILE)) {
            credential = null;
            return;
        }
        try (Reader reader = Files.newBufferedReader(CREDENTIAL_FILE, StandardCharsets.UTF_8)) {
            credential = GSON.fromJson(reader, QqCredential.class);
        } catch (Exception e) {
            AcademyCraft.LOGGER.error("Failed to load QQ music credential", e);
            credential = null;
        }
    }

    public static synchronized void save(QqCredential newCredential) {
        credential = newCredential;
        try {
            Files.createDirectories(DIRECTORY);
            try (Writer writer = Files.newBufferedWriter(CREDENTIAL_FILE, StandardCharsets.UTF_8)) {
                GSON.toJson(newCredential, writer);
            }
        } catch (IOException e) {
            AcademyCraft.LOGGER.error("Failed to save QQ music credential", e);
        }
    }

    public static synchronized void clear() {
        credential = null;
        try {
            Files.deleteIfExists(CREDENTIAL_FILE);
        } catch (IOException e) {
            AcademyCraft.LOGGER.error("Failed to clear QQ music credential", e);
        }
    }

    public static QqCredential getCredential() {
        init();
        return credential;
    }

    public static boolean hasValidCredential() {
        var current = getCredential();
        return current != null && current.isValid();
    }

    public static String getEffectiveCookie() {
        var current = getCredential();
        return current != null ? current.toCookieString() : "";
    }
}
