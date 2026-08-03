package org.academy.internal.common.ability.teleport;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import org.academy.AcademyCraft;

import java.util.HashMap;
import java.util.Iterator;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

@EventBusSubscriber(modid = AcademyCraft.MOD_ID)
public final class TeleportChunkForceManager {
    public static final long DEFAULT_TIMEOUT_TICKS = 200L;
    private static final Map<String, Lease> LEASES = new HashMap<>();
    private static final Map<ChunkKey, Ref> REFS = new HashMap<>();

    private TeleportChunkForceManager() {
    }

    public static synchronized void forceChunk(ServerLevel level, String operationId,
                                               int blockX, int blockZ, long timeoutTicks) {
        forceRegion(level, operationId, blockX, blockZ, blockX, blockZ, timeoutTicks);
    }

    public static synchronized void forceRegion(ServerLevel level, String operationId,
                                                int minBlockX, int minBlockZ,
                                                int maxBlockX, int maxBlockZ,
                                                long timeoutTicks) {
        if (level == null || operationId == null || operationId.isBlank()) return;
        release(operationId);
        var minX = Math.min(minBlockX, maxBlockX) >> 4;
        var maxX = Math.max(minBlockX, maxBlockX) >> 4;
        var minZ = Math.min(minBlockZ, maxBlockZ) >> 4;
        var maxZ = Math.max(minBlockZ, maxBlockZ) >> 4;
        var keys = new HashSet<ChunkKey>();
        for (var x = minX; x <= maxX && keys.size() < 64; x++) {
            for (var z = minZ; z <= maxZ && keys.size() < 64; z++) {
                var pos = new ChunkPos(x, z);
                var key = new ChunkKey(level, pos.pack());
                acquire(key, pos);
                keys.add(key);
            }
        }
        LEASES.put(operationId, new Lease(Set.copyOf(keys), level.getGameTime() + Math.max(1L, timeoutTicks)));
    }

    private static void acquire(ChunkKey key, ChunkPos pos) {
        var ref = REFS.get(key);
        if (ref == null) {
            var owned = key.level.setChunkForced(pos.x(), pos.z(), true);
            ref = new Ref(0, owned);
            REFS.put(key, ref);
        }
        ref.count++;
    }

    public static synchronized void release(String operationId) {
        var lease = LEASES.remove(operationId);
        if (lease == null) return;
        lease.keys.forEach(TeleportChunkForceManager::release);
    }

    private static void release(ChunkKey key) {
        var ref = REFS.get(key);
        if (ref == null) return;
        if (--ref.count > 0) return;
        REFS.remove(key);
        if (ref.owned) {
            var pos = ChunkPos.unpack(key.chunk);
            key.level.setChunkForced(pos.x(), pos.z(), false);
        }
    }

    @SubscribeEvent
    public static synchronized void onServerTick(ServerTickEvent.Post event) {
        var iterator = LEASES.entrySet().iterator();
        while (iterator.hasNext()) {
            var lease = iterator.next().getValue();
            if (lease.keys.stream().allMatch(key -> key.level.getGameTime() < lease.expiresAt)) continue;
            iterator.remove();
            lease.keys.forEach(TeleportChunkForceManager::release);
        }
    }

    @SubscribeEvent
    public static synchronized void onServerStopping(ServerStoppingEvent event) {
        Iterator<String> iterator = LEASES.keySet().iterator();
        while (iterator.hasNext()) {
            var lease = LEASES.get(iterator.next());
            iterator.remove();
            lease.keys.forEach(TeleportChunkForceManager::release);
        }
        REFS.clear();
    }

    private record ChunkKey(ServerLevel level, long chunk) {
    }

    private record Lease(Set<ChunkKey> keys, long expiresAt) {
    }

    private static final class Ref {
        private int count;
        private final boolean owned;

        private Ref(int count, boolean owned) {
            this.count = count;
            this.owned = owned;
        }
    }
}
