package org.academy.internal.common.attribute;

import net.minecraft.server.level.ServerPlayer;
import org.jspecify.annotations.Nullable;

import java.util.ArrayDeque;
import java.util.Deque;

/**
 * Associates block-loot enchantment queries with the server player breaking the block.
 */
public final class BlockLootPlayerContext {
    private static final ThreadLocal<Deque<ServerPlayer>> PLAYERS =
            ThreadLocal.withInitial(ArrayDeque::new);

    private BlockLootPlayerContext() {
    }

    public static void push(ServerPlayer player) {
        PLAYERS.get().push(player);
    }

    public static void pop() {
        var players = PLAYERS.get();
        if (!players.isEmpty()) players.pop();
        if (players.isEmpty()) PLAYERS.remove();
    }

    @Nullable
    public static ServerPlayer current() {
        return PLAYERS.get().peek();
    }
}
