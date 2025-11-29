package org.academy.api.server.sync;

import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import org.academy.api.common.sync.SyncKey;

import java.util.HashMap;
import java.util.Map;

@EventBusSubscriber
public final class ServerSyncManager {
    private static final Map<SyncKey, DataSyncManager<?>> SHARED = new HashMap<>();

    public static <V> void register(SyncKey key, DataSyncManager<V> manager) {
        SHARED.put(key, manager);
    }

    public static void unregister(SyncKey key) {
        SHARED.remove(key);
    }

    @SubscribeEvent
    public static void onPlayerLoggedOut(PlayerEvent.PlayerLoggedOutEvent event) {
        for (var manager : SHARED.values()) {
            manager.remove(event.getEntity().getUUID());
        }
    }

    private ServerSyncManager() {
    }
}
