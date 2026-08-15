package org.academy.internal.server.ability;

import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import org.academy.api.server.ability.AbilitySystemServer;
import org.academy.api.server.vanilla.MinecraftServerContext;
import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

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
        registerPlayer(player.getUUID());
    }

    public void onPlayerLogout(ServerPlayer player) {
        unregisterPlayer(player.getUUID());
    }

    /**
     * Queues a sync only while the player is registered with this manager. Ability cleanup may
     * still mutate persistent data after the logout hook has removed that registration; those
     * late writes must remain valid without recreating an offline network queue.
     */
    public boolean schedulePlayerSync(UUID uuid, Identifier syncType) {
        if (uuid == null || syncType == null) return false;
        return playerSyncQueueMap.computeIfPresent(uuid, (_, syncQueue) -> {
            syncQueue.add(syncType);
            return syncQueue;
        }) != null;
    }

    void registerPlayer(UUID uuid) {
        if (uuid != null) playerSyncQueueMap.put(uuid, ConcurrentHashMap.newKeySet());
    }

    void unregisterPlayer(UUID uuid) {
        if (uuid != null) playerSyncQueueMap.remove(uuid);
    }

    @Nullable ServerPlayer getOnlinePlayer(UUID uuid) {
        return context.getMinecraftServer().getPlayerList().getPlayer(uuid);
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
