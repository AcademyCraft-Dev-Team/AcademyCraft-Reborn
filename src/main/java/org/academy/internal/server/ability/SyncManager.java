package org.academy.internal.server.ability;

import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import org.academy.AcademyCraft;
import org.academy.api.server.vanilla.MinecraftServerContext;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

public class SyncManager {
    private static final Logger LOGGER = AcademyCraft.getLogger();

    private final Map<UUID, LivePlayer> livePlayerMap = new ConcurrentHashMap<>();
    private final List<Runnable> pendingTasks = new CopyOnWriteArrayList<>();
    private final List<AbilitySubsystem> subsystems = new CopyOnWriteArrayList<>();
    private final MinecraftServerContext context;

    public SyncManager(MinecraftServerContext context) {
        this.context = context;
    }

    public void register(AbilitySubsystem subsystem) {
        subsystems.add(subsystem);
    }

    public void addTask(Runnable runnable) {
        pendingTasks.add(runnable);
    }

    public void halt() {
        livePlayerMap.clear();
        pendingTasks.clear();
    }

    public void onPlayerLogin(ServerPlayer player) {
        var uuid = player.getUUID();
        livePlayerMap.put(uuid, new LivePlayer(uuid));
        for (var sub : subsystems) {
            sub.onPlayerLogin(player);
        }
    }

    public void onPlayerLogout(ServerPlayer player) {
        var uuid = player.getUUID();
        livePlayerMap.remove(uuid);
        for (var sub : subsystems) {
            sub.onPlayerLogout(player);
        }
    }

    public void schedulePlayerSync(UUID uuid, Identifier syncType) {
        if (livePlayerMap.containsKey(uuid)) {
            livePlayerMap.get(uuid).syncQueue.add(syncType);
        }
    }

    public void tick() {
        if (context.getMinecraftServer().isPaused()) return;

        if (!pendingTasks.isEmpty()) {
            pendingTasks.forEach(Runnable::run);
            pendingTasks.clear();
        }

        for (var livePlayer : livePlayerMap.values()) {
            var player = context.getMinecraftServer().getPlayerList().getPlayer(livePlayer.uuid);
            if (player != null) {
                for (var sub : subsystems) {
                    sub.tick(player);
                }
                tickPlayerSync(livePlayer, player);
            }
        }
    }

    private void tickPlayerSync(LivePlayer livePlayer, ServerPlayer player) {
        for (var type : livePlayer.syncQueue) {
            for (var sub : subsystems) {
                sub.processSync(player, type);
            }
        }
        livePlayer.syncQueue.clear();
    }

    public interface AbilitySubsystem {
        default void tick(ServerPlayer player) {
        }

        default void onPlayerLogin(@NotNull ServerPlayer player) {
        }

        default void onPlayerLogout(@NotNull ServerPlayer player) {
        }

        void processSync(@NotNull ServerPlayer player, @NotNull Identifier type);
    }

    private static class LivePlayer {
        public final UUID uuid;
        public final Set<Identifier> syncQueue = ConcurrentHashMap.newKeySet();

        public LivePlayer(final UUID newUuid) {
            uuid = newUuid;
        }
    }
}