package org.academy.internal.common.world.entity.misaka.favor;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import org.academy.api.common.misaka.MisakaNAT;
import org.academy.internal.common.world.entity.misaka.MobRelation;
import org.academy.internal.server.world.level.storage.MisakaSisterRecord;
import org.academy.internal.server.world.level.storage.MisakaSisterRoster;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;

/**
 * Favor mutation and one-hop LAN propagation.
 * <p>
 * Propagation scope (design §4.5): origin + origin's network + sisters within
 * {@link #LAN_PROXIMITY_CHUNKS} of origin + those neighbors' directly connected networks.
 * Does <strong>not</strong> recursively scan proximity from neighbors.
 */
public final class FavorService {
    /** Chebyshev chunk radius that bridges distinct Misaka networks into one favor hop. */
    public static final int LAN_PROXIMITY_CHUNKS = 4;

    private FavorService() {
    }

    /** Apply favor delta to a single record (no LAN flood). */
    public static void modifyFavor(MisakaSisterRecord record, String name, int delta) {
        if (!record.awakened || name == null || name.isEmpty()) {
            return;
        }
        int current = record.favorByPlayerName.getOrDefault(name, 0);
        if (current <= -20) {
            record.favorByPlayerName.put(name, -20);
            return;
        }
        record.favorByPlayerName.put(name, Mth.clamp(current + delta, -20, 20));
    }

    /**
     * Apply favor delta to the one-hop LAN component of {@code origin}.
     */
    public static void modifyFavorLan(MinecraftServer server, MisakaSisterRecord origin, String name, int delta) {
        modifyFavorLan(server, List.of(origin), name, delta);
    }

    /**
     * Union of one-hop components of all seeds, applying {@code delta} once per sister.
     */
    public static void modifyFavorLan(
            MinecraftServer server,
            Collection<MisakaSisterRecord> seeds,
            String name,
            int delta
    ) {
        if (name == null || name.isEmpty() || seeds == null || seeds.isEmpty()) {
            return;
        }
        var roster = MisakaSisterRoster.get(server);
        var level = server.overworld();
        Set<UUID> applied = new HashSet<>();
        for (var seed : seeds) {
            if (seed == null || !seed.awakened) {
                continue;
            }
            for (var member : resolveLanComponent(level, seed, roster.all())) {
                if (applied.add(member.misakaUuid)) {
                    modifyFavor(member, name, delta);
                }
            }
        }
    }

    public static List<MisakaSisterRecord> resolveLanComponent(
            ServerLevel level,
            MisakaSisterRecord origin,
            Collection<MisakaSisterRecord> all
    ) {
        return resolveLanComponent(
                origin,
                all,
                record -> networkKey(level, record),
                FavorService::positionChunk,
                FavorService::positionDimension
        );
    }

    /**
     * Pure one-hop resolve for tests.
     * Members = origin ∪ same-network-as-origin ∪ proximity-to-origin ∪ networks-of-proximity-neighbors.
     */
    public static List<MisakaSisterRecord> resolveLanComponent(
            MisakaSisterRecord origin,
            Collection<MisakaSisterRecord> all,
            Function<MisakaSisterRecord, @Nullable Object> networkKey,
            Function<MisakaSisterRecord, @Nullable ChunkPos> chunkPos
    ) {
        return resolveLanComponent(origin, all, networkKey, chunkPos, FavorService::positionDimension);
    }

    public static List<MisakaSisterRecord> resolveLanComponent(
            MisakaSisterRecord origin,
            Collection<MisakaSisterRecord> all,
            Function<MisakaSisterRecord, @Nullable Object> networkKey,
            Function<MisakaSisterRecord, @Nullable ChunkPos> chunkPos,
            Function<MisakaSisterRecord, @Nullable ResourceKey<Level>> dimension
    ) {
        if (!origin.awakened) {
            return List.of();
        }
        List<MisakaSisterRecord> awakened = new ArrayList<>();
        for (var record : all) {
            if (record.awakened) {
                awakened.add(record);
            }
        }

        Object originNet = networkKey.apply(origin);
        ChunkPos originChunk = chunkPos.apply(origin);
        ResourceKey<Level> originDim = dimension.apply(origin);

        Set<UUID> included = new HashSet<>();
        List<MisakaSisterRecord> result = new ArrayList<>();
        Set<Object> neighborNetworks = new HashSet<>();

        include(origin, included, result);

        // Pass 1: same network as origin + proximity neighbors of origin only.
        for (var other : awakened) {
            if (included.contains(other.misakaUuid)) {
                continue;
            }
            Object otherNet = networkKey.apply(other);
            if (originNet != null && originNet.equals(otherNet)) {
                include(other, included, result);
                continue;
            }
            if (withinProximity(originChunk, chunkPos.apply(other), originDim, dimension.apply(other))) {
                include(other, included, result);
                if (otherNet != null) {
                    neighborNetworks.add(otherNet);
                }
            }
        }

        // Pass 2: entire networks of proximity neighbors (one hop). No further proximity scan.
        if (!neighborNetworks.isEmpty()) {
            for (var other : awakened) {
                if (included.contains(other.misakaUuid)) {
                    continue;
                }
                Object otherNet = networkKey.apply(other);
                if (otherNet != null && neighborNetworks.contains(otherNet)) {
                    include(other, included, result);
                }
            }
        }

        return result;
    }

    private static void include(
            MisakaSisterRecord record,
            Set<UUID> included,
            List<MisakaSisterRecord> result
    ) {
        if (included.add(record.misakaUuid)) {
            result.add(record);
        }
    }

    private static boolean withinProximity(
            @Nullable ChunkPos chunkA,
            @Nullable ChunkPos chunkB,
            @Nullable ResourceKey<Level> dimA,
            @Nullable ResourceKey<Level> dimB
    ) {
        if (chunkA == null || chunkB == null || dimA == null || dimB == null || !dimA.equals(dimB)) {
            return false;
        }
        return Math.max(Math.abs(chunkA.x() - chunkB.x()), Math.abs(chunkA.z() - chunkB.z()))
                <= LAN_PROXIMITY_CHUNKS;
    }

    /** Apply {@code delta} with LAN flood when {@code server} is present; otherwise single-record. */
    public static void applyDelta(
            @Nullable MinecraftServer server,
            MisakaSisterRecord record,
            String name,
            int delta
    ) {
        if (server != null) {
            modifyFavorLan(server, record, name, delta);
        } else {
            modifyFavor(record, name, delta);
        }
    }

    public static MobRelation relation(MisakaSisterRecord record, String name) {
        if (!record.awakened) {
            return MobRelation.INDIFFERENT;
        }
        int favor = record.favorByPlayerName.getOrDefault(name, 0);
        if (favor <= -20) {
            return MobRelation.DEADLY_ENEMY;
        }
        if (favor < -10) {
            return MobRelation.HOSTILE;
        }
        if (favor <= 0) {
            return MobRelation.INDIFFERENT;
        }
        int max = record.favorByPlayerName.values().stream().mapToInt(Integer::intValue).max().orElse(0);
        return favor == max ? MobRelation.BENEVOLENT : MobRelation.DEFAULT;
    }

    public static boolean isPrivilegePlayer(MisakaSisterRecord record, String name) {
        return relation(record, name) == MobRelation.BENEVOLENT
                && Objects.equals(name, record.lastInteractedBenevolentPlayerName);
    }

    /** All player names currently at max favor &gt; 0 (benevolent set). */
    public static List<String> benevolentNames(MisakaSisterRecord record) {
        if (!record.awakened || record.favorByPlayerName.isEmpty()) {
            return List.of();
        }
        int max = record.favorByPlayerName.values().stream().mapToInt(Integer::intValue).max().orElse(0);
        if (max <= 0) {
            return List.of();
        }
        List<String> names = new ArrayList<>();
        for (var entry : record.favorByPlayerName.entrySet()) {
            if (entry.getValue() == max) {
                names.add(entry.getKey());
            }
        }
        names.sort(String::compareTo);
        return names;
    }

    private static @Nullable Object networkKey(ServerLevel level, MisakaSisterRecord record) {
        BlockPos node = record.networkNodePos;
        if (node == null) {
            return null;
        }
        return MisakaNAT.get().resolveNetworkId(level, node);
    }

    private static @Nullable ChunkPos positionChunk(MisakaSisterRecord record) {
        if (record.lastKnownChunk != null) {
            return record.lastKnownChunk;
        }
        if (record.wanderAnchorChunk != null) {
            return record.wanderAnchorChunk;
        }
        if (record.networkNodePos != null) {
            return ChunkPos.containing(record.networkNodePos);
        }
        return null;
    }

    private static @Nullable ResourceKey<Level> positionDimension(MisakaSisterRecord record) {
        return record.lastKnownDimension != null ? record.lastKnownDimension : OVERWORLD_KEY;
    }

    private static final ResourceKey<Level> OVERWORLD_KEY =
            ResourceKey.create(Registries.DIMENSION, Identifier.withDefaultNamespace("overworld"));
}
