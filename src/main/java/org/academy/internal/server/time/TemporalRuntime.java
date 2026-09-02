package org.academy.internal.server.time;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.ServerTickRateManager;
import net.minecraft.server.level.ServerChunkCache;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.BlockEventData;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.NaturalSpawner;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.TickingBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.ticks.LevelTicks;
import net.minecraft.world.ticks.ScheduledTick;
import org.academy.AcademyCraft;
import org.academy.api.server.time.TemporalAccumulator;
import org.academy.api.server.time.TemporalApi;
import org.academy.api.server.time.TemporalChannel;
import org.academy.api.server.time.TemporalField;
import org.academy.api.server.time.TemporalFieldLease;
import org.academy.api.server.time.TemporalImmunityLease;
import org.academy.api.server.time.TemporalPauseSource;
import org.academy.api.server.time.TemporalService;
import org.academy.api.server.time.TemporalScope;
import org.academy.internal.common.ability.program.ServerProgramScheduler;
import org.academy.internal.common.network.TemporalImmunitySyncPacket;
import org.academy.mixin.common.LevelTicksAccessor;

import java.lang.ref.WeakReference;
import java.net.URL;
import java.security.ProtectionDomain;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.WeakHashMap;
import java.util.function.BiConsumer;

/**
 * Server-owned temporal state and anti-freeze heartbeat.
 *
 * <p>The heartbeat deliberately does not depend on {@code TickRateManager}.
 * It detects protected root entities whose normal tick did not advance and
 * supplies the missing tick from the server, level, or wall-clock boundary.</p>
 */
public final class TemporalRuntime implements TemporalService {
    private static final StackWalker STATE_STACK_WALKER = StackWalker.getInstance(
            StackWalker.Option.RETAIN_CLASS_REFERENCE
    );
    private static final boolean DEBUG = Boolean.getBoolean("academy.temporalDebug");
    private static final long SERVER_TICK_NANOS = 50_000_000L;
    private static final long MAX_WALL_CLOCK_DEBT_NANOS = 1_000_000_000L;
    private static final int MAX_WALL_CLOCK_FORCED_TICKS_PER_PASS = 2;
    private static final int MAX_LOGICAL_TICKS_PER_PASS = 8;
    private static final long STALE_ACCUMULATOR_HEARTBEATS = 400L;
    private static final Set<TemporalChannel> INTEGRATED_CHANNELS = Set.copyOf(
            EnumSet.of(
                    TemporalChannel.LEVEL_CLOCK,
                    TemporalChannel.SERVER_CLOCK,
                    TemporalChannel.WORLD_BORDER,
                    TemporalChannel.WEATHER_AND_RAID,
                    TemporalChannel.NATURAL_SPAWNING,
                    TemporalChannel.DRAGON_FIGHT,
                    TemporalChannel.ENTITY,
                    TemporalChannel.BLOCK_ENTITY,
                    TemporalChannel.SCHEDULED_BLOCK,
                    TemporalChannel.SCHEDULED_FLUID,
                    TemporalChannel.RANDOM_TICK,
                    TemporalChannel.BLOCK_EVENT,
                    TemporalChannel.ACADEMY_SCHEDULER
            )
    );
    private static final Map<LevelTicks<?>, ScheduledQueueBinding<?>>
            SCHEDULED_QUEUE_BINDINGS = Collections.synchronizedMap(
                    new WeakHashMap<>()
            );

    private final MinecraftServer server;
    private final TemporalSavedData savedData;
    private final TemporalImmunityState transientImmunities =
            new TemporalImmunityState();
    private final Map<UUID, TemporalField> temporalFields = new LinkedHashMap<>();
    private final Map<UUID, TemporalFieldLease> debugFieldLeases =
            new LinkedHashMap<>();
    private final Map<UUID, DebugImmunityGroup> debugImmunityGroups =
            new LinkedHashMap<>();
    private final Map<UUID, AccumulatorState> entityTickAccumulators =
            new HashMap<>();
    private final Map<TickingBlockEntity, AccumulatorState> blockEntityTickAccumulators =
            new IdentityHashMap<>();
    private final Map<ResourceKey<Level>, AccumulatorState> levelClockAccumulators =
            new HashMap<>();
    private final Map<String, AccumulatorState> serverClockAccumulators =
            new HashMap<>();
    private final Map<ResourceKey<Level>, AccumulatorState>
            worldBorderTickAccumulators = new HashMap<>();
    private final Map<ResourceKey<Level>, AccumulatorState> weatherTickAccumulators =
            new HashMap<>();
    private final Map<ResourceKey<Level>, AccumulatorState> raidTickAccumulators =
            new HashMap<>();
    private final Map<ResourceKey<Level>, AccumulatorState>
            customSpawnerTickAccumulators = new HashMap<>();
    private final Map<ResourceKey<Level>, AccumulatorState>
            dragonFightTickAccumulators = new HashMap<>();
    private final Map<BlockEventKey, AccumulatorState> blockEventAccumulators =
            new HashMap<>();
    private final Map<AcademySchedulerKey, AccumulatorState>
            academySchedulerAccumulators = new HashMap<>();
    private final Map<UUID, TickSnapshot> serverTickSnapshots = new HashMap<>();
    private final Map<ServerLevel, Map<UUID, TickSnapshot>> levelTickSnapshots =
            new IdentityHashMap<>();
    private final Map<UUID, Long> lastFallbackHeartbeats = new HashMap<>();
    private final Map<UUID, TemporalTickDebt> wallClockDebtStates = new HashMap<>();
    private final ThreadLocal<Set<UUID>> guardBypassStack =
            ThreadLocal.withInitial(HashSet::new);
    private final ThreadLocal<Set<UUID>> fallbackTickStack =
            ThreadLocal.withInitial(HashSet::new);
    private final ThreadLocal<Set<UUID>> scaledEntityTickStack =
            ThreadLocal.withInitial(HashSet::new);
    private final ThreadLocal<Set<ResourceKey<Level>>> scaledLevelClockStack =
            ThreadLocal.withInitial(HashSet::new);
    private final ThreadLocal<Set<ResourceKey<Level>>> scaledCustomSpawnerStack =
            ThreadLocal.withInitial(HashSet::new);
    private long heartbeat;
    private long stateRevision;
    private boolean stopped;

    public TemporalRuntime(MinecraftServer server) {
        this.server = server;
        savedData = TemporalSavedData.get(server);
    }

    @Override
    public TemporalFieldLease acquireField(TemporalField field) {
        requireServerThread();
        requireActive();
        if (field == null) {
            throw new IllegalArgumentException("Temporal field cannot be null.");
        }

        var fieldId = UUID.randomUUID();
        var owner = leaseOwnerAfterAcquire("acquireField");
        temporalFields.put(fieldId, field);
        resetScaleAccumulators();
        rebaseScheduledQueues();
        return new FieldLease(fieldId, field, owner);
    }

    @Override
    public double effectiveScale(ServerLevel level, TemporalChannel channel) {
        if (stopped || level == null || level.getServer() != server) return 1.0D;
        if (channel == TemporalChannel.SERVER_CLOCK) {
            return resolveSaveScale(channel);
        }
        return resolveScale(level.dimension(), null, null, channel);
    }

    @Override
    public double effectiveScale(Entity entity, TemporalChannel channel) {
        if (stopped || entity == null
                || !(entity.level() instanceof ServerLevel level)
                || level.getServer() != server) {
            return 1.0D;
        }
        if (channel == TemporalChannel.SERVER_CLOCK) {
            return resolveSaveScale(channel);
        }
        return resolveScale(level.dimension(), entity.position(), entity, channel);
    }

    @Override
    public double effectiveScale(
            ServerLevel level,
            BlockPos position,
            TemporalChannel channel
    ) {
        if (stopped || level == null || position == null
                || level.getServer() != server) {
            return 1.0D;
        }
        if (channel == TemporalChannel.SERVER_CLOCK) {
            return resolveSaveScale(channel);
        }
        var center = new Vec3(
                position.getX() + 0.5D,
                position.getY() + 0.5D,
                position.getZ() + 0.5D
        );
        return resolveScale(level.dimension(), center, null, channel);
    }

    /** Captures a read-only operator snapshot for {@code /academy debug tick}. */
    public TemporalTickDiagnostics debugSnapshot(
            ServerLevel level,
            BlockPos position,
            Entity entity
    ) {
        requireServerThread();
        if (level == null || level.getServer() != server) {
            throw new IllegalArgumentException(
                    "Tick diagnostics require a level owned by this runtime."
            );
        }
        if (position == null) {
            throw new IllegalArgumentException(
                    "Tick diagnostics require a sample position."
            );
        }
        if (entity != null && entity.level() != level) {
            throw new IllegalArgumentException(
                    "The diagnostic entity must be in the sampled level."
            );
        }

        var channelStates = new ArrayList<TemporalTickDiagnostics.ChannelState>();
        for (var channel : TemporalChannel.values()) {
            channelStates.add(new TemporalTickDiagnostics.ChannelState(
                    channel,
                    INTEGRATED_CHANNELS.contains(channel),
                    effectiveScale(level, channel),
                    effectiveScale(level, position, channel),
                    entity == null ? null : effectiveScale(entity, channel)
            ));
        }

        var center = Vec3.atCenterOf(position);
        var fieldStates = new ArrayList<TemporalTickDiagnostics.FieldState>();
        for (var entry : temporalFields.entrySet()) {
            var fieldId = entry.getKey();
            var field = entry.getValue();
            fieldStates.add(new TemporalTickDiagnostics.FieldState(
                    fieldId,
                    debugFieldLeases.containsKey(fieldId),
                    describeScope(field.scope()),
                    field.channels(),
                    field.scale(),
                    field.pauseSource(),
                    field.scope().contains(
                            level.dimension(),
                            center,
                            entity == null ? null : entity.getUUID()
                    )
            ));
        }

        var debugImmunities = debugImmunityGroups.entrySet().stream()
                .map(entry -> new TemporalTickDiagnostics.ImmunityControlState(
                        entry.getKey(),
                        entry.getValue().entityIds(),
                        entry.getValue().entityNames(),
                        entry.getValue().sources()
                ))
                .toList();

        var tickRateManager = level.tickRateManager();
        var vanillaState = new TemporalTickDiagnostics.VanillaTickState(
                tickRateManager.tickrate(),
                tickRateManager.isFrozen(),
                tickRateManager.runsNormally(),
                tickRateManager.isSteppingForward(),
                tickRateManager.frozenTicksToRun(),
                tickRateManager instanceof ServerTickRateManager manager
                        && manager.isSprinting()
        );
        var entityState = entity == null ? null : debugEntityState(
                level,
                entity
        );

        return new TemporalTickDiagnostics(
                level.dimension().identifier(),
                position,
                level.getGameTime(),
                level.getDefaultClockTime(),
                heartbeat,
                stopped,
                vanillaState,
                channelStates,
                fieldStates,
                debugImmunities,
                debugQueueState(
                        level.getBlockTicks(),
                        TemporalChannel.SCHEDULED_BLOCK
                ),
                debugQueueState(
                        level.getFluidTicks(),
                        TemporalChannel.SCHEDULED_FLUID
                ),
                new TemporalTickDiagnostics.AccumulatorState(
                        entityTickAccumulators.size(),
                        blockEntityTickAccumulators.size(),
                        levelClockAccumulators.size(),
                        weatherTickAccumulators.size(),
                        raidTickAccumulators.size(),
                        blockEventAccumulators.size(),
                        serverClockAccumulators.size(),
                        academySchedulerAccumulators.size(),
                        worldBorderTickAccumulators.size(),
                        customSpawnerTickAccumulators.size(),
                        dragonFightTickAccumulators.size()
                ),
                entityState
        );
    }

    /** Adds one operator-owned field for tick-system validation. */
    public UUID addDebugField(TemporalField field) {
        requireAcademyStateCaller("addDebugField");
        requireServerThread();
        requireActive();
        var lease = acquireField(field);
        debugFieldLeases.put(lease.fieldId(), lease);
        return lease.fieldId();
    }

    /** Removes one field created by the tick debugger. */
    public boolean removeDebugField(UUID fieldId) {
        requireAcademyStateCaller("removeDebugField");
        requireServerThread();
        var lease = debugFieldLeases.remove(fieldId);
        if (lease == null) return false;
        lease.close();
        return true;
    }

    /** Removes every field created by the tick debugger. */
    public int clearDebugFields() {
        requireAcademyStateCaller("clearDebugFields");
        requireServerThread();
        var leases = List.copyOf(debugFieldLeases.values());
        debugFieldLeases.clear();
        for (var lease : leases) lease.close();
        return leases.size();
    }

    /** Adds one removable immunity contribution to every selected entity. */
    public UUID addDebugImmunity(
            Collection<? extends Entity> entities,
            Set<TemporalPauseSource> sources
    ) {
        requireAcademyStateCaller("addDebugImmunity");
        requireServerThread();
        requireActive();
        if (entities == null || entities.isEmpty()) {
            throw new IllegalArgumentException(
                    "Debug immunity requires at least one entity."
            );
        }
        if (sources == null || sources.isEmpty()) {
            throw new IllegalArgumentException(
                    "Debug immunity requires at least one pause source."
            );
        }

        var uniqueEntities = new LinkedHashMap<UUID, Entity>();
        for (var entity : entities) {
            requireOwnedEntity(entity);
            uniqueEntities.put(entity.getUUID(), entity);
        }
        var leases = new ArrayList<TemporalImmunityLease>();
        try {
            for (var entity : uniqueEntities.values()) {
                leases.add(acquireImmunity(entity, sources));
            }
        } catch (RuntimeException exception) {
            for (var lease : leases) lease.close();
            throw exception;
        }

        var controlId = UUID.randomUUID();
        debugImmunityGroups.put(controlId, new DebugImmunityGroup(
                List.copyOf(uniqueEntities.keySet()),
                uniqueEntities.values().stream()
                        .map(entity -> entity.getDisplayName().getString())
                        .toList(),
                Set.copyOf(sources),
                List.copyOf(leases)
        ));
        return controlId;
    }

    /** Removes one grouped immunity contribution created by the debugger. */
    public boolean removeDebugImmunity(UUID controlId) {
        requireAcademyStateCaller("removeDebugImmunity");
        requireServerThread();
        var group = debugImmunityGroups.remove(controlId);
        if (group == null) return false;
        for (var lease : group.leases()) lease.close();
        return true;
    }

    /** Removes every immunity contribution created by the debugger. */
    public int clearDebugImmunities() {
        requireAcademyStateCaller("clearDebugImmunities");
        requireServerThread();
        var groups = List.copyOf(debugImmunityGroups.values());
        debugImmunityGroups.clear();
        for (var group : groups) {
            for (var lease : group.leases()) lease.close();
        }
        return groups.size();
    }

    /** Clears all command-owned temporal test state. */
    public int clearDebugControls() {
        requireAcademyStateCaller("clearDebugControls");
        requireServerThread();
        return clearDebugFields() + clearDebugImmunities();
    }

    @Override
    public TemporalImmunityLease acquireImmunity(
            Entity entity,
            Set<TemporalPauseSource> sources
    ) {
        requireServerThread();
        requireActive();
        requireOwnedEntity(entity);
        if (sources.isEmpty()) {
            throw new IllegalArgumentException("At least one pause source is required.");
        }

        var ownedSources = EnumSet.copyOf(sources);
        var entityId = entity.getUUID();
        var owner = leaseOwnerAfterAcquire("acquireImmunity");
        transientImmunities.acquire(entityId, ownedSources);
        publishClientState();
        return new Lease(entityId, ownedSources, owner);
    }

    @Override
    public boolean isImmune(Entity entity, TemporalPauseSource source) {
        if (stopped || entity.level().getServer() != server) return false;
        var entityId = entity.getUUID();
        return transientImmunities.isImmune(entityId, source)
                || savedData.isImmune(entityId, source);
    }

    @Override
    public boolean isTimeStopImmune(Entity entity) {
        if (stopped || entity.level().getServer() != server) return false;
        var entityId = entity.getUUID();
        return transientImmunities.hasAny(entityId) || savedData.hasAny(entityId);
    }

    /**
     * Dispatches zero or more complete root-entity ticks for one server pass.
     * Returns true when the injected outer invocation must be cancelled.
     */
    public boolean dispatchEntityTicks(ServerLevel level, Entity entity) {
        requireHookCaller("dispatchEntityTicks", ServerLevel.class);
        requireServerThread();
        if (stopped || level.getServer() != server || entity.isRemoved()) return false;

        var entityId = entity.getUUID();
        var inProgress = scaledEntityTickStack.get();
        if (inProgress.contains(entityId)) return false;

        var scale = effectiveScale(entity, TemporalChannel.ENTITY);
        var logicalTicks = logicalTicks(entityTickAccumulators, entityId, scale);
        if (logicalTicks == 1) return false;
        if (logicalTicks == 0) return true;

        inProgress.add(entityId);
        try {
            for (var index = 0; index < logicalTicks && !entity.isRemoved(); index++) {
                level.tickNonPassenger(entity);
            }
        } finally {
            inProgress.remove(entityId);
            if (inProgress.isEmpty()) scaledEntityTickStack.remove();
        }
        return true;
    }

    /** Runs a block-entity ticker according to its effective local scale. */
    public void dispatchBlockEntityTicks(
            ServerLevel level,
            TickingBlockEntity ticker
    ) {
        requireHookCaller("dispatchBlockEntityTicks", Level.class);
        requireServerThread();
        if (stopped || level.getServer() != server) {
            ticker.tick();
            return;
        }

        var scale = effectiveScale(
                level,
                ticker.getPos(),
                TemporalChannel.BLOCK_ENTITY
        );
        var logicalTicks = logicalTicks(
                blockEntityTickAccumulators,
                ticker,
                scale
        );
        for (var index = 0; index < logicalTicks && !ticker.isRemoved(); index++) {
            ticker.tick();
        }
    }

    /**
     * Dispatches the protected level clock according to its level-wide scale.
     * Returns true when the injected outer invocation must be cancelled.
     */
    public boolean dispatchLevelClockTicks(
            ServerLevel level,
            Runnable vanillaTickTime
    ) {
        requireHookCaller("dispatchLevelClockTicks", ServerLevel.class);
        requireServerThread();
        if (stopped || level.getServer() != server) return false;

        var dimension = level.dimension();
        var inProgress = scaledLevelClockStack.get();
        if (inProgress.contains(dimension)) return false;

        var scale = effectiveScale(level, TemporalChannel.LEVEL_CLOCK);
        var logicalTicks = logicalTicks(
                levelClockAccumulators,
                dimension,
                scale
        );
        if (logicalTicks == 1) return false;
        if (logicalTicks == 0) return true;

        inProgress.add(dimension);
        try {
            for (var index = 0; index < logicalTicks; index++) {
                vanillaTickTime.run();
            }
        } finally {
            inProgress.remove(dimension);
            if (inProgress.isEmpty()) scaledLevelClockStack.remove();
        }
        return true;
    }

    /** Runs the vanilla save-global clock manager without stopping I/O. */
    public void dispatchServerClockTicks(Runnable vanillaClockTick) {
        requireHookCaller("dispatchServerClockTicks", MinecraftServer.class);
        requireServerThread();
        if (stopped) {
            vanillaClockTick.run();
            return;
        }
        var logicalTicks = logicalTicks(
                serverClockAccumulators,
                "server",
                resolveSaveScale(TemporalChannel.SERVER_CLOCK)
        );
        for (var index = 0; index < logicalTicks; index++) {
            vanillaClockTick.run();
        }
    }

    /**
     * Advances one owner-bound Academy program session at its local rate.
     * The scheduler owns its logical age, so physical pauses cannot expire it.
     */
    public void dispatchAcademySchedulerTicks(
            UUID ownerId,
            UUID sessionId,
            Runnable logicalTick
    ) {
        requireHookCaller(
                "dispatchAcademySchedulerTicks",
                ServerProgramScheduler.class
        );
        requireServerThread();
        if (stopped) {
            logicalTick.run();
            return;
        }

        var owner = resolveEntity(ownerId);
        var scale = owner == null
                ? resolveSaveScale(TemporalChannel.ACADEMY_SCHEDULER)
                : effectiveScale(owner, TemporalChannel.ACADEMY_SCHEDULER);
        var key = new AcademySchedulerKey(ownerId, sessionId);
        var logicalTicks = logicalTicks(
                academySchedulerAccumulators,
                key,
                scale
        );
        for (var index = 0; index < logicalTicks; index++) {
            logicalTick.run();
        }
    }

    /** Runs the dimension weather state machine at its effective level rate. */
    public void dispatchWeatherTicks(
            ServerLevel level,
            Runnable vanillaWeatherTick
    ) {
        requireHookCaller("dispatchWeatherTicks", ServerLevel.class);
        dispatchLevelSubsystemTicks(
                level,
                vanillaWeatherTick,
                weatherTickAccumulators,
                TemporalChannel.WEATHER_AND_RAID
        );
    }

    /** Runs one dimension's world-border interpolation at its local rate. */
    public void dispatchWorldBorderTicks(
            ServerLevel level,
            Runnable vanillaWorldBorderTick
    ) {
        requireHookCaller("dispatchWorldBorderTicks", ServerLevel.class);
        dispatchLevelSubsystemTicks(
                level,
                vanillaWorldBorderTick,
                worldBorderTickAccumulators,
                TemporalChannel.WORLD_BORDER
        );
    }

    /** Scales one selected rain, snow, or ice update at its exact position. */
    public void dispatchPrecipitationTicks(
            ServerLevel level,
            BlockPos position,
            Runnable vanillaPrecipitationTick
    ) {
        requireHookCaller("dispatchPrecipitationTicks", ServerLevel.class);
        dispatchStochasticTicks(
                level,
                position,
                TemporalChannel.WEATHER_AND_RAID,
                vanillaPrecipitationTick
        );
    }

    /** Scales one chunk-local lightning attempt without pausing chunk I/O. */
    public void dispatchThunderTicks(
            ServerLevel level,
            BlockPos chunkCenter,
            Runnable vanillaThunderTick
    ) {
        requireHookCaller("dispatchThunderTicks", ServerChunkCache.class);
        dispatchStochasticTicks(
                level,
                chunkCenter,
                TemporalChannel.WEATHER_AND_RAID,
                vanillaThunderTick
        );
    }

    /** Scales one natural-spawn category attempt at its selected position. */
    public void dispatchNaturalSpawningTicks(
            ServerLevel level,
            BlockPos position,
            Runnable vanillaSpawnTick
    ) {
        requireHookCaller(
                "dispatchNaturalSpawningTicks",
                NaturalSpawner.class
        );
        dispatchStochasticTicks(
                level,
                position,
                TemporalChannel.NATURAL_SPAWNING,
                vanillaSpawnTick
        );
    }

    /** Runs dimension-wide custom spawners at their logical rate. */
    public boolean dispatchCustomSpawnerTicks(
            ServerLevel level,
            Runnable vanillaSpawnerTick
    ) {
        requireHookCaller("dispatchCustomSpawnerTicks", ServerLevel.class);
        requireServerThread();
        if (stopped || level.getServer() != server) return false;

        var dimension = level.dimension();
        var inProgress = scaledCustomSpawnerStack.get();
        if (inProgress.contains(dimension)) return false;
        var logicalTicks = logicalTicks(
                customSpawnerTickAccumulators,
                dimension,
                effectiveScale(level, TemporalChannel.NATURAL_SPAWNING)
        );
        if (logicalTicks == 1) return false;
        if (logicalTicks == 0) return true;

        inProgress.add(dimension);
        try {
            for (var index = 0; index < logicalTicks; index++) {
                vanillaSpawnerTick.run();
            }
        } finally {
            inProgress.remove(dimension);
            if (inProgress.isEmpty()) scaledCustomSpawnerStack.remove();
        }
        return true;
    }

    /** Runs the End dragon-fight state machine at its dimension rate. */
    public void dispatchDragonFightTicks(
            ServerLevel level,
            Runnable vanillaDragonFightTick
    ) {
        requireHookCaller("dispatchDragonFightTicks", ServerLevel.class);
        dispatchLevelSubsystemTicks(
                level,
                vanillaDragonFightTick,
                dragonFightTickAccumulators,
                TemporalChannel.DRAGON_FIGHT
        );
    }

    /** Runs the dimension raid manager at the same effective level rate. */
    public void dispatchRaidTicks(
            ServerLevel level,
            Runnable vanillaRaidTick
    ) {
        requireHookCaller("dispatchRaidTicks", ServerLevel.class);
        dispatchLevelSubsystemTicks(
                level,
                vanillaRaidTick,
                raidTickAccumulators,
                TemporalChannel.WEATHER_AND_RAID
        );
    }

    /**
     * Defers a one-shot block event until its local temporal credit reaches
     * one logical invocation. Events are never duplicated by acceleration.
     */
    public boolean deferBlockEvent(
            ServerLevel level,
            BlockEventData eventData
    ) {
        requireHookCaller("deferBlockEvent", ServerLevel.class);
        requireServerThread();
        if (stopped || level.getServer() != server) return false;

        var key = new BlockEventKey(
                level.dimension(),
                eventData.pos().immutable(),
                eventData.block(),
                eventData.paramA(),
                eventData.paramB()
        );
        var scale = effectiveScale(
                level,
                eventData.pos(),
                TemporalChannel.BLOCK_EVENT
        );
        if (scale >= 1.0D) {
            blockEventAccumulators.remove(key);
            return false;
        }
        return logicalTicks(blockEventAccumulators, key, scale) == 0;
    }

    /** Wraps one vanilla scheduled-tick queue pass with safe rebasing. */
    public <T> void dispatchScheduledQueue(
            ServerLevel level,
            LevelTicks<T> queue,
            long gameTime,
            int maxTicks,
            BiConsumer<BlockPos, T> callback
    ) {
        requireHookCaller("dispatchScheduledQueue", ServerLevel.class);
        requireServerThread();
        if (stopped || level.getServer() != server) {
            queue.tick(gameTime, maxTicks, callback);
            return;
        }

        var channel = scheduledChannel(level, queue);
        if (channel == null) {
            queue.tick(gameTime, maxTicks, callback);
            return;
        }
        var binding = bindScheduledQueue(queue, level, channel);
        ensureScheduledQueueRebased(binding);
        binding.dispatching = true;
        try {
            queue.tick(gameTime, maxTicks, callback);
        } finally {
            binding.dispatching = false;
            ensureScheduledQueueRebased(binding);
        }
    }

    /**
     * Rewrites a newly requested block/fluid delay into physical level ticks.
     * Called only from the protected {@link LevelAccessor#createTick} hook.
     */
    public int scaleScheduledDelay(
            ServerLevel level,
            BlockPos position,
            Object type,
            int delay
    ) {
        requireHookCaller("scaleScheduledDelay", LevelAccessor.class);
        requireServerThread();
        if (stopped || level.getServer() != server) return delay;

        var channel = scheduledChannel(type);
        if (channel == null) return delay;
        var queue = scheduledQueue(level, channel);
        var binding = bindScheduledQueueUnchecked(queue, level, channel);
        if (!binding.dispatching) ensureScheduledQueueRebasedUnchecked(binding);

        var fields = binding.dispatching
                ? binding.appliedFields : fieldSnapshot();
        var relativeScale = scheduledRelativeScale(
                fields,
                level,
                position,
                channel
        );
        var key = new ScheduledTickKey(type, position);
        if (relativeScale == 0.0D
                && !hasScheduledTickUnchecked(queue, position, type)) {
            binding.frozenRemaining.putIfAbsent(
                    key,
                    (double) Math.max(0, delay)
            );
        } else if (relativeScale > 0.0D
                && !hasScheduledTickUnchecked(queue, position, type)) {
            binding.frozenRemaining.remove(key);
        }
        return TemporalScheduledTickMath.scaleNewDelay(delay, relativeScale);
    }

    /**
     * Prevents a collected tick from entering the callback queue while its
     * local temporal channel is hard-paused.
     */
    public static <T> boolean deferScheduledTickIfPaused(
            LevelTicks<T> queue,
            ScheduledTick<T> tick
    ) {
        requireHookCaller("deferScheduledTickIfPaused", LevelTicks.class);
        ScheduledQueueBinding<T> binding;
        synchronized (SCHEDULED_QUEUE_BINDINGS) {
            @SuppressWarnings("unchecked")
            var found = (ScheduledQueueBinding<T>) SCHEDULED_QUEUE_BINDINGS.get(queue);
            binding = found;
        }
        if (binding == null || binding.runtime.stopped) return false;
        return binding.runtime.deferScheduledTick(binding, tick);
    }

    /** Dispatches one selected block random tick at its local temporal rate. */
    public void dispatchRandomBlockTick(
            BlockState originalState,
            ServerLevel level,
            BlockPos position,
            RandomSource random
    ) {
        requireHookCaller("dispatchRandomBlockTick", ServerLevel.class);
        requireServerThread();
        if (stopped || level.getServer() != server) {
            originalState.randomTick(level, position, random);
            return;
        }

        var invocations = TemporalStochasticTickMath.invocationCount(
                effectiveScale(level, position, TemporalChannel.RANDOM_TICK),
                random::nextDouble
        );
        for (var index = 0; index < invocations; index++) {
            var currentState = level.getBlockState(position);
            if (!currentState.isRandomlyTicking()) break;
            currentState.randomTick(level, position, random);
        }
    }

    /** Dispatches one selected fluid random tick at its local temporal rate. */
    public void dispatchRandomFluidTick(
            FluidState originalState,
            ServerLevel level,
            BlockPos position,
            RandomSource random
    ) {
        requireHookCaller("dispatchRandomFluidTick", ServerLevel.class);
        requireServerThread();
        if (stopped || level.getServer() != server) {
            originalState.randomTick(level, position, random);
            return;
        }

        var invocations = TemporalStochasticTickMath.invocationCount(
                effectiveScale(level, position, TemporalChannel.RANDOM_TICK),
                random::nextDouble
        );
        for (var index = 0; index < invocations; index++) {
            var currentState = level.getFluidState(position);
            if (!currentState.isRandomlyTicking()) break;
            currentState.randomTick(level, position, random);
        }
    }

    /** Internal persistence boundary for ability-state reconciliation. */
    public void setPersistentImmunity(
            Entity entity,
            Set<TemporalPauseSource> sources,
            boolean enabled
    ) {
        requireAcademyStateCaller("setPersistentImmunity");
        requireServerThread();
        requireActive();
        requireOwnedEntity(entity);
        if (sources.isEmpty()) {
            throw new IllegalArgumentException("At least one pause source is required.");
        }
        if (savedData.setPersistent(
                entity.getUUID(),
                EnumSet.copyOf(sources),
                enabled
        )) {
            publishClientState();
        }
    }

    /** Immutable transport snapshot used by the client compensation runtime. */
    public ClientStateSnapshot clientStateSnapshot() {
        requireServerThread();
        var masks = new HashMap<UUID, Integer>();
        for (var entityId : protectedEntityIds()) {
            var sources = EnumSet.noneOf(TemporalPauseSource.class);
            sources.addAll(savedData.sources(entityId));
            sources.addAll(transientImmunities.sources(entityId));
            var mask = 0;
            for (var source : sources) mask |= 1 << source.ordinal();
            if (mask != 0) masks.put(entityId, mask);
        }
        return new ClientStateSnapshot(stateRevision, Map.copyOf(masks));
    }

    /** Captures the independent server heartbeat before vanilla child ticking. */
    public void beginServerHeartbeat() {
        requireHookCaller("beginServerHeartbeat", MinecraftServer.class);
        requireServerThread();
        if (stopped) return;
        heartbeat++;
        if (heartbeat % 200L == 0L) pruneStaleAccumulators();
        snapshotTrackedEntities(null, serverTickSnapshots);
        if (heartbeat % 40L == 0L) {
            lastFallbackHeartbeats.entrySet().removeIf(
                    entry -> heartbeat - entry.getValue() > 40L
            );
        }
    }

    /** Supplies one missing tick after vanilla child ticking. */
    public void finishServerHeartbeat() {
        requireHookCaller("finishServerHeartbeat", MinecraftServer.class);
        requireServerThread();
        if (stopped) {
            serverTickSnapshots.clear();
            return;
        }
        runFallbacks(null, serverTickSnapshots, ForcedTickReason.SERVER_HEARTBEAT, true);
    }

    /** Captures protected entities belonging to one level before it ticks. */
    public void beginLevelTick(ServerLevel level) {
        requireHookCaller("beginLevelTick", ServerLevel.class);
        requireServerThread();
        if (stopped || level.getServer() != server) return;
        bindScheduledQueues(level);
        var snapshots = levelTickSnapshots.computeIfAbsent(
                level,
                ignored -> new HashMap<>()
        );
        snapshotTrackedEntities(level, snapshots);
    }

    /** Supplies one missing tick at the level boundary. */
    public void finishLevelTick(ServerLevel level) {
        requireHookCaller("finishLevelTick", ServerLevel.class);
        requireServerThread();
        var snapshots = levelTickSnapshots.remove(level);
        if (snapshots == null) return;
        if (stopped || level.getServer() != server) {
            snapshots.clear();
            return;
        }
        runFallbacks(level, snapshots, ForcedTickReason.SERVER_LEVEL, true);
    }

    /**
     * Starts the high-priority {@link Level#guardEntityTick} bypass.
     * A per-thread identity stack prevents recursive entry for the same entity.
     */
    public boolean tryEnterGuardBypass(Level level, Entity entity) {
        if (stopped || level.isClientSide() || entity.isPassenger()) return false;
        if (level.getServer() != server || !isTimeStopImmune(entity)) return false;
        requireHookCaller("tryEnterGuardBypass", Level.class);
        var inProgress = guardBypassStack.get();
        return inProgress.add(entity.getUUID());
    }

    public void exitGuardBypass(Entity entity) {
        requireHookCaller("exitGuardBypass", Level.class);
        var inProgress = guardBypassStack.get();
        inProgress.remove(entity.getUUID());
        if (inProgress.isEmpty()) guardBypassStack.remove();
    }

    /**
     * Uses elapsed wall time to keep immunity alive even if a foreign time stop
     * suppresses the ordinary server or level tick method entirely.
     */
    public void compensateWallClockDebt() {
        requireHookCaller("compensateWallClockDebt", MinecraftServer.class);
        requireServerThread();
        if (stopped) {
            wallClockDebtStates.clear();
            return;
        }

        var now = System.nanoTime();
        var active = new HashSet<UUID>();
        for (var entityId : protectedEntityIds()) {
            var entity = resolveEntity(entityId);
            if (!shouldTrack(entity, null)) continue;
            active.add(entityId);

            var state = wallClockDebtStates.computeIfAbsent(
                    entityId,
                    ignored -> new TemporalTickDebt(
                            SERVER_TICK_NANOS,
                            MAX_WALL_CLOCK_DEBT_NANOS,
                            MAX_WALL_CLOCK_FORCED_TICKS_PER_PASS,
                            now,
                            entity.tickCount
                    )
            );
            var ticksToRun = state.update(now, entity.tickCount);
            if (!(entity.level() instanceof ServerLevel level)) continue;
            for (var index = 0; index < ticksToRun; index++) {
                if (!forceTick(level, entity, ForcedTickReason.WALL_CLOCK_DEBT, false)) {
                    break;
                }
                state.consumeForcedTick(entity.tickCount);
                if (!shouldTrack(entity, level)) break;
            }
        }
        wallClockDebtStates.keySet().removeIf(entityId -> !active.contains(entityId));
    }

    public void shutdown() {
        requireServerThread();
        if (stopped) return;
        stopped = true;
        debugFieldLeases.clear();
        debugImmunityGroups.clear();
        transientImmunities.clear();
        temporalFields.clear();
        resetScaleAccumulators();
        synchronized (SCHEDULED_QUEUE_BINDINGS) {
            SCHEDULED_QUEUE_BINDINGS.values().removeIf(
                    binding -> binding.runtime == this
            );
        }
        serverTickSnapshots.clear();
        levelTickSnapshots.clear();
        lastFallbackHeartbeats.clear();
        wallClockDebtStates.clear();
        guardBypassStack.remove();
        fallbackTickStack.remove();
        scaledEntityTickStack.remove();
        scaledLevelClockStack.remove();
    }

    private void bindScheduledQueues(ServerLevel level) {
        ensureScheduledQueueRebased(bindScheduledQueue(
                level.getBlockTicks(),
                level,
                TemporalChannel.SCHEDULED_BLOCK
        ));
        ensureScheduledQueueRebased(bindScheduledQueue(
                level.getFluidTicks(),
                level,
                TemporalChannel.SCHEDULED_FLUID
        ));
    }

    private TemporalTickDiagnostics.EntityState debugEntityState(
            ServerLevel level,
            Entity entity
    ) {
        var immuneSources = EnumSet.noneOf(TemporalPauseSource.class);
        for (var source : TemporalPauseSource.values()) {
            if (isImmune(entity, source)) immuneSources.add(source);
        }
        return new TemporalTickDiagnostics.EntityState(
                entity.getUUID(),
                entity.getDisplayName().getString(),
                isTimeStopImmune(entity),
                immuneSources,
                level.tickRateManager().isEntityFrozen(entity)
        );
    }

    @SuppressWarnings("unchecked")
    private <T> TemporalTickDiagnostics.QueueState debugQueueState(
            LevelTicks<T> queue,
            TemporalChannel channel
    ) {
        var containers = ((LevelTicksAccessor<T>) queue)
                .academy$getAllContainers();
        long pendingTicks = 0L;
        for (var container : containers.values()) {
            pendingTicks += container.getAll().count();
        }

        ScheduledQueueBinding<T> binding;
        synchronized (SCHEDULED_QUEUE_BINDINGS) {
            binding = (ScheduledQueueBinding<T>) SCHEDULED_QUEUE_BINDINGS.get(queue);
        }
        var owned = binding != null && binding.runtime == this;
        return new TemporalTickDiagnostics.QueueState(
                channel,
                owned,
                clampDiagnosticCount(pendingTicks),
                owned ? binding.frozenRemaining.size() : 0,
                owned && binding.dispatching,
                owned ? clampDiagnosticCount(binding.appliedFields.stream()
                        .filter(field -> field.channels().contains(channel))
                        .count()) : 0
        );
    }

    private static int clampDiagnosticCount(long value) {
        return (int) Math.min(Integer.MAX_VALUE, Math.max(0L, value));
    }

    private static String describeScope(TemporalScope scope) {
        if (scope instanceof TemporalScope.Save) return "save";
        if (scope instanceof TemporalScope.Dimension dimension) {
            return "dimension " + dimension.dimension().identifier();
        }
        if (scope instanceof TemporalScope.Sphere sphere) {
            return String.format(
                    Locale.ROOT,
                    "sphere %s (%.1f, %.1f, %.1f) r=%.1f",
                    sphere.dimension().identifier(),
                    sphere.center().x,
                    sphere.center().y,
                    sphere.center().z,
                    sphere.radius()
            );
        }
        if (scope instanceof TemporalScope.Entities entities) {
            return "entities " + entities.entityIds().stream()
                    .map(UUID::toString)
                    .sorted()
                    .collect(java.util.stream.Collectors.joining(","));
        }
        return scope.toString();
    }

    private <T> ScheduledQueueBinding<T> bindScheduledQueue(
            LevelTicks<T> queue,
            ServerLevel level,
            TemporalChannel channel
    ) {
        synchronized (SCHEDULED_QUEUE_BINDINGS) {
            @SuppressWarnings("unchecked")
            var existing = (ScheduledQueueBinding<T>)
                    SCHEDULED_QUEUE_BINDINGS.get(queue);
            if (existing != null
                    && existing.runtime == this
                    && existing.level == level
                    && existing.channel == channel) {
                return existing;
            }
            var binding = new ScheduledQueueBinding<T>(
                    this,
                    level,
                    queue,
                    channel
            );
            SCHEDULED_QUEUE_BINDINGS.put(queue, binding);
            return binding;
        }
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private ScheduledQueueBinding<Object> bindScheduledQueueUnchecked(
            LevelTicks<?> queue,
            ServerLevel level,
            TemporalChannel channel
    ) {
        return (ScheduledQueueBinding<Object>) bindScheduledQueue(
                (LevelTicks) queue,
                level,
                channel
        );
    }

    private void rebaseScheduledQueues() {
        var ownedBindings = new ArrayList<ScheduledQueueBinding<?>>();
        synchronized (SCHEDULED_QUEUE_BINDINGS) {
            for (var binding : SCHEDULED_QUEUE_BINDINGS.values()) {
                if (binding.runtime == this) ownedBindings.add(binding);
            }
        }
        for (var binding : ownedBindings) {
            if (!binding.dispatching) {
                ensureScheduledQueueRebasedUnchecked(binding);
            }
        }
    }

    private <T> void ensureScheduledQueueRebased(
            ScheduledQueueBinding<T> binding
    ) {
        var targetFields = fieldSnapshot();
        if (binding.appliedFields.equals(targetFields)) return;
        rebaseScheduledQueue(binding, targetFields);
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private void ensureScheduledQueueRebasedUnchecked(
            ScheduledQueueBinding<?> binding
    ) {
        ensureScheduledQueueRebased((ScheduledQueueBinding) binding);
    }

    @SuppressWarnings("unchecked")
    private <T> void rebaseScheduledQueue(
            ScheduledQueueBinding<T> binding,
            List<TemporalField> targetFields
    ) {
        var queue = binding.queueReference.get();
        if (queue == null) {
            binding.frozenRemaining.clear();
            binding.appliedFields = targetFields;
            return;
        }
        var containers = ((LevelTicksAccessor<T>) queue)
                .academy$getAllContainers();
        var queuedTicks = new ArrayList<ScheduledTick<T>>();
        for (var container : containers.values()) {
            container.getAll().forEach(queuedTicks::add);
        }

        var liveKeys = new HashSet<ScheduledTickKey>();
        for (var tick : queuedTicks) {
            liveKeys.add(new ScheduledTickKey(tick.type(), tick.pos()));
        }
        binding.frozenRemaining.keySet().retainAll(liveKeys);
        if (queuedTicks.isEmpty()) {
            binding.appliedFields = targetFields;
            return;
        }

        for (var container : containers.values()) {
            container.removeIf(ignored -> true);
        }

        var now = binding.level.getGameTime();
        for (var tick : queuedTicks) {
            var key = new ScheduledTickKey(tick.type(), tick.pos());
            var oldRelativeScale = scheduledRelativeScale(
                    binding.appliedFields,
                    binding.level,
                    tick.pos(),
                    binding.channel
            );
            var temporalRemaining = TemporalScheduledTickMath.temporalRemaining(
                    now,
                    tick.triggerTick(),
                    oldRelativeScale,
                    binding.frozenRemaining.get(key)
            );
            var newRelativeScale = scheduledRelativeScale(
                    targetFields,
                    binding.level,
                    tick.pos(),
                    binding.channel
            );
            if (newRelativeScale == 0.0D) {
                binding.frozenRemaining.put(key, temporalRemaining);
            } else {
                binding.frozenRemaining.remove(key);
            }
            queue.schedule(new ScheduledTick<>(
                    tick.type(),
                    tick.pos(),
                    TemporalScheduledTickMath.rebasedTrigger(
                            now,
                            temporalRemaining,
                            newRelativeScale
                    ),
                    tick.priority(),
                    tick.subTickOrder()
            ));
        }
        binding.appliedFields = targetFields;
    }

    private <T> boolean deferScheduledTick(
            ScheduledQueueBinding<T> binding,
            ScheduledTick<T> tick
    ) {
        requireServerThread();
        if (stopped || binding.level.getServer() != server) return false;
        var relativeScale = scheduledRelativeScale(
                fieldSnapshot(),
                binding.level,
                tick.pos(),
                binding.channel
        );
        if (relativeScale != 0.0D) return false;

        var key = new ScheduledTickKey(tick.type(), tick.pos());
        binding.frozenRemaining.putIfAbsent(key, 0.0D);
        var now = binding.level.getGameTime();
        var queue = binding.queueReference.get();
        if (queue == null) return false;
        queue.schedule(new ScheduledTick<>(
                tick.type(),
                tick.pos(),
                TemporalScheduledTickMath.rebasedTrigger(
                        now,
                        binding.frozenRemaining.get(key),
                        0.0D
                ),
                tick.priority(),
                tick.subTickOrder()
        ));
        return true;
    }

    private List<TemporalField> fieldSnapshot() {
        return List.copyOf(temporalFields.values());
    }

    private static double scheduledRelativeScale(
            List<TemporalField> fields,
            ServerLevel level,
            BlockPos position,
            TemporalChannel channel
    ) {
        var center = new Vec3(
                position.getX() + 0.5D,
                position.getY() + 0.5D,
                position.getZ() + 0.5D
        );
        var scheduledScale = TemporalFieldResolver.resolve(
                fields,
                level.dimension(),
                center,
                channel,
                ignored -> false
        );
        var levelClockScale = TemporalFieldResolver.resolve(
                fields,
                level.dimension(),
                null,
                TemporalChannel.LEVEL_CLOCK,
                ignored -> false
        );
        return TemporalScheduledTickMath.relativeScale(
                scheduledScale,
                levelClockScale
        );
    }

    private static TemporalChannel scheduledChannel(
            ServerLevel level,
            LevelTicks<?> queue
    ) {
        if (queue == level.getBlockTicks()) return TemporalChannel.SCHEDULED_BLOCK;
        if (queue == level.getFluidTicks()) return TemporalChannel.SCHEDULED_FLUID;
        return null;
    }

    private static TemporalChannel scheduledChannel(Object type) {
        if (type instanceof Block) return TemporalChannel.SCHEDULED_BLOCK;
        if (type instanceof Fluid) return TemporalChannel.SCHEDULED_FLUID;
        return null;
    }

    private static LevelTicks<?> scheduledQueue(
            ServerLevel level,
            TemporalChannel channel
    ) {
        return channel == TemporalChannel.SCHEDULED_BLOCK
                ? level.getBlockTicks() : level.getFluidTicks();
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static boolean hasScheduledTickUnchecked(
            LevelTicks<?> queue,
            BlockPos position,
            Object type
    ) {
        return ((LevelTicks) queue).hasScheduledTick(position, type);
    }

    private double resolveScale(
            ResourceKey<Level> dimension,
            Vec3 position,
            Entity subject,
            TemporalChannel channel
    ) {
        return TemporalFieldResolver.resolve(
                temporalFields.values(),
                dimension,
                position,
                subject == null ? null : subject.getUUID(),
                channel,
                source -> subject != null && isImmune(subject, source)
        );
    }

    private double resolveSaveScale(TemporalChannel channel) {
        return TemporalFieldResolver.resolveSave(
                temporalFields.values(),
                channel
        );
    }

    private <K> int logicalTicks(
            Map<K, AccumulatorState> accumulators,
            K key,
            double scale
    ) {
        if (scale == 1.0D) {
            accumulators.remove(key);
            return 1;
        }
        var state = accumulators.computeIfAbsent(
                key,
                ignored -> new AccumulatorState()
        );
        state.lastAccessHeartbeat = heartbeat;
        return state.accumulator.advance(scale, MAX_LOGICAL_TICKS_PER_PASS);
    }

    private void dispatchLevelSubsystemTicks(
            ServerLevel level,
            Runnable vanillaTick,
            Map<ResourceKey<Level>, AccumulatorState> accumulators,
            TemporalChannel channel
    ) {
        requireServerThread();
        if (stopped || level.getServer() != server) {
            vanillaTick.run();
            return;
        }
        var logicalTicks = logicalTicks(
                accumulators,
                level.dimension(),
                effectiveScale(level, channel)
        );
        for (var index = 0; index < logicalTicks; index++) {
            vanillaTick.run();
        }
    }

    private void dispatchStochasticTicks(
            ServerLevel level,
            BlockPos position,
            TemporalChannel channel,
            Runnable vanillaTick
    ) {
        requireServerThread();
        if (stopped || level.getServer() != server) {
            vanillaTick.run();
            return;
        }
        var invocations = TemporalStochasticTickMath.invocationCount(
                effectiveScale(level, position, channel),
                level.getRandom()::nextDouble
        );
        for (var index = 0; index < invocations; index++) {
            vanillaTick.run();
        }
    }

    private void resetScaleAccumulators() {
        entityTickAccumulators.clear();
        blockEntityTickAccumulators.clear();
        levelClockAccumulators.clear();
        serverClockAccumulators.clear();
        worldBorderTickAccumulators.clear();
        weatherTickAccumulators.clear();
        raidTickAccumulators.clear();
        customSpawnerTickAccumulators.clear();
        dragonFightTickAccumulators.clear();
        blockEventAccumulators.clear();
        academySchedulerAccumulators.clear();
    }

    private void pruneStaleAccumulators() {
        var oldestHeartbeat = heartbeat - STALE_ACCUMULATOR_HEARTBEATS;
        entityTickAccumulators.values().removeIf(
                state -> state.lastAccessHeartbeat < oldestHeartbeat
        );
        blockEntityTickAccumulators.values().removeIf(
                state -> state.lastAccessHeartbeat < oldestHeartbeat
        );
        levelClockAccumulators.values().removeIf(
                state -> state.lastAccessHeartbeat < oldestHeartbeat
        );
        serverClockAccumulators.values().removeIf(
                state -> state.lastAccessHeartbeat < oldestHeartbeat
        );
        worldBorderTickAccumulators.values().removeIf(
                state -> state.lastAccessHeartbeat < oldestHeartbeat
        );
        weatherTickAccumulators.values().removeIf(
                state -> state.lastAccessHeartbeat < oldestHeartbeat
        );
        raidTickAccumulators.values().removeIf(
                state -> state.lastAccessHeartbeat < oldestHeartbeat
        );
        customSpawnerTickAccumulators.values().removeIf(
                state -> state.lastAccessHeartbeat < oldestHeartbeat
        );
        dragonFightTickAccumulators.values().removeIf(
                state -> state.lastAccessHeartbeat < oldestHeartbeat
        );
        blockEventAccumulators.values().removeIf(
                state -> state.lastAccessHeartbeat < oldestHeartbeat
        );
        academySchedulerAccumulators.values().removeIf(
                state -> state.lastAccessHeartbeat < oldestHeartbeat
        );
    }

    private void snapshotTrackedEntities(
            ServerLevel expectedLevel,
            Map<UUID, TickSnapshot> destination
    ) {
        destination.clear();
        for (var entityId : protectedEntityIds()) {
            var entity = resolveEntity(entityId);
            if (!shouldTrack(entity, expectedLevel)) continue;
            destination.put(entityId, new TickSnapshot(
                    entity.tickCount,
                    entity.level().dimension(),
                    heartbeat
            ));
        }
    }

    private void runFallbacks(
            ServerLevel expectedLevel,
            Map<UUID, TickSnapshot> snapshots,
            ForcedTickReason reason,
            boolean onePerHeartbeat
    ) {
        for (var entry : Map.copyOf(snapshots).entrySet()) {
            var entity = resolveEntity(entry.getKey());
            var snapshot = entry.getValue();
            if (!shouldFallback(entity, expectedLevel, snapshot)) continue;
            forceTick((ServerLevel) entity.level(), entity, reason, onePerHeartbeat);
        }
        snapshots.clear();
    }

    private boolean shouldFallback(
            Entity entity,
            ServerLevel expectedLevel,
            TickSnapshot snapshot
    ) {
        if (snapshot.heartbeat != heartbeat || !shouldTrack(entity, expectedLevel)) {
            return false;
        }
        return entity.tickCount == snapshot.tickCount
                && entity.level().dimension().equals(snapshot.dimension);
    }

    private boolean forceTick(
            ServerLevel level,
            Entity entity,
            ForcedTickReason reason,
            boolean onePerHeartbeat
    ) {
        if (!shouldTrack(entity, level)) return false;
        var entityId = entity.getUUID();
        if (onePerHeartbeat && lastFallbackHeartbeats.getOrDefault(entityId, Long.MIN_VALUE)
                == heartbeat) {
            return false;
        }

        var inProgress = fallbackTickStack.get();
        if (!inProgress.add(entityId)) return false;
        if (onePerHeartbeat) lastFallbackHeartbeats.put(entityId, heartbeat);
        var before = entity.tickCount;
        try {
            level.tickNonPassenger(entity);
            var advanced = entity.tickCount != before;
            if (!advanced && DEBUG) {
                AcademyCraft.LOGGER.warn(
                        "Temporal forced tick did not advance: entity={}, reason={}, heartbeat={}",
                        entityId,
                        reason,
                        heartbeat
                );
            }
            return advanced;
        } catch (Throwable throwable) {
            if (DEBUG) {
                AcademyCraft.LOGGER.warn(
                        "Temporal forced tick failed: entity={}, reason={}, heartbeat={}",
                        entityId,
                        reason,
                        heartbeat,
                        throwable
                );
            }
            return false;
        } finally {
            inProgress.remove(entityId);
            if (inProgress.isEmpty()) fallbackTickStack.remove();
        }
    }

    private Set<UUID> protectedEntityIds() {
        var result = new HashSet<>(savedData.entityIds());
        result.addAll(transientImmunities.entityIds());
        return result;
    }

    private void publishClientState() {
        stateRevision++;
        TemporalImmunitySyncPacket.broadcast(server, clientStateSnapshot());
    }

    private Entity resolveEntity(UUID entityId) {
        var player = server.getPlayerList().getPlayer(entityId);
        if (player != null) return player;
        for (var level : server.getAllLevels()) {
            var entity = level.getEntity(entityId);
            if (entity != null) return entity;
        }
        return null;
    }

    private boolean shouldTrack(Entity entity, ServerLevel expectedLevel) {
        if (entity == null || entity.isRemoved() || entity.isPassenger()) return false;
        if (!(entity.level() instanceof ServerLevel level) || level.getServer() != server) {
            return false;
        }
        if (expectedLevel != null && level != expectedLevel) return false;
        if (entity instanceof ServerPlayer player && player.isSpectator()) return false;
        return isTimeStopImmune(entity);
    }

    private void requireOwnedEntity(Entity entity) {
        if (entity.level().getServer() != server) {
            throw new IllegalArgumentException("Entity does not belong to this server.");
        }
    }

    private void requireServerThread() {
        if (!server.isSameThread()) {
            throw new IllegalStateException("Temporal state may only change on the server thread.");
        }
    }

    private void requireActive() {
        if (stopped) {
            throw new IllegalStateException("Temporal runtime has stopped.");
        }
    }

    private static void requireAcademyStateCaller(String entryMethod) {
        var caller = stateCallerAfter(entryMethod);
        if (!sameStateCodeSource(caller, AcademyCraft.class)) {
            throw new SecurityException("Unauthorized temporal state mutation.");
        }
    }

    private static void requireHookCaller(String entryMethod, Class<?> targetClass) {
        var caller = stateCallerAfter(entryMethod);
        if (caller != targetClass && !sameStateCodeSource(caller, AcademyCraft.class)) {
            throw new SecurityException("Unauthorized temporal lifecycle invocation.");
        }
    }

    private static Class<?> stateCallerAfter(String entryMethod) {
        return STATE_STACK_WALKER.walk(frames -> frames
                .dropWhile(frame -> frame.getDeclaringClass() != TemporalRuntime.class
                        || !frame.getMethodName().equals(entryMethod))
                .skip(1)
                .map(StackWalker.StackFrame::getDeclaringClass)
                .findFirst()
                .orElse(null));
    }

    private static Class<?> leaseOwnerAfterAcquire(String entryMethod) {
        return STATE_STACK_WALKER.walk(frames -> frames
                .dropWhile(frame -> frame.getDeclaringClass() != TemporalRuntime.class
                        || !frame.getMethodName().equals(entryMethod))
                .skip(1)
                .map(StackWalker.StackFrame::getDeclaringClass)
                .filter(type -> type != TemporalService.class && type != TemporalApi.class)
                .findFirst()
                .orElse(null));
    }

    private static Class<?> leaseCallerAfterClose() {
        return STATE_STACK_WALKER.walk(frames -> frames
                .dropWhile(frame -> frame.getDeclaringClass() != Lease.class
                        || !frame.getMethodName().equals("close"))
                .skip(1)
                .map(StackWalker.StackFrame::getDeclaringClass)
                .findFirst()
                .orElse(null));
    }

    private static Class<?> fieldLeaseCallerAfterClose() {
        return STATE_STACK_WALKER.walk(frames -> frames
                .dropWhile(frame -> frame.getDeclaringClass() != FieldLease.class
                        || !frame.getMethodName().equals("close"))
                .skip(1)
                .map(StackWalker.StackFrame::getDeclaringClass)
                .findFirst()
                .orElse(null));
    }

    private static boolean sameStateCodeSource(Class<?> left, Class<?> right) {
        if (left == null || right == null) return false;
        var leftDomain = stateProtectionDomain(left);
        var rightDomain = stateProtectionDomain(right);
        if (leftDomain != null && leftDomain == rightDomain) return true;
        var leftLocation = stateCodeSourceLocation(leftDomain);
        var rightLocation = stateCodeSourceLocation(rightDomain);
        return leftLocation != null && leftLocation.equals(rightLocation);
    }

    private static ProtectionDomain stateProtectionDomain(Class<?> type) {
        try {
            return type.getProtectionDomain();
        } catch (SecurityException ignored) {
            return null;
        }
    }

    private static URL stateCodeSourceLocation(ProtectionDomain domain) {
        return domain == null || domain.getCodeSource() == null
                ? null : domain.getCodeSource().getLocation();
    }

    private record TickSnapshot(
            int tickCount,
            ResourceKey<Level> dimension,
            long heartbeat
    ) {
    }

    private record DebugImmunityGroup(
            List<UUID> entityIds,
            List<String> entityNames,
            Set<TemporalPauseSource> sources,
            List<TemporalImmunityLease> leases
    ) {
    }

    private enum ForcedTickReason {
        SERVER_LEVEL,
        SERVER_HEARTBEAT,
        WALL_CLOCK_DEBT
    }

    private static final class AccumulatorState {
        private final TemporalAccumulator accumulator = new TemporalAccumulator();
        private long lastAccessHeartbeat;
    }

    private record BlockEventKey(
            ResourceKey<Level> dimension,
            BlockPos position,
            Block block,
            int paramA,
            int paramB
    ) {
    }

    private record AcademySchedulerKey(
            UUID ownerId,
            UUID sessionId
    ) {
    }

    private record ScheduledTickKey(Object type, BlockPos position) {
        private ScheduledTickKey {
            if (type == null || position == null) {
                throw new IllegalArgumentException(
                        "Scheduled tick key requires a type and position."
                );
            }
            position = position.immutable();
        }
    }

    private static final class ScheduledQueueBinding<T> {
        private final TemporalRuntime runtime;
        private final ServerLevel level;
        private final WeakReference<LevelTicks<T>> queueReference;
        private final TemporalChannel channel;
        private final Map<ScheduledTickKey, Double> frozenRemaining =
                new HashMap<>();
        private List<TemporalField> appliedFields = List.of();
        private boolean dispatching;

        private ScheduledQueueBinding(
                TemporalRuntime runtime,
                ServerLevel level,
                LevelTicks<T> queue,
                TemporalChannel channel
        ) {
            this.runtime = runtime;
            this.level = level;
            queueReference = new WeakReference<>(queue);
            this.channel = channel;
        }
    }

    private final class FieldLease implements TemporalFieldLease {
        private final UUID fieldId;
        private final TemporalField field;
        private final Class<?> owner;
        private boolean active = true;

        private FieldLease(
                UUID fieldId,
                TemporalField field,
                Class<?> owner
        ) {
            this.fieldId = fieldId;
            this.field = field;
            this.owner = owner;
        }

        @Override
        public UUID fieldId() {
            return fieldId;
        }

        @Override
        public TemporalField field() {
            return field;
        }

        @Override
        public boolean isActive() {
            return active && !stopped;
        }

        @Override
        public void close() {
            var caller = fieldLeaseCallerAfterClose();
            if (!sameStateCodeSource(caller, owner)) {
                throw new SecurityException("Only the temporal field owner may close it.");
            }
            requireServerThread();
            if (!active) return;
            active = false;
            if (!stopped && temporalFields.remove(fieldId) != null) {
                resetScaleAccumulators();
                rebaseScheduledQueues();
            }
        }
    }

    private final class Lease implements TemporalImmunityLease {
        private final UUID entityId;
        private final Set<TemporalPauseSource> sources;
        private final Class<?> owner;
        private boolean active = true;

        private Lease(
                UUID entityId,
                Set<TemporalPauseSource> sources,
                Class<?> owner
        ) {
            this.entityId = entityId;
            this.sources = Set.copyOf(sources);
            this.owner = owner;
        }

        @Override
        public UUID entityId() {
            return entityId;
        }

        @Override
        public Set<TemporalPauseSource> sources() {
            return sources;
        }

        @Override
        public boolean isActive() {
            return active && !stopped;
        }

        @Override
        public void close() {
            var caller = leaseCallerAfterClose();
            if (!sameStateCodeSource(caller, owner)) {
                throw new SecurityException("Only the immunity lease owner may close it.");
            }
            requireServerThread();
            if (!active) return;
            active = false;
            if (!stopped) {
                transientImmunities.release(entityId, sources);
                publishClientState();
            }
        }
    }

    public record ClientStateSnapshot(long revision, Map<UUID, Integer> masks) {
        public ClientStateSnapshot {
            masks = Map.copyOf(masks);
        }
    }
}
