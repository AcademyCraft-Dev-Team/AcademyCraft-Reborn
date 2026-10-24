package org.academy.internal.server.world.level.storage;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;
import org.academy.AcademyCraft;
import org.jspecify.annotations.Nullable;

import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Stable Misaka network identity registry.
 * Topology components map to durable {@link UUID}s that survive node add/remove when possible:
 * merges inherit the UUID of the larger side; splits keep that UUID only on the larger fragment
 * (tied fragments: the side with the lex-smallest node keeps it; the other gets a new UUID).
 */
public final class MisakaNetworkRegistry extends SavedData {
    public static final int CURRENT_DATA_VERSION = 1;

    /** Test-only override; when non-null, {@link #get(MinecraftServer)} returns it. */
    public static final AtomicReference<@Nullable MisakaNetworkRegistry> TESTING_OVERRIDE = new AtomicReference<>();

    public record NetworkMeta(UUID networkId) {
        public NetworkMeta {
            networkId = networkId == null ? new UUID(0L, 0L) : networkId;
        }

        public static final Codec<NetworkMeta> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                MisakaSavedDataCodecs.UUID_STRING_CODEC.fieldOf("network_id").forGetter(NetworkMeta::networkId)
        ).apply(instance, NetworkMeta::new));
    }

    public static final Codec<MisakaNetworkRegistry> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.INT.optionalFieldOf("data_version", 0).forGetter(r -> r.dataVersion),
            Codec.unboundedMap(MisakaSavedDataCodecs.UUID_STRING_CODEC, NetworkMeta.CODEC)
                    .fieldOf("networks")
                    .forGetter(r -> Map.copyOf(r.networks)),
            Codec.unboundedMap(MisakaSavedDataCodecs.BLOCK_POS_STRING_CODEC, MisakaSavedDataCodecs.UUID_STRING_CODEC)
                    .fieldOf("node_binding")
                    .forGetter(r -> Map.copyOf(r.nodeBinding))
    ).apply(instance, MisakaNetworkRegistry::fromCodec));

    public static final SavedDataType<MisakaNetworkRegistry> SAVED_DATA_TYPE = new SavedDataType<>(
            AcademyCraft.academy("misaka_network_registry"),
            MisakaNetworkRegistry::new,
            CODEC
    );

    private int dataVersion = CURRENT_DATA_VERSION;
    private final Map<UUID, NetworkMeta> networks = new HashMap<>();
    private final Map<BlockPos, UUID> nodeBinding = new HashMap<>();

    public MisakaNetworkRegistry() {
    }

    private static MisakaNetworkRegistry fromCodec(
            int dataVersion,
            Map<UUID, NetworkMeta> networks,
            Map<BlockPos, UUID> nodeBinding
    ) {
        var registry = new MisakaNetworkRegistry();
        registry.dataVersion = Math.max(dataVersion, CURRENT_DATA_VERSION);
        networks.forEach((id, meta) -> registry.networks.put(id, meta == null ? new NetworkMeta(id) : meta));
        nodeBinding.forEach((pos, id) -> {
            if (pos != null && id != null) {
                registry.nodeBinding.put(pos.immutable(), id);
                registry.networks.putIfAbsent(id, new NetworkMeta(id));
            }
        });
        return registry;
    }

    /** Test-only: install a stub registry. Pass null to clear. */
    public static void testingInstall(@Nullable MisakaNetworkRegistry registry) {
        TESTING_OVERRIDE.set(registry);
    }

    public static MisakaNetworkRegistry get(MinecraftServer server) {
        var override = TESTING_OVERRIDE.get();
        if (override != null) {
            return override;
        }
        return server.overworld().getDataStorage().computeIfAbsent(SAVED_DATA_TYPE);
    }

    public Optional<UUID> get(BlockPos node) {
        if (node == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(nodeBinding.get(node.immutable()));
    }

    public Map<BlockPos, UUID> nodeBindingsView() {
        return Map.copyOf(nodeBinding);
    }

    public Set<UUID> knownNetworkIds() {
        return Set.copyOf(networks.keySet());
    }

    /**
     * Resolve a stable network UUID for the wireless component containing {@code anyNodeInComponent}.
     * Creates a new UUID when the component has no prior binding.
     */
    public UUID resolveOrCreate(ServerLevel level, BlockPos anyNodeInComponent) {
        if (anyNodeInComponent == null) {
            return new UUID(0L, 0L);
        }
        var component = collectComponent(level, anyNodeInComponent.immutable());
        if (component.isEmpty()) {
            component = Set.of(anyNodeInComponent.immutable());
        }
        return assignComponent(component);
    }

    /**
     * Recompute bindings for every live wireless node component.
     * Orphaned network UUIDs keep {@link #networks} entries (governance may still reference them).
     */
    public void reconcileTopology(ServerLevel level) {
        if (level == null) {
            return;
        }
        var data = WirelessNetworkData.get(level);
        var remaining = new HashSet<>(data.getAllNodes().keySet());
        var live = new HashSet<BlockPos>();
        while (!remaining.isEmpty()) {
            var start = remaining.iterator().next();
            var component = collectComponent(level, start);
            if (component.isEmpty()) {
                remaining.remove(start);
                continue;
            }
            remaining.removeAll(component);
            live.addAll(component);
            assignComponent(component);
        }
        boolean removed = nodeBinding.keySet().removeIf(pos -> !live.contains(pos));
        if (removed) {
            setDirty();
        }
        dataVersion = CURRENT_DATA_VERSION;
    }

    /**
     * Assign / inherit a network UUID for an explicit component.
     * Merge: inherit UUID of the side with the most currently-bound nodes (ties → lex-smaller UUID).
     * Split: the fragment holding a strict majority of an old UUID keeps it; a tied fragment keeps it
     * only when it contains the lex-smallest node among all nodes still bound to that UUID;
     * every other fragment receives a fresh UUID.
     */
    public UUID assignComponent(Set<BlockPos> component) {
        if (component == null || component.isEmpty()) {
            return new UUID(0L, 0L);
        }
        var immutableComponent = new HashSet<BlockPos>();
        for (var pos : component) {
            if (pos != null) {
                immutableComponent.add(pos.immutable());
            }
        }
        if (immutableComponent.isEmpty()) {
            return new UUID(0L, 0L);
        }
        var counts = new HashMap<UUID, Integer>();
        for (var pos : immutableComponent) {
            var existing = nodeBinding.get(pos);
            if (existing != null) {
                counts.merge(existing, 1, Integer::sum);
            }
        }
        UUID chosen = null;
        int best = -1;
        for (var entry : counts.entrySet()) {
            int count = entry.getValue();
            var id = entry.getKey();
            if (count > best || (count == best && chosen != null && id.compareTo(chosen) < 0)
                    || (count == best && chosen == null)) {
                best = count;
                chosen = id;
            }
        }
        if (chosen != null && !mayInheritSplitUuid(chosen, best, immutableComponent)) {
            chosen = null;
        }
        if (chosen == null) {
            chosen = UUID.randomUUID();
        }
        networks.putIfAbsent(chosen, new NetworkMeta(chosen));
        boolean changed = false;
        for (var pos : immutableComponent) {
            var prev = nodeBinding.put(pos, chosen);
            if (!chosen.equals(prev)) {
                changed = true;
            }
        }
        if (changed) {
            setDirty();
        }
        return chosen;
    }

    /**
     * Whether this fragment may keep {@code candidate} when other live nodes still hold it
     * (split case). Merge of the full set always passes ({@code outside == 0}).
     */
    private boolean mayInheritSplitUuid(UUID candidate, int insideCount, Set<BlockPos> component) {
        int total = 0;
        BlockPos globalMin = null;
        for (var entry : nodeBinding.entrySet()) {
            if (!candidate.equals(entry.getValue())) {
                continue;
            }
            total++;
            var pos = entry.getKey();
            if (globalMin == null || compareBlockPos(pos, globalMin) < 0) {
                globalMin = pos;
            }
        }
        int outside = total - insideCount;
        if (outside <= 0) {
            return true;
        }
        if (insideCount > outside) {
            return true;
        }
        if (insideCount < outside) {
            return false;
        }
        // Equal fragments: only the side containing the lex-smallest bound node keeps the UUID.
        return globalMin != null && component.contains(globalMin);
    }

    private static int compareBlockPos(BlockPos a, BlockPos b) {
        int c = Integer.compare(a.getX(), b.getX());
        if (c != 0) {
            return c;
        }
        c = Integer.compare(a.getY(), b.getY());
        if (c != 0) {
            return c;
        }
        return Integer.compare(a.getZ(), b.getZ());
    }

    /** BFS over node↔node links in {@link WirelessNetworkData}. */
    public static Set<BlockPos> collectComponent(ServerLevel level, BlockPos nodePos) {
        var result = new HashSet<BlockPos>();
        if (level == null || nodePos == null) {
            return result;
        }
        var data = WirelessNetworkData.get(level);
        var queue = new ArrayDeque<BlockPos>();
        queue.add(nodePos.immutable());
        while (!queue.isEmpty()) {
            var current = queue.poll();
            if (!result.add(current)) {
                continue;
            }
            var config = data.getNodeConfig(current);
            if (config == null) {
                continue;
            }
            for (var userPos : config.connectedUsers.keySet()) {
                if (data.getNodeConfig(userPos) != null) {
                    queue.add(userPos.immutable());
                }
            }
        }
        return result;
    }

    /** Test hook: bind a node without topology BFS. */
    public void testingBind(BlockPos node, UUID networkId) {
        if (node == null || networkId == null) {
            return;
        }
        nodeBinding.put(node.immutable(), networkId);
        networks.putIfAbsent(networkId, new NetworkMeta(networkId));
    }

    /** Test hook: clear all bindings/meta. */
    public void testingClear() {
        networks.clear();
        nodeBinding.clear();
        dataVersion = CURRENT_DATA_VERSION;
    }
}
