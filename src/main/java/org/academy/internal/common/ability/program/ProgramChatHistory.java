package org.academy.internal.common.ability.program;

import net.minecraft.server.level.ServerPlayer;
import org.jspecify.annotations.Nullable;

import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** Bounded server-observed chat history for program queries. */
public final class ProgramChatHistory {
    public static final int MAX_MESSAGES = 32;
    public static final int MAX_CODE_POINTS = 256;
    private static final Map<UUID, ArrayDeque<String>> HISTORY = new HashMap<>();

    private ProgramChatHistory() {
    }

    public static void record(ServerPlayer player, String message) {
        record(player.getUUID(), message);
    }

    static void record(UUID playerId, String message) {
        var text = slice(message, 0, MAX_CODE_POINTS);
        var history = HISTORY.computeIfAbsent(playerId, _ -> new ArrayDeque<>());
        history.addFirst(text);
        while (history.size() > MAX_MESSAGES) history.removeLast();
    }

    public static @Nullable String read(UUID playerId, int distance) {
        if (distance < 0 || distance >= MAX_MESSAGES) return null;
        var history = HISTORY.get(playerId);
        if (history == null) return null;
        var index = 0;
        for (var message : history) {
            if (index++ == distance) return message;
        }
        return null;
    }

    public static String slice(String text, int start, int length) {
        if (text == null || start < 0 || length < 0) return "";
        var total = text.codePointCount(0, text.length());
        if (start >= total) return "";
        var from = text.offsetByCodePoints(0, start);
        var to = text.offsetByCodePoints(from, Math.min(length, total - start));
        return text.substring(from, to);
    }

    public static void clear(UUID playerId) {
        HISTORY.remove(playerId);
    }

    public static void clear() {
        HISTORY.clear();
    }
}
