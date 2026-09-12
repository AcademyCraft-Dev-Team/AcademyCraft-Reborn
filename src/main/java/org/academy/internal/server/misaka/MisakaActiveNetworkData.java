package org.academy.internal.server.misaka;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import org.academy.AcademyCraft;
import org.academy.internal.server.config.GenericConfig;
import org.academy.internal.server.world.level.storage.MisakaSavedDataCodecs;
import org.jspecify.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Per-player ActiveNetwork selection. Only the active network may consume that player's demand.
 */
public final class MisakaActiveNetworkData extends SavedData {
    public static final Codec<MisakaActiveNetworkData> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.unboundedMap(MisakaSavedDataCodecs.UUID_STRING_CODEC, MisakaSavedDataCodecs.UUID_STRING_CODEC)
                    .fieldOf("active_by_player")
                    .forGetter(d -> Map.copyOf(d.activeByPlayer)),
            Codec.unboundedMap(MisakaSavedDataCodecs.UUID_STRING_CODEC, Codec.LONG)
                    .fieldOf("switch_game_time")
                    .forGetter(d -> Map.copyOf(d.lastSwitchGameTime))
    ).apply(instance, MisakaActiveNetworkData::new));

    public static final SavedDataType<MisakaActiveNetworkData> SAVED_DATA_TYPE = new SavedDataType<>(
            AcademyCraft.academy("misaka_active_network"),
            MisakaActiveNetworkData::new,
            CODEC
    );

    /** Default switch cooldown: 5 seconds (100 ticks). Overridable via GenericConfig if present. */
    public static final int DEFAULT_SWITCH_COOLDOWN_TICKS = 100;

    private final Map<UUID, UUID> activeByPlayer = new HashMap<>();
    private final Map<UUID, Long> lastSwitchGameTime = new HashMap<>();

    public MisakaActiveNetworkData() {
    }

    private MisakaActiveNetworkData(Map<UUID, UUID> active, Map<UUID, Long> switches) {
        activeByPlayer.putAll(active);
        lastSwitchGameTime.putAll(switches);
    }

    public static MisakaActiveNetworkData get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(SAVED_DATA_TYPE);
    }

    public Optional<UUID> getActive(UUID playerUuid) {
        return Optional.ofNullable(activeByPlayer.get(playerUuid));
    }

    public Optional<UUID> getActive(ServerPlayer player) {
        return getActive(player.getUUID());
    }

    /**
     * @return true if switched (or already set to same network)
     */
    public boolean trySetActive(ServerPlayer player, UUID networkId, long gameTime) {
        UUID playerId = player.getUUID();
        UUID current = activeByPlayer.get(playerId);
        if (networkId.equals(current)) {
            return true;
        }
        int cooldown = switchCooldownTicks(player.level().getServer());
        Long last = lastSwitchGameTime.get(playerId);
        if (last != null && gameTime - last < cooldown) {
            return false;
        }
        activeByPlayer.put(playerId, networkId);
        lastSwitchGameTime.put(playerId, gameTime);
        setDirty();
        return true;
    }

    public void clear(UUID playerUuid) {
        if (activeByPlayer.remove(playerUuid) != null || lastSwitchGameTime.remove(playerUuid) != null) {
            setDirty();
        }
    }

    private static int switchCooldownTicks(@Nullable MinecraftServer server) {
        if (server == null) {
            return DEFAULT_SWITCH_COOLDOWN_TICKS;
        }
        var academy = server.getAcademyCraftServer();
        if (academy == null) {
            return DEFAULT_SWITCH_COOLDOWN_TICKS;
        }
        GenericConfig config = academy.getGenericConfig();
        if (config.misakaActiveNetworkCooldownTicks > 0) {
            return config.misakaActiveNetworkCooldownTicks;
        }
        return DEFAULT_SWITCH_COOLDOWN_TICKS;
    }
}
