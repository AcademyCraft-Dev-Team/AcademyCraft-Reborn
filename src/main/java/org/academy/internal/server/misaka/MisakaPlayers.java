package org.academy.internal.server.misaka;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import org.jspecify.annotations.Nullable;

/**
 * Online-player lookups used by network membership and device-ownership transfers.
 * Offline names cannot yield a UUID from the display-name cache alone.
 */
public final class MisakaPlayers {
    private MisakaPlayers() {
    }

    public static @Nullable ServerPlayer findOnlineByName(@Nullable MinecraftServer server, @Nullable String name) {
        if (server == null || name == null || name.isBlank()) {
            return null;
        }
        return server.getPlayerList().getPlayerByName(name.trim());
    }
}
