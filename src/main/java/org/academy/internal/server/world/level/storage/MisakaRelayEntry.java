package org.academy.internal.server.world.level.storage;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import org.jspecify.annotations.Nullable;

import java.util.Optional;
import java.util.UUID;

/**
 * Persisted + runtime state for one Misaka relay satellite.
 */
public final class MisakaRelayEntry {
    public enum Phase {
        ORBIT,
        LAUNCHING,
        CRASHING;

        public static final Codec<Phase> CODEC = Codec.STRING.xmap(
                name -> {
                    try {
                        return Phase.valueOf(name);
                    } catch (IllegalArgumentException ex) {
                        return ORBIT;
                    }
                },
                Enum::name
        );

        /** Orbiting or ascending — still registered; power / beam only after {@link #ORBIT}. */
        public boolean isActive() {
            return this == ORBIT || this == LAUNCHING;
        }
    }

    public static final Codec<MisakaRelayEntry> ENTRY_CODEC = RecordCodecBuilder.create(instance -> instance.group(
            MisakaSavedDataCodecs.UUID_STRING_CODEC.fieldOf("satellite_id").forGetter(e -> e.satelliteId),
            MisakaSavedDataCodecs.BLOCK_POS_STRING_CODEC.fieldOf("network_id").forGetter(e -> e.networkId),
            MisakaSavedDataCodecs.DIMENSION_CODEC.fieldOf("dimension").forGetter(e -> e.dimension),
            Codec.BOOL.fieldOf("hyper").forGetter(e -> e.hyper),
            MisakaSavedDataCodecs.BLOCK_POS_STRING_CODEC.fieldOf("laser_pos").forGetter(e -> e.laserPos),
            MisakaSavedDataCodecs.DIMENSION_CODEC.fieldOf("laser_dimension").forGetter(e -> e.laserDimension),
            MisakaSavedDataCodecs.BLOCK_POS_STRING_CODEC.fieldOf("cabin_pos").forGetter(e -> e.cabinPos),
            MisakaSavedDataCodecs.DIMENSION_CODEC.optionalFieldOf("cabin_dimension", MisakaSavedDataCodecs.DEFAULT_OVERWORLD).forGetter(e -> e.cabinDimension),
            MisakaSavedDataCodecs.UUID_STRING_CODEC.optionalFieldOf("entity_uuid").forGetter(e -> Optional.ofNullable(e.entityUuid)),
            Phase.CODEC.fieldOf("phase").orElse(Phase.ORBIT).forGetter(e -> e.phase),
            Codec.BOOL.fieldOf("laser_bound").orElse(true).forGetter(e -> e.laserBound)
    ).apply(instance, (id, networkId, dimension, hyper, laserPos, laserDimension, cabinPos, cabinDimension, entityUuid, phase, laserBound) ->
            new MisakaRelayEntry(id, networkId, dimension, hyper, laserPos, laserDimension, cabinPos, cabinDimension, entityUuid.orElse(null), phase, laserBound)
    ));

    public final UUID satelliteId;
    public BlockPos networkId;
    public ResourceKey<Level> dimension;
    public boolean hyper;
    /**
     * Last laser tower position used as orbit anchor.
     * When {@link #laserBound} is false the tower was destroyed / unbound and no longer feeds power.
     */
    public BlockPos laserPos;
    public ResourceKey<Level> laserDimension;
    public BlockPos cabinPos;
    /** Dimension of the aerospace cabin that launched this satellite. */
    public ResourceKey<Level> cabinDimension;
    public @Nullable UUID entityUuid;
    public Phase phase = Phase.ORBIT;
    /** False after the bound energy laser tower is destroyed until a new unused tower is rebound. */
    public boolean laserBound = true;
    /** Runtime: fed this server tick. */
    public transient boolean powered;
    /**
     * Runtime crash-debt ticks: increments while unfed, decreases only when a laser pays
     * recovery energy ({@link MisakaRelayRegistry#feed(UUID, boolean)} with recover). Does not reset instantly on power restore.
     */
    public transient int unpoweredTicks;
    /**
     * Runtime: ticks remaining before a cabin-ops forced crash fires.
     * {@code 0} means not armed; cancel clears it before {@link Phase#CRASHING}.
     */
    public transient int forceCrashCountdownTicks;

    public MisakaRelayEntry(
            UUID satelliteId,
            BlockPos networkId,
            ResourceKey<Level> dimension,
            boolean hyper,
            BlockPos laserPos,
            ResourceKey<Level> laserDimension,
            BlockPos cabinPos,
            @Nullable UUID entityUuid,
            Phase phase
    ) {
        this(satelliteId, networkId, dimension, hyper, laserPos, laserDimension, cabinPos, MisakaSavedDataCodecs.DEFAULT_OVERWORLD, entityUuid, phase, true);
    }

    public MisakaRelayEntry(
            UUID satelliteId,
            BlockPos networkId,
            ResourceKey<Level> dimension,
            boolean hyper,
            BlockPos laserPos,
            ResourceKey<Level> laserDimension,
            BlockPos cabinPos,
            ResourceKey<Level> cabinDimension,
            @Nullable UUID entityUuid,
            Phase phase,
            boolean laserBound
    ) {
        this.satelliteId = satelliteId;
        this.networkId = networkId.immutable();
        this.dimension = dimension;
        this.hyper = hyper;
        this.laserPos = laserPos.immutable();
        this.laserDimension = laserDimension;
        this.cabinPos = cabinPos.immutable();
        this.cabinDimension = cabinDimension == null ? MisakaSavedDataCodecs.DEFAULT_OVERWORLD : cabinDimension;
        this.entityUuid = entityUuid;
        this.phase = phase == null ? Phase.ORBIT : phase;
        this.laserBound = laserBound;
    }

    /** Backward-compatible ctor: cabin dimension defaults to overworld. */
    public MisakaRelayEntry(
            UUID satelliteId,
            BlockPos networkId,
            ResourceKey<Level> dimension,
            boolean hyper,
            BlockPos laserPos,
            ResourceKey<Level> laserDimension,
            BlockPos cabinPos,
            @Nullable UUID entityUuid,
            Phase phase,
            boolean laserBound
    ) {
        this(satelliteId, networkId, dimension, hyper, laserPos, laserDimension, cabinPos, MisakaSavedDataCodecs.DEFAULT_OVERWORLD, entityUuid, phase, laserBound);
    }
}
