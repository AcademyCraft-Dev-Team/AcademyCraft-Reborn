package org.academy.internal.server.world.level.storage;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
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
            ResourceKey<Level> lastKnownDimension,
            WanderStyle wanderStyle
    ) {
        private static final Codec<NetworkState> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                MisakaSavedDataCodecs.BLOCK_POS_STRING_CODEC.optionalFieldOf("network_node_pos").forGetter(state -> Optional.ofNullable(state.networkNodePos)),
                ChunkPos.CODEC.optionalFieldOf("wander_anchor_chunk").forGetter(state -> Optional.ofNullable(state.wanderAnchorChunk)),
                ChunkPos.CODEC.optionalFieldOf("last_known_chunk").forGetter(state -> Optional.ofNullable(state.lastKnownChunk)),
                MisakaSavedDataCodecs.DIMENSION_CODEC.optionalFieldOf("last_known_dimension", MisakaSavedDataCodecs.DEFAULT_OVERWORLD)
                        .forGetter(NetworkState::lastKnownDimension),
                WanderStyle.CODEC.fieldOf("wander_style").orElse(WanderStyle.FREE_MOVE).forGetter(NetworkState::wanderStyle)
        ).apply(instance, (networkNodePos, wanderAnchorChunk, lastKnownChunk, lastKnownDimension, wanderStyle) ->
                new NetworkState(
                        networkNodePos.orElse(null),
                        wanderAnchorChunk.orElse(null),
                        lastKnownChunk.orElse(null),
                        lastKnownDimension,
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
            boolean dailyCakeFavor,
            int lastHotSpringFavorDay
    ) {
        private static final Codec<DailyState> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                Codec.INT.fieldOf("last_daily_reset_day").orElse(-1).forGetter(DailyState::lastDailyResetDay),
                Codec.BOOL.fieldOf("daily_pet").orElse(false).forGetter(DailyState::dailyPet),
                Codec.BOOL.fieldOf("daily_sleep").orElse(false).forGetter(DailyState::dailySleep),
                Codec.BOOL.fieldOf("daily_social").orElse(false).forGetter(DailyState::dailySocial),
                Codec.BOOL.fieldOf("daily_favorite_favor").orElse(false).forGetter(DailyState::dailyFavoriteFavor),
                Codec.BOOL.fieldOf("daily_cake_favor").orElse(false).forGetter(DailyState::dailyCakeFavor),
                Codec.INT.fieldOf("last_hot_spring_favor_day").orElse(-1).forGetter(DailyState::lastHotSpringFavorDay)
        ).apply(instance, DailyState::new));
    }

    private record StatusFlags(
            boolean awakened,
            boolean promaxUsed,
            boolean highTierUnlocked,
            boolean isReconstruction,
            boolean incapacitated,
            boolean starving,
            int perception,
            int perceptionCap
    ) {
        private static final Codec<StatusFlags> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                Codec.BOOL.fieldOf("awakened").orElse(false).forGetter(StatusFlags::awakened),
                Codec.BOOL.fieldOf("promax_used").orElse(false).forGetter(StatusFlags::promaxUsed),
                Codec.BOOL.fieldOf("high_tier_unlocked").orElse(false).forGetter(StatusFlags::highTierUnlocked),
                Codec.BOOL.fieldOf("is_reconstruction").orElse(false).forGetter(StatusFlags::isReconstruction),
                Codec.BOOL.fieldOf("incapacitated").orElse(false).forGetter(StatusFlags::incapacitated),
                Codec.BOOL.fieldOf("starving").orElse(false).forGetter(StatusFlags::starving),
                Codec.INT.fieldOf("perception").orElse(1).forGetter(StatusFlags::perception),
                Codec.INT.fieldOf("perception_cap").orElse(100).forGetter(StatusFlags::perceptionCap)
        ).apply(instance, StatusFlags::new));
    }

    public static final Codec<MisakaSisterRecord> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            MisakaSavedDataCodecs.UUID_STRING_CODEC.fieldOf("misaka_uuid").forGetter(r -> r.misakaUuid),
            Codec.INT.fieldOf("serial").forGetter(r -> r.serial),
            MisakaPersonality.CODEC.fieldOf("personality").forGetter(r -> r.personality),
            StatusFlags.CODEC.fieldOf("status").forGetter(MisakaSisterRecord::statusSnapshot),
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
    /** Explicit reconstruction identity; set when perception reaches 101+. */
    public boolean isReconstruction;
    /** Downed instead of permanent death; stops compute and combat. */
    public boolean incapacitated;
    public int perception = 1;
    public int perceptionCap = 100;
    public boolean starving;
    public final Map<String, Integer> favorByPlayerName = new HashMap<>();
    public String lastInteractedBenevolentPlayerName = "";
    public @Nullable BlockPos networkNodePos;
    public @Nullable ChunkPos wanderAnchorChunk;
    /** Last loaded chunk; used for favor LAN proximity when the entity is unloaded. */
    public @Nullable ChunkPos lastKnownChunk;
    /** Dimension of {@link #lastKnownChunk} / last loaded position. */
    public ResourceKey<Level> lastKnownDimension = MisakaSavedDataCodecs.DEFAULT_OVERWORLD;
    public WanderStyle wanderStyle = WanderStyle.FREE_MOVE;
    public long awakeWindowEndGameTime;
    public final Set<String> awakeSpottedNames = new HashSet<>();
    public int lastDailyResetDay = -1;
    public boolean dailyPet;
    public boolean dailySleep;
    public boolean dailySocial;
    public boolean dailyFavoriteFavor;
    public boolean dailyCakeFavor;
    public int lastHotSpringFavorDay = -1;
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
            StatusFlags status,
            Map<String, Integer> favorByPlayerName,
            String lastInteractedBenevolentPlayerName,
            NetworkState network,
            AwakeState awake,
            DailyState daily,
            int rescuedDayIndex
    ) {
        var record = new MisakaSisterRecord(misakaUuid, serial, personality, rescuedDayIndex);
        record.awakened = status.awakened();
        record.promaxUsed = status.promaxUsed();
        record.highTierUnlocked = status.highTierUnlocked();
        record.isReconstruction = status.isReconstruction()
                || status.perception() >= 101
                || status.highTierUnlocked();
        record.incapacitated = status.incapacitated();
        record.perception = status.perception();
        record.perceptionCap = status.perceptionCap();
        record.starving = status.starving();
        record.favorByPlayerName.putAll(favorByPlayerName);
        record.lastInteractedBenevolentPlayerName = lastInteractedBenevolentPlayerName;
        record.networkNodePos = network.networkNodePos() == null ? null : network.networkNodePos().immutable();
        record.wanderAnchorChunk = network.wanderAnchorChunk();
        record.lastKnownChunk = network.lastKnownChunk();
        record.lastKnownDimension = network.lastKnownDimension() == null
                ? MisakaSavedDataCodecs.DEFAULT_OVERWORLD
                : network.lastKnownDimension();
        record.wanderStyle = network.wanderStyle();
        record.awakeWindowEndGameTime = awake.awakeWindowEndGameTime();
        record.awakeSpottedNames.addAll(awake.awakeSpottedNames());
        record.lastDailyResetDay = daily.lastDailyResetDay();
        record.dailyPet = daily.dailyPet();
        record.dailySleep = daily.dailySleep();
        record.dailySocial = daily.dailySocial();
        record.dailyFavoriteFavor = daily.dailyFavoriteFavor();
        record.dailyCakeFavor = daily.dailyCakeFavor();
        record.lastHotSpringFavorDay = daily.lastHotSpringFavorDay();
        return record;
    }

    private StatusFlags statusSnapshot() {
        return new StatusFlags(
                awakened,
                promaxUsed,
                highTierUnlocked,
                isReconstruction,
                incapacitated,
                starving,
                perception,
                perceptionCap
        );
    }

    private Map<String, Integer> favorSnapshot() {
        return Map.copyOf(favorByPlayerName);
    }

    private NetworkState networkSnapshot() {
        return new NetworkState(
                networkNodePos,
                wanderAnchorChunk,
                lastKnownChunk,
                lastKnownDimension == null ? MisakaSavedDataCodecs.DEFAULT_OVERWORLD : lastKnownDimension,
                wanderStyle
        );
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
                dailyCakeFavor,
                lastHotSpringFavorDay
        );
    }
}
