package org.academy.internal.common.ability.program;

import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import org.jspecify.annotations.Nullable;

import java.util.IdentityHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Server-thread facade for resumable ability-program sessions.
 */
public final class ServerProgramScheduler {
    private static final Map<MinecraftServer, ProgramSessionScheduler<SessionKey>> SCHEDULERS =
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
        return SCHEDULERS.computeIfAbsent(server, _ -> new ProgramSessionScheduler<>()).start(
                key,
                program,
                executors,
                attachment,
                fuelPerTick,
                server.getTickCount(),
                maxLifetimeTicks,
                listener
        );
    }

    public static boolean cancel(MinecraftServer server, SessionKey key) {
        requireServerThread(server);
        var scheduler = SCHEDULERS.get(server);
        return scheduler != null && scheduler.cancel(key);
    }

    public static void tick(MinecraftServer server) {
        requireServerThread(server);
        var scheduler = SCHEDULERS.get(server);
        if (scheduler != null) scheduler.tick(server.getTickCount());
    }

    public static void clear(MinecraftServer server) {
        requireServerThread(server);
        var scheduler = SCHEDULERS.remove(server);
        if (scheduler != null) scheduler.clear();
    }

    private static void requireServerThread(MinecraftServer server) {
        if (server == null || !server.isSameThread()) {
            throw new IllegalStateException("Ability programs must be scheduled on the server thread");
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
