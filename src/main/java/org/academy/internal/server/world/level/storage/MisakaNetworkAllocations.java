package org.academy.internal.server.world.level.storage;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;
import org.academy.AcademyCraft;
import org.academy.internal.server.misaka.MisakaComputeSink;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

/**
 * Per wireless-network-component compute allocation percentages keyed by {@code resolveNetworkId}.
 */
public final class MisakaNetworkAllocations extends SavedData {
    private static final Codec<BlockPos> BLOCK_POS_CODEC = Codec.STRING.flatXmap(
            value -> {
                try {
                    var parts = value.split(",");
                    if (parts.length != 3) {
                        return DataResult.error(() -> "Invalid BlockPos: " + value);
                    }
                    return DataResult.success(new BlockPos(
                            Integer.parseInt(parts[0].trim()),
                            Integer.parseInt(parts[1].trim()),
                            Integer.parseInt(parts[2].trim())
                    ));
                } catch (NumberFormatException exception) {
                    return DataResult.error(() -> "Invalid BlockPos: " + value);
                }
            },
            pos -> DataResult.success(pos.getX() + "," + pos.getY() + "," + pos.getZ())
    );

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
            Codec.unboundedMap(BLOCK_POS_CODEC, PERCENTS_CODEC)
                    .fieldOf("allocations")
                    .forGetter(data -> Map.copyOf(data.byNetworkId))
    ).apply(instance, MisakaNetworkAllocations::new));

    public static final SavedDataType<MisakaNetworkAllocations> SAVED_DATA_TYPE = new SavedDataType<>(
            AcademyCraft.academy("misaka_network_allocations"),
            MisakaNetworkAllocations::new,
            CODEC
    );

    private final Map<BlockPos, int[]> byNetworkId = new HashMap<>();

    public MisakaNetworkAllocations() {
    }

    private MisakaNetworkAllocations(Map<BlockPos, int[]> allocations) {
        allocations.forEach((pos, percents) ->
                byNetworkId.put(pos.immutable(), MisakaComputeSink.clampAllocations(percents)));
    }

    public static MisakaNetworkAllocations get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(SAVED_DATA_TYPE);
    }

    public int[] get(BlockPos networkId) {
        if (networkId == null) {
            return new int[MisakaComputeSink.COUNT];
        }
        var stored = byNetworkId.get(networkId.immutable());
        return stored == null
                ? new int[MisakaComputeSink.COUNT]
                : Arrays.copyOf(stored, MisakaComputeSink.COUNT);
    }

    public void set(BlockPos networkId, int[] percents) {
        if (networkId == null) {
            return;
        }
        byNetworkId.put(networkId.immutable(), MisakaComputeSink.clampAllocations(percents));
        setDirty();
    }
}
