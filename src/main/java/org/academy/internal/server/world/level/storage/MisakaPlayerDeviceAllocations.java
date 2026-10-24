package org.academy.internal.server.world.level.storage;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;
import org.academy.AcademyCraft;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Persisted last device allocation percents per player.
 * Zero entries are omitted. Empty map ⇒ never configured (equal-split mode).
 */
public final class MisakaPlayerDeviceAllocations extends SavedData {
    private static final Codec<Map<Identifier, Integer>> DEVICE_MAP_CODEC =
            Codec.unboundedMap(Identifier.CODEC, Codec.INT).xmap(
                    MisakaPlayerDeviceAllocations::sanitizeDeviceMap,
                    map -> map
            );

    public static final Codec<MisakaPlayerDeviceAllocations> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.unboundedMap(MisakaSavedDataCodecs.UUID_STRING_CODEC, DEVICE_MAP_CODEC)
                    .fieldOf("players")
                    .forGetter(data -> data.byPlayer)
    ).apply(instance, MisakaPlayerDeviceAllocations::new));

    public static final SavedDataType<MisakaPlayerDeviceAllocations> SAVED_DATA_TYPE = new SavedDataType<>(
            AcademyCraft.academy("misaka_player_device_allocations"),
            MisakaPlayerDeviceAllocations::new,
            CODEC
    );

    private final Map<UUID, Map<Identifier, Integer>> byPlayer;

    public MisakaPlayerDeviceAllocations() {
        this.byPlayer = new HashMap<>();
    }

    private MisakaPlayerDeviceAllocations(Map<UUID, Map<Identifier, Integer>> byPlayer) {
        this.byPlayer = new HashMap<>();
        if (byPlayer != null) {
            byPlayer.forEach((uuid, map) -> {
                if (uuid != null) {
                    this.byPlayer.put(uuid, sanitizeDeviceMap(map));
                }
            });
        }
    }

    private static Map<Identifier, Integer> sanitizeDeviceMap(Map<Identifier, Integer> raw) {
        var out = new LinkedHashMap<Identifier, Integer>();
        if (raw == null) {
            return out;
        }
        int sum = 0;
        for (var entry : raw.entrySet()) {
            if (entry.getKey() == null || entry.getValue() == null) {
                continue;
            }
            int value = Math.max(0, Math.min(100, entry.getValue()));
            if (value <= 0) {
                continue;
            }
            if (sum + value > 100) {
                value = 100 - sum;
            }
            if (value > 0) {
                out.put(entry.getKey(), value);
                sum += value;
            }
            if (sum >= 100) {
                break;
            }
        }
        return out;
    }

    public static MisakaPlayerDeviceAllocations get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(SAVED_DATA_TYPE);
    }

    public boolean neverConfigured(UUID playerUuid) {
        if (playerUuid == null) {
            return true;
        }
        var map = byPlayer.get(playerUuid);
        return map == null || map.isEmpty();
    }

    public int lastPercent(UUID playerUuid, Identifier deviceId) {
        if (playerUuid == null || deviceId == null) {
            return 0;
        }
        var map = byPlayer.get(playerUuid);
        if (map == null) {
            return 0;
        }
        return map.getOrDefault(deviceId, 0);
    }

    public Map<Identifier, Integer> lastPercents(UUID playerUuid) {
        if (playerUuid == null) {
            return Map.of();
        }
        var map = byPlayer.get(playerUuid);
        return map == null ? Map.of() : Map.copyOf(map);
    }

    public void setLastPercents(UUID playerUuid, Map<Identifier, Integer> percents) {
        if (playerUuid == null) {
            return;
        }
        var sanitized = sanitizeDeviceMap(percents);
        if (sanitized.isEmpty()) {
            byPlayer.remove(playerUuid);
        } else {
            byPlayer.put(playerUuid, new LinkedHashMap<>(sanitized));
        }
        setDirty();
    }

    public void setLastPercent(UUID playerUuid, Identifier deviceId, int percent) {
        if (playerUuid == null || deviceId == null) {
            return;
        }
        var current = new LinkedHashMap<>(lastPercents(playerUuid));
        if (percent <= 0) {
            current.remove(deviceId);
        } else {
            current.put(deviceId, percent);
        }
        setLastPercents(playerUuid, current);
    }
}
