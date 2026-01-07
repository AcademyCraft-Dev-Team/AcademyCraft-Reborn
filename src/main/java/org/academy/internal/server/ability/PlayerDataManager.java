package org.academy.internal.server.ability;

import net.minecraft.core.Holder;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import org.academy.AcademyCraft;
import org.academy.api.common.ability.AbilityCategory;
import org.academy.api.common.ability.SyncTypes;
import org.academy.api.common.ability.pakcet.SyncAbilityCategoryPacket;
import org.academy.api.common.registries.Registries;
import org.academy.internal.common.ability.AbilityCategories;
import org.academy.internal.server.world.level.storage.Player;
import org.academy.internal.server.world.level.storage.WorldData;
import org.jetbrains.annotations.NotNull;
import org.jspecify.annotations.Nullable;
import org.misaka.MisakaNetworkServer;
import org.slf4j.Logger;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;

public final class PlayerDataManager implements AbilitySubsystem {
    private static final Logger LOGGER = AcademyCraft.getLogger();
    private final SyncManager syncManager;
    private final Map<UUID, Player> playerDataMap;

    public PlayerDataManager(WorldData worldData, SyncManager syncManager) {
        playerDataMap = worldData.getPlayers();
        this.syncManager = syncManager;
    }

    @Override
    public void onPlayerLogin(ServerPlayer serverPlayer) {
        playerDataMap.computeIfAbsent(serverPlayer.getUUID(), this::createDefaultPlayerData);
        syncManager.schedulePlayerSync(serverPlayer.getUUID(), SyncTypes.ABILITY_CATEGORY);
    }

    @Override
    public void processSync(@NotNull ServerPlayer player, @NotNull Identifier type) {
        var uuid = player.getUUID();
        if (SyncTypes.ABILITY_CATEGORY.equals(type)) {
            var packet = new SyncAbilityCategoryPacket(getPlayerAbilityCategory(uuid));
            MisakaNetworkServer.sendPacket(player, packet);
        }
    }

    public AbilityCategory getPlayerAbilityCategory(UUID uuid) {
        return Optional.ofNullable(getData(uuid))
                .map(Player::getAbilityCategory)
                .map(Identifier::tryParse)
                .flatMap(Registries.ABILITY_CATEGORIES::get)
                .map(Holder::value)
                .orElse(AbilityCategories.LEVEL0.get());
    }

    public void setPlayerAbilityCategory(UUID uuid, AbilityCategory abilityCategory) {
        var key = Registries.ABILITY_CATEGORIES.getKey(abilityCategory);
        if (key == null) {
            LOGGER.warn("Tried to set unregistered AbilityCategory for player {}", uuid);
            return;
        }
        var data = getData(uuid);
        if (data != null) {
            data.setAbilityCategory(key.toString());
            syncManager.schedulePlayerSync(uuid, SyncTypes.ABILITY_CATEGORY);
        }
    }

    @Nullable
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