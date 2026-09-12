package org.academy.internal.server.world.level.storage;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;
import org.academy.AcademyCraft;
import org.academy.internal.server.misaka.MisakaComputeSink;
import org.jspecify.annotations.Nullable;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Per Misaka-network-component compute allocation percentages keyed by stable network {@link UUID}.
 * Legacy BlockPos string keys are migrated on first server access via {@link MisakaNetworkRegistry}.
 */
public final class MisakaNetworkAllocations extends SavedData {
    public static final int CURRENT_DATA_VERSION = 1;

    private static final Codec<int[]> PERCENTS_CODEC = Codec.INT.listOf().xmap(
            list -> {
                var raw = new int[MisakaComputeSink.COUNT];
                for (int i = 0; i < Math.min(MisakaComputeSink.COUNT, list.size()); i++) {
                    raw[i] = list.get(i);
                }
                return MisakaComputeSink.clampAllocations(raw);
            },
            arr -> Arrays.stream(arr == null ? new int[MisakaComputeSink.COUNT] : arr).boxed().toList()
    );

    public static final Codec<MisakaNetworkAllocations> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.INT.optionalFieldOf("data_version", 0).forGetter(data -> data.dataVersion),
            Codec.unboundedMap(Codec.STRING, PERCENTS_CODEC)
                    .fieldOf("allocations")
                    .forGetter(MisakaNetworkAllocations::encodeAllocations)
    ).apply(instance, MisakaNetworkAllocations::fromCodec));

    public static final SavedDataType<MisakaNetworkAllocations> SAVED_DATA_TYPE = new SavedDataType<>(
            AcademyCraft.academy("misaka_network_allocations"),
            MisakaNetworkAllocations::new,
            CODEC
    );

    private int dataVersion = CURRENT_DATA_VERSION;
    private final Map<UUID, int[]> byNetworkId = new HashMap<>();
    /** Legacy BlockPos keys awaiting {@link #migrateLegacy(MinecraftServer)}. */
    private final Map<BlockPos, int[]> pendingLegacy = new HashMap<>();
    private @Nullable transient MinecraftServer owningServer;
    private boolean legacyMigrated;

    public MisakaNetworkAllocations() {
    }

    private static MisakaNetworkAllocations fromCodec(int dataVersion, Map<String, int[]> raw) {
        var data = new MisakaNetworkAllocations();
        data.dataVersion = dataVersion;
        if (raw != null) {
            for (var entry : raw.entrySet()) {
                var key = entry.getKey();
                var percents = MisakaComputeSink.clampAllocations(entry.getValue());
                if (key == null) {
                    continue;
                }
                try {
                    data.byNetworkId.put(UUID.fromString(key), percents);
                    continue;
                } catch (IllegalArgumentException ignored) {
                }
                var pos = tryParseBlockPos(key);
                if (pos != null) {
                    data.pendingLegacy.put(pos, percents);
                }
            }
        }
        if (data.pendingLegacy.isEmpty()) {
            data.dataVersion = CURRENT_DATA_VERSION;
            data.legacyMigrated = true;
        }
        return data;
    }

    private Map<String, int[]> encodeAllocations() {
        var out = new HashMap<String, int[]>();
        byNetworkId.forEach((id, percents) -> out.put(id.toString(), percents));
        // Preserve unmigrated legacy keys across save if server never ran.
        pendingLegacy.forEach((pos, percents) ->
                out.put(pos.getX() + "," + pos.getY() + "," + pos.getZ(), percents));
        return out;
    }

    private static @Nullable BlockPos tryParseBlockPos(String value) {
        try {
            var parts = value.split(",");
            if (parts.length != 3) {
                return null;
            }
            return new BlockPos(
                    Integer.parseInt(parts[0].trim()),
                    Integer.parseInt(parts[1].trim()),
                    Integer.parseInt(parts[2].trim())
            );
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    public static MisakaNetworkAllocations get(MinecraftServer server) {
        var data = server.overworld().getDataStorage().computeIfAbsent(SAVED_DATA_TYPE);
        data.owningServer = server;
        data.migrateLegacy(server);
        return data;
    }

    private void migrateLegacy(MinecraftServer server) {
        if (legacyMigrated || pendingLegacy.isEmpty()) {
            legacyMigrated = true;
            dataVersion = CURRENT_DATA_VERSION;
            return;
        }
        var level = server.overworld();
        var registry = MisakaNetworkRegistry.get(server);
        for (var entry : pendingLegacy.entrySet()) {
            var networkId = registry.resolveOrCreate(level, entry.getKey());
            byNetworkId.putIfAbsent(networkId, MisakaComputeSink.clampAllocations(entry.getValue()));
        }
        pendingLegacy.clear();
        legacyMigrated = true;
        dataVersion = CURRENT_DATA_VERSION;
        setDirty();
    }

    public int[] get(UUID networkId) {
        if (networkId == null) {
            return new int[MisakaComputeSink.COUNT];
        }
        ensureMigrated();
        var stored = byNetworkId.get(networkId);
        return stored == null
                ? new int[MisakaComputeSink.COUNT]
                : Arrays.copyOf(stored, MisakaComputeSink.COUNT);
    }

    public void set(UUID networkId, int[] percents) {
        if (networkId == null) {
            return;
        }
        ensureMigrated();
        byNetworkId.put(networkId, MisakaComputeSink.clampAllocations(percents));
        dataVersion = CURRENT_DATA_VERSION;
        setDirty();
    }

    private void ensureMigrated() {
        if (!legacyMigrated && owningServer != null) {
            migrateLegacy(owningServer);
        }
    }
}
