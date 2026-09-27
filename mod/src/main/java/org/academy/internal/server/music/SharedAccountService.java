package org.academy.internal.server.music;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.commands.Commands;
import net.minecraft.server.level.ServerPlayer;
import org.academy.AcademyCraft;
import org.academy.internal.common.music.provider.MusicProviders;
import org.academy.internal.common.music.provider.ProviderCredential;
import org.academy.internal.common.network.MusicAccountPackets;
import org.academy.internal.server.config.MusicConfig;
import org.jspecify.annotations.Nullable;
import org.misaka.MisakaNetworkServer;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public final class SharedAccountService {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Path DIRECTORY = Path.of("config", "academy", "music");

    private static final Map<String, ProviderCredential> CREDENTIALS = new ConcurrentHashMap<>();
    private static volatile MusicConfig.SharedAccountSettings settings = new MusicConfig.SharedAccountSettings();

    private SharedAccountService() {
    }

    public static void init(MusicConfig config) {
        settings = config.sharedAccount;
        CREDENTIALS.clear();
        for (var provider : new String[]{"qq", "netease"}) {
            var credential = load(provider);
            if (credential != null) CREDENTIALS.put(provider, credential);
        }
    }

    public static boolean isEnabled() {
        return settings.enabled;
    }

    public static ProviderCredential credentialFor(String provider) {
        return CREDENTIALS.get(normalize(provider));
    }

    public static void upload(String provider, String payloadJson) {
        var normalized = normalize(provider);
        if (MusicProviders.byName(normalized).isEmpty()) {
            throw new IllegalArgumentException("unknown provider");
        }
        var credential = parsePayload(normalized, payloadJson);
        CREDENTIALS.put(normalized, credential);
        var root = new JsonObject();
        root.addProperty("provider", normalized);
        root.addProperty("payload", payloadJson);
        try {
            Files.createDirectories(DIRECTORY);
            Files.writeString(DIRECTORY.resolve("server_credential_" + normalized + ".json"),
                    GSON.toJson(root), StandardCharsets.UTF_8);
        } catch (IOException exception) {
            AcademyCraft.LOGGER.warn("Failed to save shared credential for {}", normalized, exception);
        }
    }

    public static boolean clear(String provider) {
        var normalized = normalize(provider);
        var removed = CREDENTIALS.remove(normalized) != null;
        try {
            Files.deleteIfExists(DIRECTORY.resolve("server_credential_" + normalized + ".json"));
        } catch (IOException exception) {
            AcademyCraft.LOGGER.warn("Failed to delete shared credential for {}", normalized, exception);
        }
        return removed;
    }

    public record CredentialStatus(boolean valid, long expiresAtEpochSeconds) {
    }

    public static CredentialStatus status(String provider) {
        var credential = credentialFor(provider);
        if ("qq".equals(normalize(provider))) {
            return new CredentialStatus(
                    credential.hasQqCredential() && !credential.isQqExpired(),
                    credential.qqKeyExpiresAtEpochSeconds()
            );
        }
        return new CredentialStatus(credential.hasCookie(), 0L);
    }

    private static @Nullable ProviderCredential load(String provider) {
        var file = DIRECTORY.resolve("server_credential_" + provider + ".json");
        if (!Files.exists(file)) return null;
        try {
            var root = JsonParser.parseString(Files.readString(file, StandardCharsets.UTF_8)).getAsJsonObject();
            if (!root.has("payload")) return null;
            return parsePayload(provider, root.get("payload").getAsString());
        } catch (Exception exception) {
            AcademyCraft.LOGGER.warn("Failed to load shared credential for {}", provider, exception);
            return null;
        }
    }

    private static ProviderCredential parsePayload(String provider, String payloadJson) {
        var payload = JsonParser.parseString(payloadJson).getAsJsonObject();
        if ("qq".equals(provider)) {
            var musicId = string(payload, "musicid");
            var musicKey = string(payload, "musickey");
            if (musicId.isBlank() || musicKey.isBlank()) {
                throw new IllegalArgumentException("qq credential missing musicid/musickey");
            }
            var expiresIn = payload.has("keyExpiresIn") ? payload.get("keyExpiresIn").getAsLong() : 0L;
            var createTime = payload.has("musickeyCreateTime") ? payload.get("musickeyCreateTime").getAsLong() : 0L;
            var expiresAt = expiresIn > 0 ? createTime + expiresIn : 0L;
            return ProviderCredential.ofQq(musicId, musicKey, expiresAt);
        }
        if ("netease".equals(provider)) {
            var cookie = string(payload, "cookie");
            if (cookie.isBlank()) {
                throw new IllegalArgumentException("netease credential missing cookie");
            }
            return ProviderCredential.ofCookie(cookie);
        }
        throw new IllegalArgumentException("unknown provider");
    }

    private static String string(JsonObject object, String key) {
        return object.has(key) && !object.get(key).isJsonNull()
                ? object.get(key).getAsString() : "";
    }

    private static String normalize(String provider) {
        return provider.trim().toLowerCase(Locale.ROOT);
    }

    /**
     * 处理客户端凭证操作：UPLOAD/CLEAR 需 OP 权限，STATUS 所有玩家可查（不含凭证内容）喵。
     */
    public static void handleAction(
            MusicAccountPackets.AccountActionPacket packet,
            ServerPlayer player
    ) {
        var provider = normalize(packet.provider());
        String messageKey = "";
        boolean ok = true;
        switch (packet.action()) {
            case UPLOAD, CLEAR -> {
                if (!Commands.LEVEL_GAMEMASTERS.check(player.permissions())) {
                    sendStatus(player, provider, "message.academy.music_account.no_permission", false);
                    return;
                }
                if (packet.action() == MusicAccountPackets.AccountAction.UPLOAD) {
                    try {
                        upload(provider, packet.payload());
                        messageKey = "message.academy.music_account.uploaded";
                        AcademyCraft.LOGGER.info("Shared music account '{}' uploaded by {}",
                                provider, player.getGameProfile().name());
                    } catch (IllegalArgumentException exception) {
                        ok = false;
                        messageKey = "message.academy.music_account.payload_invalid";
                    }
                } else {
                    clear(provider);
                    messageKey = "message.academy.music_account.cleared";
                }
            }
            case STATUS -> messageKey = "message.academy.music_account.status";
        }
        sendStatus(player, provider, messageKey, ok);
    }

    private static void sendStatus(ServerPlayer player, String provider, String messageKey, boolean ok) {
        var status = status(provider);
        MisakaNetworkServer.send(player,
                new MusicAccountPackets.AccountStatusPacket(
                        isEnabled(),
                        provider,
                        true,
                        status.valid(),
                        status.expiresAtEpochSeconds(),
                        ok,
                        messageKey
                ));
    }
}
