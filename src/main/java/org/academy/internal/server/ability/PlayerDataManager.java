package org.academy.internal.server.ability;

import com.mojang.logging.LogUtils;
import net.minecraft.server.level.ServerPlayer;
import org.academy.api.common.data.CPData;
import org.academy.internal.server.world.level.storage.Player;
import org.academy.internal.server.world.level.storage.WorldData;
import org.slf4j.Logger;

import java.util.Map;
import java.util.UUID;

public final class PlayerDataManager {
    private static final Logger LOGGER = LogUtils.getLogger();

    private final Map<UUID, Player> playerDataMap;

    public PlayerDataManager(WorldData worldData) {
        playerDataMap = worldData.getPlayers();
    }

    public void onPlayerLogin(ServerPlayer serverPlayer) {
        playerDataMap.computeIfAbsent(serverPlayer.getUUID(), this::createDefaultPlayerData);
    }

    public Player getData(UUID playerUUID) {
        return playerDataMap.get(playerUUID);
    }

    private Player createDefaultPlayerData(UUID uuid) {
        LOGGER.debug("Creating new data entry for player {}", uuid);
        var player = new Player();
        player.setLevel(0);
        player.setAvailableCP(100);
        player.setMaxCP(100);
        player.setStatus(CPData.Status.NORMAL);
        player.markDirty();
        return player;
    }
}