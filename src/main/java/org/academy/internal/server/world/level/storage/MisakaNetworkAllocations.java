package org.academy.internal.server.world.level.storage;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;
import org.academy.AcademyCraft;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Per Misaka-network compute allocation percentages keyed by player UUID.
 * Legacy sink int[] and BlockPos keys are discarded / migrated without purpose semantics.
 */
public final class MisakaNetworkAllocations extends SavedData {
    public static final int CURRENT_DATA_VERSION = 2;

    private static final Codec<Map<UUID, Integer>> PLAYER_PERCENTS_CODEC =
            Codec.unboundedMap(MisakaSavedDataCodecs.UUID_STRING_CODEC, Codec.INT).xmap(
                    MisakaNetworkAllocations::clampPlayerPercents,
                    map -> map
            );

    public static final Codec<MisakaNetworkAllocations> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.INT.optionalFieldOf("data_version", 0).forGetter(data -> data.dataVersion),
            Codec.unboundedMap(Codec.STRING, PLAYER_PERCENTS_CODEC)
                    .fieldOf("allocations")
                    .forGetter(MisakaNetworkAllocations::encodeAllocations)
    ).apply(instance, MisakaNetworkAllocations::fromCodec));

    public static final SavedDataType<MisakaNetworkAllocations> SAVED_DATA_TYPE = new SavedDataType<>(
            AcademyCraft.academy("misaka_network_allocations"),
            MisakaNetworkAllocations::new,
            CODEC
    );

    private int dataVersion = CURRENT_DATA_VERSION;
    private final Map<UUID, Map<UUID, Integer>> byNetworkId = new HashMap<>();

    public MisakaNetworkAllocations() {
    }

    private static MisakaNetworkAllocations fromCodec(int dataVersion, Map<String, Map<UUID, Integer>> raw) {
        var data = new MisakaNetworkAllocations();
        data.dataVersion = dataVersion;
        if (raw != null) {
            for (var entry : raw.entrySet()) {
                if (entry.getKey() == null) {
                    continue;
                }
                try {
                    var networkId = UUID.fromString(entry.getKey());
                    data.byNetworkId.put(networkId, clampPlayerPercents(entry.getValue()));
                } catch (IllegalArgumentException ignored) {
                    // Drop legacy BlockPos keys and unreadable entries (v1 sink arrays never decode here).
                }
            }
        }
        data.dataVersion = CURRENT_DATA_VERSION;
        return data;
    }

    private Map<String, Map<UUID, Integer>> encodeAllocations() {
        var out = new HashMap<String, Map<UUID, Integer>>();
        byNetworkId.forEach((id, percents) -> out.put(id.toString(), Map.copyOf(percents)));
        return out;
    }

    public static Map<UUID, Integer> clampPlayerPercents(Map<UUID, Integer> raw) {
        var out = new LinkedHashMap<UUID, Integer>();
        if (raw == null || raw.isEmpty()) {
            return out;
        }
        int sum = 0;
        for (var entry : raw.entrySet()) {
            if (entry.getKey() == null || entry.getValue() == null) {
                continue;
            }
            int value = Math.max(0, Math.min(100, entry.getValue()));
            if (sum + value > 100) {
                value = 100 - sum;
            }
            if (value > 0) {
                out.put(entry.getKey(), value);
            }
            sum += value;
            if (sum >= 100) {
                break;
            }
        }
        return out;
    }

    public static int sum(Map<UUID, Integer> percents) {
        if (percents == null) {
            return 0;
        }
        int sum = 0;
        for (int value : percents.values()) {
            sum += Math.max(0, value);
        }
        return sum;
    }

    public static MisakaNetworkAllocations get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(SAVED_DATA_TYPE);
    }

    public Map<UUID, Integer> get(UUID networkId) {
        if (networkId == null) {
            return Map.of();
        }
        var stored = byNetworkId.get(networkId);
        return stored == null ? Map.of() : Map.copyOf(stored);
    }

    public void set(UUID networkId, Map<UUID, Integer> percents) {
        if (networkId == null) {
            return;
        }
        var clamped = clampPlayerPercents(percents);
        if (clamped.isEmpty()) {
            byNetworkId.remove(networkId);
        } else {
            byNetworkId.put(networkId, new LinkedHashMap<>(clamped));
        }
        dataVersion = CURRENT_DATA_VERSION;
        setDirty();
    }

    /** Ensure integrated networks without rows default 100% to {@code privilegePlayer}. */
    public void ensureDefaultPrivilege(UUID networkId, UUID privilegePlayer) {
        if (networkId == null || privilegePlayer == null) {
            return;
        }
        if (!byNetworkId.containsKey(networkId) || byNetworkId.get(networkId).isEmpty()) {
            set(networkId, Map.of(privilegePlayer, 100));
        }
    }
}
