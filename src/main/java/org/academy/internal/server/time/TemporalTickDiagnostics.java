package org.academy.internal.server.time;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import org.academy.api.server.time.TemporalChannel;
import org.academy.api.server.time.TemporalPauseSource;

import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/** Immutable server-thread snapshot used by the operator tick debugger. */
public record TemporalTickDiagnostics(
        Identifier dimension,
        BlockPos position,
        long gameTime,
        long clockTime,
        long heartbeat,
        boolean runtimeStopped,
        VanillaTickState vanilla,
        List<ChannelState> channels,
        List<FieldState> fields,
        List<ImmunityControlState> debugImmunities,
        QueueState scheduledBlocks,
        QueueState scheduledFluids,
        AccumulatorState accumulators,
        EntityState entity
) {
    public TemporalTickDiagnostics {
        Objects.requireNonNull(dimension, "dimension");
        Objects.requireNonNull(position, "position");
        Objects.requireNonNull(vanilla, "vanilla");
        Objects.requireNonNull(scheduledBlocks, "scheduledBlocks");
        Objects.requireNonNull(scheduledFluids, "scheduledFluids");
        Objects.requireNonNull(accumulators, "accumulators");
        position = position.immutable();
        channels = List.copyOf(channels);
        fields = List.copyOf(fields);
        debugImmunities = List.copyOf(debugImmunities);
    }

    public record VanillaTickState(
            float tickRate,
            boolean frozen,
            boolean runsNormally,
            boolean stepping,
            int frozenTicksToRun,
            boolean sprinting
    ) {
    }

    public record ChannelState(
            TemporalChannel channel,
            boolean integrated,
            double levelScale,
            double localScale,
            Double entityScale
    ) {
        public ChannelState {
            Objects.requireNonNull(channel, "channel");
        }
    }

    public record FieldState(
            UUID id,
            boolean debugControlled,
            String scope,
            Set<TemporalChannel> channels,
            double scale,
            TemporalPauseSource pauseSource,
            boolean matchesPosition
    ) {
        public FieldState {
            Objects.requireNonNull(id, "id");
            Objects.requireNonNull(scope, "scope");
            Objects.requireNonNull(channels, "channels");
            Objects.requireNonNull(pauseSource, "pauseSource");
            channels = Set.copyOf(channels);
        }
    }

    public record ImmunityControlState(
            UUID id,
            List<UUID> entityIds,
            List<String> entityNames,
            Set<TemporalPauseSource> sources
    ) {
        public ImmunityControlState {
            Objects.requireNonNull(id, "id");
            entityIds = List.copyOf(entityIds);
            entityNames = List.copyOf(entityNames);
            sources = Set.copyOf(sources);
        }
    }

    public record QueueState(
            TemporalChannel channel,
            boolean bound,
            int pendingTicks,
            int frozenTicks,
            boolean dispatching,
            int applicableFieldCount
    ) {
        public QueueState {
            Objects.requireNonNull(channel, "channel");
        }
    }

    public record AccumulatorState(
            int entities,
            int blockEntities,
            int levelClocks,
            int weather,
            int raids,
            int blockEvents,
            int serverClocks,
            int academySchedulers
    ) {
    }

    public record EntityState(
            UUID id,
            String name,
            boolean timeStopImmune,
            Set<TemporalPauseSource> immuneSources,
            boolean frozenByEffectiveVanillaManager
    ) {
        public EntityState {
            Objects.requireNonNull(id, "id");
            Objects.requireNonNull(name, "name");
            Objects.requireNonNull(immuneSources, "immuneSources");
            immuneSources = Set.copyOf(immuneSources);
        }
    }
}
