package org.academy.internal.server.pvp;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;
import org.academy.AcademyCraft;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** Persistent, server-authoritative PVP switch cooldowns measured in online player ticks. */
public final class PvpCooldownData extends SavedData {
    private static final Codec<UUID> UUID_CODEC = Codec.STRING.comapFlatMap(
            value -> {
                try {
                    return DataResult.success(UUID.fromString(value));
                } catch (IllegalArgumentException exception) {
                    return DataResult.error(() -> "Invalid UUID: " + value);
                }
            },
            UUID::toString
    );
    public static final Codec<PvpCooldownData> CODEC = RecordCodecBuilder.create(instance ->
            instance.group(
                    Codec.unboundedMap(UUID_CODEC, Codec.INT)
                            .fieldOf("remaining_ticks")
                            .forGetter(PvpCooldownData::snapshot)
            ).apply(instance, PvpCooldownData::new)
    );
    public static final SavedDataType<PvpCooldownData> SAVED_DATA_TYPE = new SavedDataType<>(
            AcademyCraft.academy("pvp_cooldowns"),
            PvpCooldownData::new,
            CODEC
    );

    private final Map<UUID, Integer> remainingTicks;

    public PvpCooldownData() {
        remainingTicks = new HashMap<>();
    }

    private PvpCooldownData(Map<UUID, Integer> remainingTicks) {
        this.remainingTicks = new HashMap<>();
        remainingTicks.forEach((playerId, ticks) -> {
            if (playerId != null && ticks != null && ticks > 0) {
                this.remainingTicks.put(playerId, ticks);
            }
        });
    }

    public static PvpCooldownData get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(SAVED_DATA_TYPE);
    }

    public synchronized int remainingTicks(UUID playerId) {
        if (playerId == null) return 0;
        return Math.max(0, remainingTicks.getOrDefault(playerId, 0));
    }

    public synchronized void startOrRefresh(UUID playerId, int durationTicks) {
        if (playerId == null || durationTicks <= 0) return;
        if (remainingTicks.getOrDefault(playerId, 0) == durationTicks) return;
        remainingTicks.put(playerId, durationTicks);
        setDirty();
    }

    /** Advances one online tick for exactly one UUID; offline UUIDs are deliberately untouched. */
    public synchronized void tickOnline(UUID playerId) {
        if (playerId == null) return;
        var remaining = remainingTicks.get(playerId);
        if (remaining == null) return;
        if (remaining <= 1) remainingTicks.remove(playerId);
        else remainingTicks.put(playerId, remaining - 1);
        setDirty();
    }

    private synchronized Map<UUID, Integer> snapshot() {
        return Map.copyOf(remainingTicks);
    }
}
