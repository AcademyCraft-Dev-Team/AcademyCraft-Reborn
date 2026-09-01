package org.academy.internal.server.time;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.TickingBlockEntity;
import net.minecraft.world.phys.Vec3;
import org.academy.AcademyCraft;
import org.academy.api.server.time.TemporalAccumulator;
import org.academy.api.server.time.TemporalApi;
import org.academy.api.server.time.TemporalChannel;
import org.academy.api.server.time.TemporalField;
import org.academy.api.server.time.TemporalFieldLease;
import org.academy.api.server.time.TemporalImmunityLease;
import org.academy.api.server.time.TemporalPauseSource;
import org.academy.api.server.time.TemporalService;
import org.academy.internal.common.network.TemporalImmunitySyncPacket;

import java.net.URL;
import java.security.ProtectionDomain;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

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

    private final MinecraftServer server;
    private final TemporalSavedData savedData;
    private final TemporalImmunityState transientImmunities =
            new TemporalImmunityState();
    private final Map<UUID, TemporalField> temporalFields = new LinkedHashMap<>();
    private final Map<UUID, AccumulatorState> entityTickAccumulators =
            new HashMap<>();
    private final Map<TickingBlockEntity, AccumulatorState> blockEntityTickAccumulators =
            new IdentityHashMap<>();
    private final Map<ResourceKey<Level>, AccumulatorState> levelClockAccumulators =
            new HashMap<>();
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
        return new FieldLease(fieldId, field, owner);
    }

    @Override
    public double effectiveScale(ServerLevel level, TemporalChannel channel) {
        if (stopped || level == null || level.getServer() != server) return 1.0D;
        return resolveScale(level.dimension(), null, null, channel);
    }

    @Override
    public double effectiveScale(Entity entity, TemporalChannel channel) {
        if (stopped || entity == null
                || !(entity.level() instanceof ServerLevel level)
                || level.getServer() != server) {
            return 1.0D;
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
        var center = new Vec3(
                position.getX() + 0.5D,
                position.getY() + 0.5D,
                position.getZ() + 0.5D
        );
        return resolveScale(level.dimension(), center, null, channel);
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
        transientImmunities.clear();
        temporalFields.clear();
        resetScaleAccumulators();
        serverTickSnapshots.clear();
        levelTickSnapshots.clear();
        lastFallbackHeartbeats.clear();
        wallClockDebtStates.clear();
        guardBypassStack.remove();
        fallbackTickStack.remove();
        scaledEntityTickStack.remove();
        scaledLevelClockStack.remove();
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
                channel,
                source -> subject != null && isImmune(subject, source)
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

    private void resetScaleAccumulators() {
        entityTickAccumulators.clear();
        blockEntityTickAccumulators.clear();
        levelClockAccumulators.clear();
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

    private enum ForcedTickReason {
        SERVER_LEVEL,
        SERVER_HEARTBEAT,
        WALL_CLOCK_DEBT
    }

    private static final class AccumulatorState {
        private final TemporalAccumulator accumulator = new TemporalAccumulator();
        private long lastAccessHeartbeat;
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
