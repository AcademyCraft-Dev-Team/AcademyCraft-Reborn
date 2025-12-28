package org.academy.internal.server.ability;

import net.minecraft.server.level.ServerPlayer;
import org.academy.AcademyCraft;
import org.academy.internal.server.world.level.storage.Player;
import org.academy.internal.server.world.level.storage.WorldData;
import org.slf4j.Logger;

import java.util.Map;
import java.util.UUID;

public final class PlayerDataManager {
    private static final Logger LOGGER = AcademyCraft.getLogger();

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
        player.markDirty();
        return player;
    }
}