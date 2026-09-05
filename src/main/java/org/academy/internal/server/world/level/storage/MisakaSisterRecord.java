package org.academy.internal.server.world.level.storage;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.ChunkPos;
import org.academy.internal.common.world.entity.misaka.MisakaPersonality;
import org.academy.internal.common.world.entity.misaka.WanderStyle;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

public final class MisakaSisterRecord {
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
    private static final Codec<Set<String>> STRING_SET_CODEC = Codec.STRING.listOf().xmap(
            HashSet::new,
            set -> new ArrayList<>(set)
    );
    private static final Codec<Map<String, Integer>> FAVOR_MAP_CODEC =
            Codec.unboundedMap(Codec.STRING, Codec.INT);

    private record NetworkState(
            @Nullable BlockPos networkNodePos,
            @Nullable ChunkPos wanderAnchorChunk,
            @Nullable ChunkPos lastKnownChunk,
            WanderStyle wanderStyle
    ) {
        private static final Codec<NetworkState> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                BLOCK_POS_CODEC.optionalFieldOf("network_node_pos").forGetter(state -> Optional.ofNullable(state.networkNodePos)),
                ChunkPos.CODEC.optionalFieldOf("wander_anchor_chunk").forGetter(state -> Optional.ofNullable(state.wanderAnchorChunk)),
                ChunkPos.CODEC.optionalFieldOf("last_known_chunk").forGetter(state -> Optional.ofNullable(state.lastKnownChunk)),
                WanderStyle.CODEC.fieldOf("wander_style").orElse(WanderStyle.FREE_MOVE).forGetter(NetworkState::wanderStyle)
        ).apply(instance, (networkNodePos, wanderAnchorChunk, lastKnownChunk, wanderStyle) ->
                new NetworkState(
                        networkNodePos.orElse(null),
                        wanderAnchorChunk.orElse(null),
                        lastKnownChunk.orElse(null),
                        wanderStyle
                )));
    }

    private record AwakeState(long awakeWindowEndGameTime, Set<String> awakeSpottedNames) {
        private static final Codec<AwakeState> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                Codec.LONG.fieldOf("awake_window_end_game_time").orElse(0L).forGetter(AwakeState::awakeWindowEndGameTime),
                STRING_SET_CODEC.fieldOf("awake_spotted_names").orElse(Set.of()).forGetter(AwakeState::awakeSpottedNames)
        ).apply(instance, AwakeState::new));
    }

    private record DailyState(
            int lastDailyResetDay,
            boolean dailyPet,
            boolean dailySleep,
            boolean dailySocial,
            boolean dailyFavoriteFavor,
            boolean dailyCakeFavor
    ) {
        private static final Codec<DailyState> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                Codec.INT.fieldOf("last_daily_reset_day").orElse(-1).forGetter(DailyState::lastDailyResetDay),
                Codec.BOOL.fieldOf("daily_pet").orElse(false).forGetter(DailyState::dailyPet),
                Codec.BOOL.fieldOf("daily_sleep").orElse(false).forGetter(DailyState::dailySleep),
                Codec.BOOL.fieldOf("daily_social").orElse(false).forGetter(DailyState::dailySocial),
                Codec.BOOL.fieldOf("daily_favorite_favor").orElse(false).forGetter(DailyState::dailyFavoriteFavor),
                Codec.BOOL.fieldOf("daily_cake_favor").orElse(false).forGetter(DailyState::dailyCakeFavor)
        ).apply(instance, DailyState::new));
    }

    public static final Codec<MisakaSisterRecord> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            UUID_CODEC.fieldOf("misaka_uuid").forGetter(r -> r.misakaUuid),
            Codec.INT.fieldOf("serial").forGetter(r -> r.serial),
            MisakaPersonality.CODEC.fieldOf("personality").forGetter(r -> r.personality),
            Codec.BOOL.fieldOf("awakened").orElse(false).forGetter(r -> r.awakened),
            Codec.BOOL.fieldOf("promax_used").orElse(false).forGetter(r -> r.promaxUsed),
            Codec.BOOL.fieldOf("high_tier_unlocked").orElse(false).forGetter(r -> r.highTierUnlocked),
            Codec.INT.fieldOf("perception").orElse(1).forGetter(r -> r.perception),
            Codec.INT.fieldOf("perception_cap").orElse(100).forGetter(r -> r.perceptionCap),
            Codec.BOOL.fieldOf("starving").orElse(false).forGetter(r -> r.starving),
            FAVOR_MAP_CODEC.fieldOf("favor_by_player").orElse(Map.of()).forGetter(MisakaSisterRecord::favorSnapshot),
            Codec.STRING.fieldOf("last_interacted_benevolent").orElse("").forGetter(r -> r.lastInteractedBenevolentPlayerName),
            NetworkState.CODEC.fieldOf("network").forGetter(MisakaSisterRecord::networkSnapshot),
            AwakeState.CODEC.fieldOf("awake").forGetter(MisakaSisterRecord::awakeSnapshot),
            DailyState.CODEC.fieldOf("daily").forGetter(MisakaSisterRecord::dailySnapshot),
            Codec.INT.fieldOf("rescued_day_index").forGetter(r -> r.rescuedDayIndex)
    ).apply(instance, MisakaSisterRecord::fromCodec));

    public final UUID misakaUuid;
    public final int serial;
    public MisakaPersonality personality;
    public boolean awakened;
    public boolean promaxUsed;
    public boolean highTierUnlocked;
    public int perception = 1;
    public int perceptionCap = 100;
    public boolean starving;
    public final Map<String, Integer> favorByPlayerName = new HashMap<>();
    public String lastInteractedBenevolentPlayerName = "";
    public @Nullable BlockPos networkNodePos;
    public @Nullable ChunkPos wanderAnchorChunk;
    /** Last loaded chunk; used for favor LAN proximity when the entity is unloaded. */
    public @Nullable ChunkPos lastKnownChunk;
    public WanderStyle wanderStyle = WanderStyle.FREE_MOVE;
    public long awakeWindowEndGameTime;
    public final Set<String> awakeSpottedNames = new HashSet<>();
    public int lastDailyResetDay = -1;
    public boolean dailyPet;
    public boolean dailySleep;
    public boolean dailySocial;
    public boolean dailyFavoriteFavor;
    public boolean dailyCakeFavor;
    public final int rescuedDayIndex;

    public MisakaSisterRecord(UUID misakaUuid, int serial, MisakaPersonality personality, int rescuedDayIndex) {
        this.misakaUuid = misakaUuid;
        this.serial = serial;
        this.personality = personality;
        this.rescuedDayIndex = rescuedDayIndex;
    }

    private static MisakaSisterRecord fromCodec(
            UUID misakaUuid,
            int serial,
            MisakaPersonality personality,
            boolean awakened,
            boolean promaxUsed,
            boolean highTierUnlocked,
            int perception,
            int perceptionCap,
            boolean starving,
            Map<String, Integer> favorByPlayerName,
            String lastInteractedBenevolentPlayerName,
            NetworkState network,
            AwakeState awake,
            DailyState daily,
            int rescuedDayIndex
    ) {
        var record = new MisakaSisterRecord(misakaUuid, serial, personality, rescuedDayIndex);
        record.awakened = awakened;
        record.promaxUsed = promaxUsed;
        record.highTierUnlocked = highTierUnlocked;
        record.perception = perception;
        record.perceptionCap = perceptionCap;
        record.starving = starving;
        record.favorByPlayerName.putAll(favorByPlayerName);
        record.lastInteractedBenevolentPlayerName = lastInteractedBenevolentPlayerName;
        record.networkNodePos = network.networkNodePos() == null ? null : network.networkNodePos().immutable();
        record.wanderAnchorChunk = network.wanderAnchorChunk();
        record.lastKnownChunk = network.lastKnownChunk();
        record.wanderStyle = network.wanderStyle();
        record.awakeWindowEndGameTime = awake.awakeWindowEndGameTime();
        record.awakeSpottedNames.addAll(awake.awakeSpottedNames());
        record.lastDailyResetDay = daily.lastDailyResetDay();
        record.dailyPet = daily.dailyPet();
        record.dailySleep = daily.dailySleep();
        record.dailySocial = daily.dailySocial();
        record.dailyFavoriteFavor = daily.dailyFavoriteFavor();
        record.dailyCakeFavor = daily.dailyCakeFavor();
        return record;
    }

    private Map<String, Integer> favorSnapshot() {
        return Map.copyOf(favorByPlayerName);
    }

    private NetworkState networkSnapshot() {
        return new NetworkState(networkNodePos, wanderAnchorChunk, lastKnownChunk, wanderStyle);
    }

    private AwakeState awakeSnapshot() {
        return new AwakeState(awakeWindowEndGameTime, Set.copyOf(awakeSpottedNames));
    }

    private DailyState dailySnapshot() {
        return new DailyState(
                lastDailyResetDay,
                dailyPet,
                dailySleep,
                dailySocial,
                dailyFavoriteFavor,
                dailyCakeFavor
        );
    }
}
