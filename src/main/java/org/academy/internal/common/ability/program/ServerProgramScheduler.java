package org.academy.internal.common.ability.program;

import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import org.academy.api.server.time.TemporalApi;
import org.academy.internal.server.time.TemporalRuntime;
import org.jspecify.annotations.Nullable;

import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Server-thread facade for resumable ability-program sessions.
 */
public final class ServerProgramScheduler {
    private static final Map<MinecraftServer, SchedulerState> SCHEDULERS =
            new IdentityHashMap<>();

    private ServerProgramScheduler() {
    }

    public static boolean start(
            MinecraftServer server,
            SessionKey key,
            CompiledProgram program,
            ProgramExecutorLookup executors,
            @Nullable Object attachment,
            int fuelPerTick,
            long maxLifetimeTicks,
            ProgramSessionScheduler.SessionListener<SessionKey> listener
    ) {
        requireServerThread(server);
        return SCHEDULERS.computeIfAbsent(server, _ -> new SchedulerState()).start(
                key,
                program,
                executors,
                attachment,
                fuelPerTick,
                maxLifetimeTicks,
                listener
        );
    }

    public static boolean cancel(MinecraftServer server, SessionKey key) {
        requireServerThread(server);
        var state = SCHEDULERS.get(server);
        return state != null && state.cancel(key);
    }

    public static void tick(MinecraftServer server) {
        requireServerThread(server);
        var state = SCHEDULERS.get(server);
        if (state == null) return;
        var temporal = (TemporalRuntime) TemporalApi.get(server);
        for (var key : state.keys()) {
            var sessionId = state.sessionId(key);
            if (sessionId == null) continue;
            temporal.dispatchAcademySchedulerTicks(
                    key.ownerId(),
                    sessionId,
                    () -> state.tick(key)
            );
        }
    }

    public static void clear(MinecraftServer server) {
        requireServerThread(server);
        var state = SCHEDULERS.remove(server);
        if (state != null) state.clear();
    }

    private static void requireServerThread(MinecraftServer server) {
        if (server == null || !server.isSameThread()) {
            throw new IllegalStateException("Ability programs must be scheduled on the server thread");
        }
    }

    private static final class SchedulerState {
        private final ProgramSessionScheduler<SessionKey> scheduler =
                new ProgramSessionScheduler<>();
        private final Map<SessionKey, Long> logicalTicks = new HashMap<>();
        private final Map<SessionKey, UUID> sessionIds = new HashMap<>();

        private boolean start(
                SessionKey key,
                CompiledProgram program,
                ProgramExecutorLookup executors,
                @Nullable Object attachment,
                int fuelPerTick,
                long maxLifetimeTicks,
                ProgramSessionScheduler.SessionListener<SessionKey> listener
        ) {
            var started = scheduler.start(
                    key,
                    program,
                    executors,
                    attachment,
                    fuelPerTick,
                    0L,
                    maxLifetimeTicks,
                    listener
            );
            if (started) {
                logicalTicks.put(key, 0L);
                sessionIds.put(key, UUID.randomUUID());
            }
            return started;
        }

        private void tick(SessionKey key) {
            var logicalTick = logicalTicks.get(key);
            if (logicalTick == null || !scheduler.contains(key)) {
                logicalTicks.remove(key);
                sessionIds.remove(key);
                return;
            }
            scheduler.tick(key, logicalTick);
            if (scheduler.contains(key)) {
                logicalTicks.put(key, logicalTick + 1L);
            } else {
                logicalTicks.remove(key);
                sessionIds.remove(key);
            }
        }

        private boolean cancel(SessionKey key) {
            logicalTicks.remove(key);
            sessionIds.remove(key);
            return scheduler.cancel(key);
        }

        private void clear() {
            scheduler.clear();
            logicalTicks.clear();
            sessionIds.clear();
        }

        private java.util.List<SessionKey> keys() {
            return scheduler.keys();
        }

        @Nullable
        private UUID sessionId(SessionKey key) {
            return sessionIds.get(key);
        }
    }

    public record SessionKey(UUID ownerId, Identifier category, UUID programId) {
        public SessionKey {
            if (ownerId == null || category == null || programId == null) {
                throw new IllegalArgumentException("Program session key fields cannot be null");
            }
        }
    }
}
