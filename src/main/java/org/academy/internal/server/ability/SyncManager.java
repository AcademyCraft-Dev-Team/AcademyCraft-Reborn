package org.academy.internal.server.ability;

import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import org.academy.api.server.ability.AbilitySystemServer;
import org.academy.api.server.vanilla.MinecraftServerContext;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

public class SyncManager {
    private final Map<UUID, Set<Identifier>> playerSyncQueueMap = new ConcurrentHashMap<>();
    private final List<Runnable> pendingTasks = new CopyOnWriteArrayList<>();
    private final MinecraftServerContext context;

    public SyncManager(MinecraftServerContext context) {
        this.context = context;
    }

    public void addTask(Runnable runnable) {
        pendingTasks.add(runnable);
    }

    public void halt() {
        playerSyncQueueMap.clear();
        pendingTasks.clear();
    }

    public void onPlayerLogin(ServerPlayer player) {
        var uuid = player.getUUID();
        playerSyncQueueMap.put(uuid, ConcurrentHashMap.newKeySet());
    }

    public void onPlayerLogout(ServerPlayer player) {
        var uuid = player.getUUID();
        playerSyncQueueMap.remove(uuid);
    }

    public void schedulePlayerSync(UUID uuid, Identifier syncType) {
        playerSyncQueueMap.get(uuid).add(syncType);
    }

    public void processPendingTasks() {
        if (!pendingTasks.isEmpty()) {
            pendingTasks.forEach(Runnable::run);
            pendingTasks.clear();
        }
    }

    public void tick(ServerPlayer player) {
        if (context.getMinecraftServer().isPaused()) return;
        if (!playerSyncQueueMap.containsKey(player.getUUID())) return;

        var syncQueue = playerSyncQueueMap.get(player.getUUID());
        if (syncQueue == null || syncQueue.isEmpty()) return;

        for (var identifier : syncQueue) {
            AbilitySystemServer.SubsystemRegistry.getHandler(identifier)
                    .ifPresent(subsystem -> subsystem.processSync(player));
        }
        syncQueue.clear();
    }
}