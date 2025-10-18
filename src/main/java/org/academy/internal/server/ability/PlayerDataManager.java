package org.academy.internal.server.ability;

import net.minecraft.server.level.ServerPlayer;
import org.academy.AcademyCraft;
import org.academy.internal.server.world.level.storage.Player;
import org.academy.internal.server.world.level.storage.WorldData;

import java.util.Map;
import java.util.UUID;

public final class PlayerDataManager {
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
        AcademyCraft.LOGGER.debug("Creating new data entry for player {}", uuid);
        var newPlayerData = new Player();
        newPlayerData.setLevel(0);
        newPlayerData.setComputingPower(100);
        newPlayerData.setMaxComputingPower(100);
        newPlayerData.markDirty();
        return newPlayerData;
    }
}