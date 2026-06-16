package org.academy.api.client.sync;

import org.academy.api.common.sync.SyncKey;
import org.academy.api.common.sync.packet.SyncDataPacket;
import org.academy.api.common.util.UncheckedUtil;
import org.misaka.MisakaNetworkClient;
import org.misaka.api.common.network.annotation.SubscribePacket;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

public final class ClientSyncManager {
    public static final Map<SyncKey, Consumer<?>> SYNC_MAP = new ConcurrentHashMap<>();

    public static void init() {
        MisakaNetworkClient.NETWORK_MANAGER.register(ClientSyncManager.class);
    }

    @SubscribePacket
    public static <V> void onSync(SyncDataPacket<V> packet) {
        var key = packet.getSyncKey();
        if (SYNC_MAP.containsKey(key)) {
            UncheckedUtil.<Consumer<V>>uncheckedCast(SYNC_MAP.get(key)).accept(packet.getValue());
        }
    }

    public static <V> void register(SyncKey syncKey, Consumer<V> setter) {
        SYNC_MAP.put(syncKey, setter);
    }

    private ClientSyncManager() {
    }
}