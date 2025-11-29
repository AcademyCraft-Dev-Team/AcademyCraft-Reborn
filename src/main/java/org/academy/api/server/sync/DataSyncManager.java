package org.academy.api.server.sync;

import net.minecraft.server.players.PlayerList;
import org.academy.api.common.sync.DataType;
import org.academy.api.common.sync.SyncKey;
import org.academy.api.common.sync.packet.SyncDataPacket;
import org.jspecify.annotations.Nullable;
import org.misaka.MisakaNetworkServer;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public final class DataSyncManager<V> {
    private final SyncKey syncKey;
    private final DataType<V> type;
    private final Map<UUID, V> values = new HashMap<>();
    private final PlayerList playerList;

    public DataSyncManager(SyncKey syncKey, DataType<V> type, PlayerList playerList) {
        this.syncKey = syncKey;
        this.type = type;
        this.playerList = playerList;
    }

    public void set(UUID key, V value) {
        var player = playerList.getPlayer(key);
        if (player != null) {
            values.put(key, value);
            MisakaNetworkServer.sendPacket(player, new SyncDataPacket<>(syncKey, type, value));
        }
    }

    public @Nullable V get(UUID key) {
        var player = playerList.getPlayer(key);
        return player == null ? null : values.get(player.getUUID());
    }

    public void remove(UUID uuid) {
        values.remove(uuid);
    }
}