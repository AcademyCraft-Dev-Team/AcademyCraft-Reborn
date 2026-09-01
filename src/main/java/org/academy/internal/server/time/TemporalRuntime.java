package org.academy.internal.server.time;

import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import org.academy.AcademyCraft;
import org.academy.api.server.time.TemporalApi;
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

    private final MinecraftServer server;
    private final TemporalSavedData savedData;
    private final TemporalImmunityState transientImmunities =
            new TemporalImmunityState();
    private final Map<UUID, TickSnapshot> serverTickSnapshots = new HashMap<>();
    private final Map<ServerLevel, Map<UUID, TickSnapshot>> levelTickSnapshots =
            new IdentityHashMap<>();
    private final Map<UUID, Long> lastFallbackHeartbeats = new HashMap<>();
    private final Map<UUID, TemporalTickDebt> wallClockDebtStates = new HashMap<>();
    private final ThreadLocal<Set<UUID>> guardBypassStack =
            ThreadLocal.withInitial(HashSet::new);
    private final ThreadLocal<Set<UUID>> fallbackTickStack =
            ThreadLocal.withInitial(HashSet::new);
    private long heartbeat;
    private long stateRevision;
    private boolean stopped;

    public TemporalRuntime(MinecraftServer server) {
        this.server = server;
        savedData = TemporalSavedData.get(server);
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
        var owner = leaseOwnerAfterAcquire();
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
        serverTickSnapshots.clear();
        levelTickSnapshots.clear();
        lastFallbackHeartbeats.clear();
        wallClockDebtStates.clear();
        guardBypassStack.remove();
        fallbackTickStack.remove();
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

    private static Class<?> leaseOwnerAfterAcquire() {
        return STATE_STACK_WALKER.walk(frames -> frames
                .dropWhile(frame -> frame.getDeclaringClass() != TemporalRuntime.class
                        || !frame.getMethodName().equals("acquireImmunity"))
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
