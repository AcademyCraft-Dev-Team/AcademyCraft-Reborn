package org.academy.internal.common.world.entity.misaka.favor;

import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.level.ChunkPos;
import org.academy.api.common.misaka.MisakaNAT;
import org.academy.internal.common.world.entity.misaka.MobRelation;
import org.academy.internal.server.world.level.storage.MisakaSisterRecord;
import org.academy.internal.server.world.level.storage.MisakaSisterRoster;
import org.jspecify.annotations.Nullable;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;

public final class FavorService {
    /** Chebyshev chunk radius that bridges distinct Misaka networks into one favor LAN. */
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
     * Apply favor delta to the LAN component of {@code origin}:
     * same wireless network, plus sisters within {@link #LAN_PROXIMITY_CHUNKS} chunks
     * and those sisters' networks (connected-component flood).
     */
    public static void modifyFavorLan(MinecraftServer server, MisakaSisterRecord origin, String name, int delta) {
        modifyFavorLan(server, List.of(origin), name, delta);
    }

    /**
     * Union of LAN components of all seeds, applying {@code delta} once per sister.
     * Used when several sisters independently qualify for the same event (witness / killed benevolent).
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
                FavorService::positionChunk
        );
    }

    /**
     * Pure graph flood for tests: edge if same non-null network key, or chunk Chebyshev ≤ {@link #LAN_PROXIMITY_CHUNKS}.
     */
    public static List<MisakaSisterRecord> resolveLanComponent(
            MisakaSisterRecord origin,
            Collection<MisakaSisterRecord> all,
            Function<MisakaSisterRecord, @Nullable Object> networkKey,
            Function<MisakaSisterRecord, @Nullable ChunkPos> chunkPos
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
        Set<UUID> visited = new HashSet<>();
        ArrayDeque<MisakaSisterRecord> queue = new ArrayDeque<>();
        visited.add(origin.misakaUuid);
        queue.add(origin);
        List<MisakaSisterRecord> component = new ArrayList<>();
        while (!queue.isEmpty()) {
            var current = queue.poll();
            component.add(current);
            Object currentNet = networkKey.apply(current);
            ChunkPos currentChunk = chunkPos.apply(current);
            for (var other : awakened) {
                if (visited.contains(other.misakaUuid)) {
                    continue;
                }
                if (!linked(currentNet, networkKey.apply(other), currentChunk, chunkPos.apply(other))) {
                    continue;
                }
                visited.add(other.misakaUuid);
                queue.add(other);
            }
        }
        return component;
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
                && name.equals(record.lastInteractedBenevolentPlayerName);
    }

    private static boolean linked(
            @Nullable Object netA,
            @Nullable Object netB,
            @Nullable ChunkPos chunkA,
            @Nullable ChunkPos chunkB
    ) {
        if (netA != null && netA.equals(netB)) {
            return true;
        }
        if (chunkA == null || chunkB == null) {
            return false;
        }
        return Math.max(Math.abs(chunkA.x() - chunkB.x()), Math.abs(chunkA.z() - chunkB.z())) <= LAN_PROXIMITY_CHUNKS;
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
}
