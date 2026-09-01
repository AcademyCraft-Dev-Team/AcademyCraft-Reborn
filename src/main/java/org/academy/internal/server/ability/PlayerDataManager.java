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
import org.jspecify.annotations.Nullable;
import org.misaka.MisakaNetworkServer;
import org.slf4j.Logger;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;

public final class PlayerDataManager implements AbilitySubsystem {
    private static final Logger LOGGER = AcademyCraft.getLogger();
    private static final StackWalker STATE_STACK_WALKER = StackWalker.getInstance(
            StackWalker.Option.RETAIN_CLASS_REFERENCE
    );
    private final SyncManager syncManager;
    private final Map<UUID, Player> playerDataMap;

    public PlayerDataManager(WorldData worldData, SyncManager syncManager) {
        playerDataMap = worldData.getPlayers();
        this.syncManager = syncManager;
    }

    @Override
    public void onPlayerLogin(ServerPlayer serverPlayer) {
        var uuid = serverPlayer.getUUID();
        playerDataMap.computeIfAbsent(uuid, this::createDefaultPlayerData);
        bindAbilityStatusProtection(uuid);
        syncManager.schedulePlayerSync(uuid, SyncTypes.ABILITY_CATEGORY);
    }

    @Override
    public void processSync(ServerPlayer player) {
        var uuid = player.getUUID();
        var packet = new SyncAbilityCategoryPacket(getPlayerAbilityCategory(uuid));
        MisakaNetworkServer.send(player, packet);
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
        var caller = STATE_STACK_WALKER.walk(frames -> frames
                .dropWhile(frame -> frame.getDeclaringClass() != PlayerDataManager.class
                        || !frame.getMethodName().equals("setPlayerAbilityCategory"))
                .skip(1)
                .map(StackWalker.StackFrame::getDeclaringClass)
                .findFirst()
                .orElse(null));
        var callerDomain = caller == null ? null : caller.getProtectionDomain();
        var academyDomain = AcademyCraft.class.getProtectionDomain();
        var allowed = callerDomain != null && callerDomain == academyDomain;
        if (!allowed && callerDomain != null && callerDomain.getCodeSource() != null) {
            var callerLocation = callerDomain.getCodeSource().getLocation();
            var academyLocation = academyDomain == null || academyDomain.getCodeSource() == null
                    ? null : academyDomain.getCodeSource().getLocation();
            allowed = callerLocation != null && callerLocation.equals(academyLocation);
        }
        if (!allowed) return;

        var key = Registries.ABILITY_CATEGORIES.getKey(abilityCategory);
        if (key == null) {
            LOGGER.warn("Tried to set unregistered AbilityCategory for player {}", uuid);
            return;
        }
        var data = getData(uuid);
        if (data != null) {
            data.setAbilityCategory(key.toString());
            data.getCpData().bindStatusProtection(abilityCategory.getClass());
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

    private void bindAbilityStatusProtection(UUID uuid) {
        var data = getData(uuid);
        if (data == null) return;
        data.getCpData().bindStatusProtection(getPlayerAbilityCategory(uuid).getClass());
    }
}
