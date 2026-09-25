package org.academy.internal.common.ability.teleport;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Util;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import org.academy.AcademyCraft;
import org.jspecify.annotations.Nullable;
import org.misaka.MisakaNetworkServer;

import java.util.*;

/**
 * Streams the 区块跃迁 map to clients: keeps the viewed rectangle loaded, rasterises
 * {@link MapTileBuilder#DETAIL}×{@link MapTileBuilder#DETAIL} tiles per chunk on a per-tick budget, and
 * pushes entity markers at a throttled cadence.
 *
 * <p>Chunks loaded for the map are held by a non-persistent, non-simulating ticket, so exploring the map
 * neither ticks entities at range nor records the world as visited. Because those chunks sit outside the
 * player's chunk-tracking view, {@code ChunkMap} does not ship real terrain for them — the raster below
 * is the only thing the client receives.
 *
 * <p>Two properties matter more than raw throughput:
 * <ul>
 *   <li>Chunks that are still generating are <em>retried</em>, not dropped. An earlier version consumed
 *       the queue once, so on a dimension whose chunks start unloaded (any page other than the player's
 *       own) nearly every tile was lost and the map stayed blank.</li>
 *   <li>The loaded rectangle is bounded by the player's own view distance, halved. A map page must never
 *       be able to pull a huge region of terrain into memory just because a client asked for it.</li>
 * </ul>
 */
@EventBusSubscriber(modid = AcademyCraft.MOD_ID)
public final class ChunkMapViewService {
    /**
     * Tiles rasterised per tick, per player. Detail sampling makes each chunk substantially more work
     * than a single texel, so the millisecond guard is what actually bounds this.
     */
    private static final int TILE_BUDGET_PER_TICK = 48;
    /**
     * Soft wall-clock budget per player per tick, in milliseconds.
     */
    private static final long TILE_TIME_BUDGET_MS = 4L;
    /**
     * Entity marker refresh cadence, in ticks, when the client does not ask for a specific rate.
     */
    private static final int ENTITY_INTERVAL_TICKS = 10;
    /**
     * Accepted range for a client-requested entity cadence; the server stays authoritative.
     */
    private static final int MIN_ENTITY_INTERVAL_TICKS = 5;
    private static final int MAX_ENTITY_INTERVAL_TICKS = 40;
    /**
     * Smallest map rectangle to load, in chunks per axis, regardless of a tiny view distance.
     */
    private static final int MIN_LOAD_SIDE = 16;
    /**
     * How many not-yet-loaded chunks are re-checked per tick, per player.
     */
    private static final int RETRY_BUDGET_PER_TICK = 64;
    /**
     * Give up on a chunk after this many ticks without it becoming loadable.
     */
    private static final int MAX_RETRY_TICKS = 200;

    private static final Map<UUID, ViewSession> SESSIONS = new HashMap<>();

    private ChunkMapViewService() {
    }

    /**
     * One player's current map view.
     */
    private static final class ViewSession {
        private final UUID player;
        private ResourceKey<Level> dimension;
        private ChunkLeapRegion region;
        private int epoch;
        private int entityIntervalTicks = ENTITY_INTERVAL_TICKS;
        /**
         * Chunks not sampled yet.
         */
        private final ArrayDeque<ChunkPos> pending = new ArrayDeque<>();
        /**
         * Chunks we tried but that are still generating; retried until they load or time out.
         */
        private final Map<ChunkPos, Long> awaiting = new HashMap<>();
        private long lastEntityTick;
        private long lastStatusTick;

        private ViewSession(UUID player) {
            this.player = player;
        }
    }

    // ---------------------------------------------------------------- requests

    public static void onViewRequest(ServerPlayer player, ChunkLeapPackets.ViewRequestPacket packet) {
        if (player == null || packet == null || packet.region() == null) return;
        var server = player.level().getServer();
        if (server == null) return;
        var region = boundByViewDistance(player, packet.region());
        var level = server.getLevel(region.dimension());
        if (level == null) return;

        var session = SESSIONS.computeIfAbsent(player.getUUID(), ViewSession::new);
        // A client may ask for a different marker cadence; clamp it so it cannot be abused to spam.
        if (packet.entityRefreshTicks() > 0) {
            session.entityIntervalTicks = Math.clamp(packet.entityRefreshTicks(),
                    MIN_ENTITY_INTERVAL_TICKS, MAX_ENTITY_INTERVAL_TICKS);
        }
        var newPage = !region.dimension().equals(session.dimension) || packet.epoch() != session.epoch;
        if (newPage) {
            if (session.dimension != null) {
                // Leaving a page: drop the raster queues; ticket removal is budgeted on tick.
                ChunkTicketLeaseManager.cancelViewLease(
                        ChunkTicketLeaseManager.viewOwner(player.getUUID(), session.dimension));
            }
            session.dimension = region.dimension();
            session.epoch = packet.epoch();
            session.pending.clear();
            session.awaiting.clear();
            queueAll(session, region);
            session.lastEntityTick = 0;
        } else {
            queueMissing(session, region);
        }
        session.region = region;
        session.lastStatusTick = 0;
        ChunkTicketLeaseManager.requestViewLease(level,
                ChunkTicketLeaseManager.viewOwner(player.getUUID(), region.dimension()), region);
    }

    /**
     * Clamps a requested view to the player's own view distance, halved.
     *
     * <p>The player's configured view distance is the amount of terrain the server is already willing to
     * keep resident for them; letting a map page exceed it would let one player pin a large region in
     * memory. Halving it keeps a map page comfortably cheaper than normal play.
     */
    static ChunkLeapRegion boundByViewDistance(ServerPlayer player, ChunkLeapRegion requested) {
        return boundByViewDistance(Math.max(2, player.requestedViewDistance()), requested);
    }

    /**
     * Pure form of {@link #boundByViewDistance(ServerPlayer, ChunkLeapRegion)}; testable without a player.
     */
    static ChunkLeapRegion boundByViewDistance(int requestedDistance, ChunkLeapRegion requested) {
        var maxSide = Math.clamp(requestedDistance, MIN_LOAD_SIDE, ChunkLeapRegion.MAX_SIDE);
        if (requested.width() <= maxSide && requested.height() <= maxSide) return requested;
        var width = Math.min(requested.width(), maxSide);
        var height = Math.min(requested.height(), maxSide);
        return ChunkLeapRegion.ofChunks(requested.dimension(),
                requested.minChunkX() + (requested.width() - width) / 2,
                requested.minChunkZ() + (requested.height() - height) / 2,
                width, height);
    }

    public static void onViewRelease(ServerPlayer player, ChunkLeapPackets.ViewReleasePacket packet) {
        if (player == null) return;
        var session = SESSIONS.remove(player.getUUID());
        if (session != null) {
            releaseSession(session);
            return;
        }
        // A page closed without a tracked session: release by dimension id alone.
        var id = Identifier.tryParse(packet.dimensionId());
        if (id != null) {
            ChunkTicketLeaseManager.cancelViewLease(ChunkTicketLeaseManager.viewOwner(player.getUUID(),
                    ResourceKey.create(Registries.DIMENSION, id)));
        }
    }

    private static void queueAll(ViewSession session, ChunkLeapRegion region) {
        for (var x = region.minChunkX(); x <= region.maxChunkX(); x++) {
            for (var z = region.minChunkZ(); z <= region.maxChunkZ(); z++) {
                session.pending.add(new ChunkPos(x, z));
            }
        }
    }

    /**
     * Keeps already-queued chunks, adds the ones that scrolled into view.
     */
    private static void queueMissing(ViewSession session, ChunkLeapRegion region) {
        var inView = new HashSet<ChunkPos>();
        for (var x = region.minChunkX(); x <= region.maxChunkX(); x++) {
            for (var z = region.minChunkZ(); z <= region.maxChunkZ(); z++) {
                inView.add(new ChunkPos(x, z));
            }
        }
        session.pending.retainAll(inView);
        session.awaiting.keySet().retainAll(inView);
        var queued = new HashSet<>(session.pending);
        for (var pos : inView) {
            if (queued.add(pos)) session.pending.add(pos);
        }
    }

    /**
     * Re-queues chunks whose content changed, so the map repaints without the player reopening it.
     *
     * <p>Called after a swap: only the chunks that actually moved are re-rasterised, and only for the
     * sessions currently looking at that dimension.
     */
    public static void invalidateChunks(ServerLevel level, ChunkLeapRegion changed) {
        if (level == null || changed == null) return;
        var dimensionId = level.dimension().identifier().toString();
        for (var session : SESSIONS.values()) {
            if (session.dimension == null || session.region == null) continue;
            if (!session.dimension.identifier().toString().equals(dimensionId)) continue;
            var overlap = intersect(session.region, changed);
            if (overlap == null) continue;
            for (var x = overlap.minChunkX(); x <= overlap.maxChunkX(); x++) {
                for (var z = overlap.minChunkZ(); z <= overlap.maxChunkZ(); z++) {
                    var pos = new ChunkPos(x, z);
                    session.awaiting.remove(pos);
                    if (!session.pending.contains(pos)) session.pending.add(pos);
                }
            }
        }
    }

    private static @Nullable ChunkLeapRegion intersect(ChunkLeapRegion a, ChunkLeapRegion b) {
        if (!a.dimension().equals(b.dimension())) return null;
        var minX = Math.max(a.minChunkX(), b.minChunkX());
        var minZ = Math.max(a.minChunkZ(), b.minChunkZ());
        var maxX = Math.min(a.maxChunkX(), b.maxChunkX());
        var maxZ = Math.min(a.maxChunkZ(), b.maxChunkZ());
        if (maxX < minX || maxZ < minZ) return null;
        return ChunkLeapRegion.ofChunks(a.dimension(), minX, minZ, maxX - minX + 1, maxZ - minZ + 1);
    }

    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        if (SESSIONS.isEmpty()) return;
        var server = event.getServer();
        var snapshot = List.copyOf(SESSIONS.entrySet());
        for (var entry : snapshot) {
            var player = server.getPlayerList().getPlayer(entry.getKey());
            if (player == null) {
                if (SESSIONS.remove(entry.getKey()) != null) releaseSession(entry.getValue());
                continue;
            }
            var session = entry.getValue();
            if (session.dimension == null || session.region == null) continue;
            var level = server.getLevel(session.dimension);
            if (level == null) continue;
            retryAwaiting(session, level, event.getServer().getTickCount());
            rasterise(player, session, level, event.getServer().getTickCount());
            maybeSendEntities(player, session, level, event.getServer().getTickCount());
        }
    }

    /**
     * Moves chunks that have finished generating back into the queue.
     *
     * <p>This is what makes a page whose chunks start unloaded actually fill in. Entries that never load
     * are abandoned after {@link #MAX_RETRY_TICKS} so a hostile or broken request cannot pin the queue
     * open forever.
     */
    private static void retryAwaiting(ViewSession session, ServerLevel level, long tick) {
        if (session.awaiting.isEmpty()) return;
        var checked = 0;
        var iterator = session.awaiting.entrySet().iterator();
        while (iterator.hasNext() && checked < RETRY_BUDGET_PER_TICK) {
            var entry = iterator.next();
            checked++;
            var pos = entry.getKey();
            if (level.hasChunk(pos.x(), pos.z())) {
                iterator.remove();
                session.pending.add(pos);
            } else if (tick - entry.getValue() > MAX_RETRY_TICKS) {
                iterator.remove();
            }
        }
    }

    private static void rasterise(ServerPlayer player, ViewSession session, ServerLevel level, long tick) {
        if (session.pending.isEmpty()) {
            // Report status only when the whole rectangle has been resolved (loaded or abandoned).
            if (session.awaiting.isEmpty() && tick - session.lastStatusTick >= 10) {
                session.lastStatusTick = tick;
                sendViewStatus(player, session, level);
            }
            return;
        }
        var deadline = Util.getMillis() + TILE_TIME_BUDGET_MS;
        var texels = new int[MapTileBuilder.texelCount()];
        var tiles = new ArrayList<ChunkLeapPackets.Tile>();
        var attempts = 0;
        while (tiles.size() < TILE_BUDGET_PER_TICK
                && tiles.size() < ChunkLeapPackets.TilesPacket.MAX_TILES
                && !session.pending.isEmpty()
                && attempts < RETRY_BUDGET_PER_TICK) {
            var pos = session.pending.poll();
            attempts++;
            if (MapTileBuilder.sampleChunk(level, pos.x(), pos.z(), texels)) {
                tiles.add(new ChunkLeapPackets.Tile(pos.x(), pos.z(), texels.clone()));
            } else {
                // Still generating: retry instead of dropping, or the tile is lost for good.
                session.awaiting.putIfAbsent(pos, tick);
            }
            if (Util.getMillis() >= deadline) break;
        }
        if (!tiles.isEmpty()) {
            MisakaNetworkServer.send(player, new ChunkLeapPackets.TilesPacket(
                    session.dimension.identifier().toString(), session.epoch, tiles));
        }
    }

    private static void maybeSendEntities(ServerPlayer player, ViewSession session, ServerLevel level, long tick) {
        if (session.region == null || tick - session.lastEntityTick < session.entityIntervalTicks) return;
        session.lastEntityTick = tick;

        var region = session.region;
        var aabb = new AABB(region.minBlockX(), level.getMinY(), region.minBlockZ(),
                region.maxBlockX() + 1.0, level.getMaxY(), region.maxBlockZ() + 1.0);
        var markers = new ArrayList<ChunkLeapPackets.Marker>();
        for (var entity : level.getEntitiesOfClass(Entity.class, aabb, candidate -> !candidate.isRemoved())) {
            if (markers.size() >= ChunkLeapPackets.EntitiesPacket.MAX_MARKERS) break;
            var category = categoryOf(entity);
            if (category < 0) continue;
            var pos = entity.blockPosition();
            // Players carry their UUID for the real skin; other living entities are previewed from their type.
            var playerId = entity instanceof Player markerPlayer ? markerPlayer.getUUID() : null;
            markers.add(new ChunkLeapPackets.Marker(entity.getId(), category,
                    BuiltInRegistries.ENTITY_TYPE.getId(entity.getType()), pos.getX(), pos.getZ(), playerId));
        }
        MisakaNetworkServer.send(player, new ChunkLeapPackets.EntitiesPacket(
                session.dimension.identifier().toString(), markers));
    }

    static byte categoryOf(Entity entity) {
        if (entity instanceof Player) return ChunkLeapPackets.CAT_PLAYER;
        if (entity instanceof ItemEntity) return ChunkLeapPackets.CAT_ITEM;
        if (entity instanceof Projectile) return ChunkLeapPackets.CAT_PROJECTILE;
        if (entity instanceof LivingEntity living) {
            var category = living.getType().getCategory();
            if (category == MobCategory.MONSTER) return ChunkLeapPackets.CAT_HOSTILE;
            if (category == MobCategory.CREATURE || category == MobCategory.AMBIENT
                    || category == MobCategory.WATER_CREATURE || category == MobCategory.AXOLOTLS) {
                return ChunkLeapPackets.CAT_PASSIVE;
            }
            return ChunkLeapPackets.CAT_NEUTRAL;
        }
        return -1;
    }

    private static void sendViewStatus(ServerPlayer player, ViewSession session, ServerLevel level) {
        var region = session.region;
        if (region == null) return;
        var total = 0;
        var loaded = 0;
        for (var x = region.minChunkX(); x <= region.maxChunkX(); x++) {
            for (var z = region.minChunkZ(); z <= region.maxChunkZ(); z++) {
                total++;
                if (level.hasChunk(x, z)) loaded++;
            }
        }
        MisakaNetworkServer.send(player, new ChunkLeapPackets.ViewStatusPacket(
                session.dimension.identifier().toString(), loaded, total));
    }

    private static void releaseSession(ViewSession session) {
        if (session.dimension != null) {
            ChunkTicketLeaseManager.cancelViewLease(
                    ChunkTicketLeaseManager.viewOwner(session.player, session.dimension));
        }
        ChunkTicketLeaseManager.release(ChunkTicketLeaseManager.preloadOwner(session.player));
    }

    @SubscribeEvent
    public static void onPlayerLoggedOut(PlayerEvent.PlayerLoggedOutEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        var session = SESSIONS.remove(player.getUUID());
        if (session != null) releaseSession(session);
        ChunkSwapService.forgetStreamedView(player);
        ChunkTicketLeaseManager.release(ChunkTicketLeaseManager.preloadOwner(player.getUUID()));
    }

    @SubscribeEvent
    public static void onServerStopping(ServerStoppingEvent event) {
        SESSIONS.clear();
        ChunkTicketLeaseManager.releaseAll();
    }
}
