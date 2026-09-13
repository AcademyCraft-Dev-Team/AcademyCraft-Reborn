package org.academy.internal.common.ability.teleport;

import net.minecraft.core.registries.Registries;
import net.minecraft.network.protocol.game.ClientboundLevelChunkWithLightPacket;
import net.minecraft.network.protocol.game.ClientboundSetChunkCacheCenterPacket;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.academy.AcademyCraft;
import org.academy.internal.common.ability.Skills;
import org.academy.internal.common.ability.teleport.skills.lv5.ChunkLeap;
import org.misaka.MisakaNetworkServer;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Validates and commits 区块跃迁 operations: chunk-region swaps, entity teleports, player point-and-go,
 * chunk inspection, and the god view.
 *
 * <p>The server never trusts client geometry: selections are clamped and validated, destinations are resolved
 * against real blocks, and every operation is charged through the skill so CP handling stays consistent with
 * the rest of the mod. Long operations are spread across ticks rather than run to completion in one.
 */
@net.neoforged.fml.common.EventBusSubscriber(modid = org.academy.AcademyCraft.MOD_ID)
public final class ChunkSwapService {
    /**
     * Hard ceiling on chunks per swap, independent of the client-side preference.
     *
     * <p>Strictly below {@link ChunkLeapSelection#MAX_CHUNKS}, which is only the wire/container limit.
     * Keeping the gameplay cap lower is what makes it enforceable: a selection is truncated to the container
     * limit on construction, so a cap equal to it could never reject anything.
     */
    public static final int MAX_SWAP_CHUNKS = 9;
    /** Wall-clock budget per server tick for applying swap pairs. */
    private static final long SWAP_TIME_BUDGET_MS = 4L;
    /** Chunks refreshed per server tick during the deferred heightmap pass. */
    private static final int REFRESH_BUDGET_PER_TICK = 6;
    /** Wall-clock budget per tick for the heightmap queue, in milliseconds. */
    private static final long REFRESH_TIME_BUDGET_MS = 3L;
    /** Ticks between bounded carrier repair passes after the light-completion barrier. */
    private static final int CARRIER_RESYNC_STEP_TICKS = 5;
    /** How many staggered resync passes a carried player receives. */
    private static final int CARRIER_RESYNC_PASSES = 2;
    /** Only the collision-relevant 3x3 neighbourhood is repaired; never the player's whole view distance. */
    private static final int CARRIER_RESYNC_RADIUS = 1;

    private ChunkSwapService() {
    }

    // ---------------------------------------------------------------- swap

    public static void onSwapRequest(ServerPlayer player, ChunkLeapPackets.SwapRequestPacket packet) {
        if (player == null || packet == null) return;
        var skill = Skills.CHUNK_LEAP.get();
        var server = player.level().getServer();
        if (server == null) return;

        var source = packet.source();
        var target = packet.target();
        var failReason = validate(source, target, server);
        if (failReason != null) {
            MisakaNetworkServer.send(player, new ChunkLeapPackets.SwapResultPacket(
                    packet.opId(), false, failReason, List.of()));
            return;
        }

        var levelA = server.getLevel(source.dimension());
        var levelB = server.getLevel(target.dimension());
        if (levelA == null || levelB == null) {
            MisakaNetworkServer.send(player, new ChunkLeapPackets.SwapResultPacket(
                    packet.opId(), false, "chunk_leap.reason.dimension_missing", List.of()));
            return;
        }

        // Distinct owners per side: acquireAndLoad releases the owner before re-acquiring, so sharing one id
        // made the second call release the first side's freshly acquired chunks. The source then unloaded
        // before the swap ran, getChunkNow returned null, and the pair was skipped silently.
        var ownerA = ChunkTicketLeaseManager.preloadOwner(player.getUUID()) + ":a";
        var ownerB = ChunkTicketLeaseManager.preloadOwner(player.getUUID()) + ":b";
        var boundsA = source.bounds();
        var boundsB = target.bounds();
        var loadA = ChunkTicketLeaseManager.acquireAndLoad(levelA, ownerA, boundsA);
        var loadB = ChunkTicketLeaseManager.acquireAndLoad(levelB, ownerB, boundsB);
        var total = source.count();
        // Report progress while the regions load, then once more when they are ready, so the map can show
        // "preloading" instead of appearing stuck before the swap begins.
        sendPreload(player, packet.opId(), boundsA, 0, total, false);
        runAfterPreload(player, CompletableFuture.allOf(loadA, loadB), () -> {
            sendPreload(player, packet.opId(), boundsA, total, total, true);
            commit(player, skill, packet, source, target, levelA, levelB);
        }, () -> {
            releasePreloadLeases(player);
            if (!player.isRemoved() && !player.hasDisconnected()) {
                MisakaNetworkServer.send(player, new ChunkLeapPackets.SwapResultPacket(
                        packet.opId(), false, "chunk_leap.reason.load_failed", List.of()));
            }
        });
    }

    private static void commit(ServerPlayer player, ChunkLeap skill, ChunkLeapPackets.SwapRequestPacket packet,
                               ChunkLeapSelection source, ChunkLeapSelection target,
                               ServerLevel levelA, ServerLevel levelB) {
        if (player.isRemoved() || player.hasDisconnected()) {
            releasePreloadLeases(player);
            return;
        }
        var entityCount = countEntities(levelA, source) + countEntities(levelB, target);
        var movePlayers = packet.movePlayers();
        // The charge happens up front, but the pairs are applied by the tick scheduler with a time budget: a
        // large swap must never run to completion inside one server tick.
        var charged = skill.executeSwap(player, source.count(), entityCount, () -> {
        });
        if (charged) {
            ACTIVE_SWAPS.add(new ActiveSwap(player, packet.opId(), levelA, levelB,
                    source, target, movePlayers));
        } else {
            MisakaNetworkServer.send(player, new ChunkLeapPackets.SwapResultPacket(
                    packet.opId(), false, "chunk_leap.reason.cost", List.of()));
            releasePreloadLeases(player);
        }
    }

    /** One in-flight swap: pairs are consumed by offset index under a per-tick time budget. */
    private static final class ActiveSwap {
        private final ServerPlayer player;
        private final UUID opId;
        private final ServerLevel levelA;
        private final ServerLevel levelB;
        private final ChunkLeapSelection source;
        private final ChunkLeapSelection target;
        private final boolean movePlayers;
        /** Index into the offset list; pairing is by offset, so one cursor is enough. */
        private int cursor;
        /** Players carried by this swap, to be resynced once their new terrain has settled. */
        private final List<ServerPlayer> carriedPlayers = new ArrayList<>();
        /** Vanilla relight operations started for every exchanged chunk pair. */
        private final List<CompletableFuture<Void>> lightingTasks = new ArrayList<>();
        /** One completion barrier, created after every pair has been exchanged. */
        private CompletableFuture<Void> lightingCompletion;

        private ActiveSwap(ServerPlayer player, UUID opId, ServerLevel levelA, ServerLevel levelB,
                           ChunkLeapSelection source, ChunkLeapSelection target, boolean movePlayers) {
            this.player = player;
            this.opId = opId;
            this.levelA = levelA;
            this.levelB = levelB;
            this.source = source;
            this.target = target;
            this.movePlayers = movePlayers;
        }
    }

    private static final List<ActiveSwap> ACTIVE_SWAPS = new ArrayList<>();

    /**
     * Applies in-flight swaps under a time budget and reports completion.
     *
     * <p>Each pair is atomic, so a mid-swap stop leaves only whole pairs exchanged; the result packet and the
     * preload lease release happen once every pair has been applied.
     */
    @net.neoforged.bus.api.SubscribeEvent
    public static void onServerTick(net.neoforged.neoforge.event.tick.ServerTickEvent.Post event) {
        var tick = event.getServer().getTickCount();
        drainRefreshes();
        drainCarrierResyncs(tick);
        if (ACTIVE_SWAPS.isEmpty()) return;
        var deadline = net.minecraft.util.Util.getMillis() + SWAP_TIME_BUDGET_MS;
        var iterator = ACTIVE_SWAPS.iterator();
        while (iterator.hasNext()) {
            var swap = iterator.next();
            performSwap(swap, deadline);
            if (swap.cursor < swap.source.count()) break;
            if (swap.lightingCompletion == null) {
                swap.lightingCompletion = CompletableFuture.allOf(
                        swap.lightingTasks.toArray(CompletableFuture<?>[]::new));
            }
            if (!swap.lightingCompletion.isDone()) break;
            try {
                swap.lightingCompletion.join();
            } catch (CompletionException exception) {
                iterator.remove();
                AcademyCraft.LOGGER.error("Chunk leap lighting rebuild failed for operation {}", swap.opId,
                        exception.getCause());
                if (!swap.player.isRemoved() && !swap.player.hasDisconnected()) {
                    MisakaNetworkServer.send(swap.player, new ChunkLeapPackets.SwapResultPacket(
                            swap.opId, false, "chunk_leap.reason.relight_failed", List.of()));
                }
                releasePreloadLeases(swap.player);
                continue;
            }
            iterator.remove();
            var player = swap.player;
            var crossDimension = swap.levelA != swap.levelB;
            // Repair a carrier's destination first. The old full-view pass emitted up to 625 whole chunks
            // before reaching the centre, which saturated an integrated or dedicated server connection and
            // left the client colliding with stale terrain until its centre packet finally arrived.
            for (var carried : swap.carriedPlayers) {
                if (crossDimension) {
                    // A respawn creates a new client chunk cache; an earlier god-view cache lease belongs
                    // to the old level and must never restore over the new one.
                    STREAMED.remove(carried.getUUID());
                    invalidateGodView(carried);
                }
                resyncSurroundings(carried, crossDimension);
                scheduleCarrierResync(carried, tick, crossDimension);
            }
            resendSwappedChunks(swap);
            // Heightmap priming is far too expensive to do inline (it scans every column of the chunk), so it
            // is queued and drained a few chunks per tick. Lighting, which cannot be deferred, is already done.
            enqueueRefresh(swap);
            // Repaint the map for anyone watching these regions; without this the terrain the player just
            // rearranged would not appear until they reopened the screen.
            ChunkMapViewService.invalidateChunks(swap.levelA, swap.source.bounds());
            ChunkMapViewService.invalidateChunks(swap.levelB, swap.target.bounds());
            if (!player.isRemoved() && !player.hasDisconnected()) {
                MisakaNetworkServer.send(player, new ChunkLeapPackets.SwapResultPacket(
                        swap.opId, true, "", List.of(swap.source.bounds(), swap.target.bounds())));
            }
            releasePreloadLeases(player);
        }
    }

    /**
     * Applies the next slice of a swap.
     *
     * <p>Pairing is by offset index: chunk {@code i} of the source exchanges with chunk {@code i} of the
     * target. That is what makes an arbitrary (even disconnected) selection meaningful — every selected chunk
     * has exactly one counterpart, with no shape matching required.
     */
    private static void performSwap(ActiveSwap swap, long deadline) {
        var crossDimension = swap.levelA != swap.levelB;
        while (swap.cursor < swap.source.count()) {
            var posA = swap.source.chunkAt(swap.cursor);
            var posB = swap.target.chunkAt(swap.cursor);
            var chunkA = swap.levelA.getChunkSource().getChunkNow(posA.x(), posA.z());
            var chunkB = swap.levelB.getChunkSource().getChunkNow(posB.x(), posB.z());
            if (chunkA != null && chunkB != null) {
                var result = crossDimension
                        ? ChunkRegionSwap.applyAcrossLevels(swap.levelA, chunkA, swap.levelB, chunkB,
                        swap.source.bounds(), swap.target.bounds(), swap.movePlayers)
                        : ChunkRegionSwap.apply(swap.levelA, chunkA, chunkB,
                        swap.source.bounds(), swap.target.bounds(), swap.movePlayers);
                swap.carriedPlayers.addAll(result.playersMoved());
                swap.lightingTasks.add(result.lightingTask());
            }
            swap.cursor++;
            if (net.minecraft.util.Util.getMillis() >= deadline) return;
        }
    }

    /** Sends exchanged chunks only after their vanilla light tasks have completed. */
    private static void resendSwappedChunks(ActiveSwap swap) {
        for (var i = 0; i < swap.source.count(); i++) {
            resendIfLoaded(swap.levelA, swap.source.chunkAt(i));
            resendIfLoaded(swap.levelB, swap.target.chunkAt(i));
        }
    }

    private static void resendIfLoaded(ServerLevel level, ChunkPos pos) {
        var chunk = level.getChunkSource().getChunkNow(pos.x(), pos.z());
        if (chunk != null) {
            ChunkRegionSwap.resend(level, chunk);
        }
    }

    // ---------------------------------------------------------------- deferred heightmaps

    /** A chunk whose heightmaps still need priming after its contents moved. */
    private record PendingRefresh(ServerLevel level, ChunkPos pos) {
    }

    private static final ArrayDeque<PendingRefresh> REFRESH_QUEUE = new ArrayDeque<>();

    private static void enqueueRefresh(ActiveSwap swap) {
        var crossDimension = swap.levelA != swap.levelB;
        for (var i = 0; i < swap.source.count(); i++) {
            REFRESH_QUEUE.add(new PendingRefresh(swap.levelA, swap.source.chunkAt(i)));
            if (crossDimension) {
                REFRESH_QUEUE.add(new PendingRefresh(swap.levelB, swap.target.chunkAt(i)));
            }
        }
    }

    private static void drainRefreshes() {
        if (REFRESH_QUEUE.isEmpty()) return;
        var deadline = net.minecraft.util.Util.getMillis() + REFRESH_TIME_BUDGET_MS;
        var done = 0;
        while (done < REFRESH_BUDGET_PER_TICK && !REFRESH_QUEUE.isEmpty()) {
            var entry = REFRESH_QUEUE.poll();
            ChunkRegionSwap.refreshHeightmapsAndResend(entry.level(), entry.pos());
            done++;
            if (net.minecraft.util.Util.getMillis() >= deadline) break;
        }
    }

    // ---------------------------------------------------------------- carrier resync

    /**
     * A player carried by a swap, waiting for the terrain around their new position to be re-sent.
     *
     * <p>The light-completion barrier has already settled the server chunk. These short delayed passes cover
     * only the client respawn/tracking handshake; recording the expected dimension prevents a later unrelated
     * teleport from receiving stale repair packets.
     */
    private record CarrierResync(ServerPlayer player, ResourceKey<net.minecraft.world.level.Level> dimension,
                                 boolean resetCacheCenter, long dueTick) {
    }

    private static final ArrayDeque<CarrierResync> CARRIER_RESYNCS = new ArrayDeque<>();

    private static void scheduleCarrierResync(ServerPlayer player, long tick, boolean resetCacheCenter) {
        if (player == null) return;
        var dimension = player.level().dimension();
        for (var pass = 1; pass <= CARRIER_RESYNC_PASSES; pass++) {
            CARRIER_RESYNCS.add(new CarrierResync(player, dimension, resetCacheCenter,
                    tick + (long) CARRIER_RESYNC_STEP_TICKS * pass));
        }
    }

    private static void drainCarrierResyncs(long tick) {
        if (CARRIER_RESYNCS.isEmpty()) return;
        var iterator = CARRIER_RESYNCS.iterator();
        while (iterator.hasNext()) {
            var entry = iterator.next();
            if (entry.dueTick() > tick) continue;
            iterator.remove();
            if (!entry.player().level().dimension().equals(entry.dimension())) continue;
            resyncSurroundings(entry.player(), entry.resetCacheCenter());
        }
    }

    /**
     * Re-sends the collision-relevant chunks around a player, centre first.
     *
     * <p>Whole-chunk packets carry the current light and make the client re-mark every section for redraw, so
     * this repairs stale blocks and collision in one pass. Limiting the pass to 3x3 avoids the previous
     * view-distance-dependent burst of up to 625 packets; the normal chunk tracker remains responsible for
     * the rest of the view.
     */
    private static void resyncSurroundings(ServerPlayer player, boolean resetCacheCenter) {
        if (player.isRemoved() || player.hasDisconnected()) return;
        var level = player.level();
        var center = player.chunkPosition();
        if (resetCacheCenter) {
            // Required after a dimension respawn (and after any old god-view stream): otherwise the client
            // can reject the centre chunk as outside its current cache window.
            player.connection.send(new ClientboundSetChunkCacheCenterPacket(center.x(), center.z()));
        }
        for (var pos : carrierResyncOrder(center, CARRIER_RESYNC_RADIUS)) {
            var chunk = level.getChunkSource().getChunkNow(pos.x(), pos.z());
            if (chunk != null) {
                sendWholeChunk(player, level, chunk);
            }
        }
    }

    /** Pure, bounded centre-first packet order for carrier recovery. */
    static List<ChunkPos> carrierResyncOrder(ChunkPos center, int requestedRadius) {
        var radius = Math.clamp(requestedRadius, 0, CARRIER_RESYNC_RADIUS);
        var side = radius * 2 + 1;
        var result = new ArrayList<ChunkPos>(side * side);
        result.add(center);
        for (var ring = 1; ring <= radius; ring++) {
            for (var dx = -ring; dx <= ring; dx++) {
                for (var dz = -ring; dz <= ring; dz++) {
                    if (Math.max(Math.abs(dx), Math.abs(dz)) != ring) continue;
                    result.add(new ChunkPos(center.x() + dx, center.z() + dz));
                }
            }
        }
        return List.copyOf(result);
    }

    // ---------------------------------------------------------------- entity teleport

    public static void onEntityTeleport(ServerPlayer player, ChunkLeapPackets.EntityTeleportPacket packet) {
        if (player == null || packet == null) return;
        var skill = Skills.CHUNK_LEAP.get();
        var server = player.level().getServer();
        if (server == null) return;

        var id = Identifier.tryParse(packet.dimensionId());
        var level = id == null ? null : server.getLevel(ResourceKey.create(Registries.DIMENSION, id));
        if (level == null) {
            MisakaNetworkServer.send(player, new ChunkLeapPackets.TeleportResultPacket(
                    false, "chunk_leap.reason.dimension_missing"));
            return;
        }
        var target = new net.minecraft.core.BlockPos(packet.targetX(), packet.targetY(), packet.targetZ());
        var region = ChunkLeapRegion.ofChunks(level.dimension(), target.getX() >> 4, target.getZ() >> 4, 1, 1);
        runAfterPreload(player, ChunkTicketLeaseManager.acquireAndLoad(level,
                ChunkTicketLeaseManager.preloadOwner(player.getUUID()), region),
                () -> commitEntityTeleport(player, skill, packet, level, target),
                () -> reportPreloadFailure(player));
    }

    private static void commitEntityTeleport(ServerPlayer player, ChunkLeap skill,
                                             ChunkLeapPackets.EntityTeleportPacket packet,
                                             ServerLevel level, net.minecraft.core.BlockPos target) {
        if (player.isRemoved() || player.hasDisconnected()) {
            releasePreloadLeases(player);
            return;
        }
        var entity = level.getEntity(packet.entityId());
        if (entity == null || entity.isRemoved()) {
            MisakaNetworkServer.send(player, new ChunkLeapPackets.TeleportResultPacket(
                    false, "chunk_leap.reason.entity_missing"));
            releasePreloadLeases(player);
            return;
        }
        // The client sends only the clicked column, so resolve the real surface here. Using the raw packet Y
        // (always 0) dropped the entity at the world floor and let the safety search walk it up into whatever
        // block it found first — which looked like arriving at a chunk corner.
        var destination = landingPosition(level, target);
        var safe = TeleportSafety.findSafe(entity, level, destination);
        var finalDestination = safe != null ? safe : destination;

        var charged = skill.executeEntityTeleport(player, () -> {
            // A ridden entity must move as one unit, so relocate its root vehicle instead.
            var root = entity.getVehicle() != null ? entity.getRootVehicle() : entity;
            var moved = root.position().add(finalDestination.subtract(entity.position()));
            var ok = TeleportSync.teleportInstantly(root, level, moved);
            entity.resetFallDistance();
            MisakaNetworkServer.send(player, new ChunkLeapPackets.TeleportResultPacket(
                    ok, ok ? "" : "chunk_leap.reason.blocked"));
        });
        if (!charged) {
            MisakaNetworkServer.send(player, new ChunkLeapPackets.TeleportResultPacket(
                    false, "chunk_leap.reason.cost"));
        }
        releasePreloadLeases(player);
    }

    // ---------------------------------------------------------------- player teleport

    /**
     * Sends the requesting player to a position they picked on the map.
     *
     * <p>FTB-style point-and-go: the coordinates come from the map, but the landing place does not. The column
     * is resolved against the real blocks and a collision-free spot is chosen, so a click on a cliff face or
     * inside a wall cannot put the player inside geometry. The destination is loaded first, because the safety
     * search needs the surrounding blocks to be present.
     */
    public static void onPlayerTeleport(ServerPlayer player, ChunkLeapPackets.PlayerTeleportPacket packet) {
        if (player == null || packet == null) return;
        var skill = Skills.CHUNK_LEAP.get();
        var server = player.level().getServer();
        if (server == null) return;
        var id = Identifier.tryParse(packet.dimensionId());
        var level = id == null ? null : server.getLevel(ResourceKey.create(Registries.DIMENSION, id));
        if (level == null) {
            MisakaNetworkServer.send(player, new ChunkLeapPackets.TeleportResultPacket(
                    false, "chunk_leap.reason.dimension_missing"));
            return;
        }
        var chunkX = packet.blockX() >> 4;
        var chunkZ = packet.blockZ() >> 4;
        var owner = ChunkTicketLeaseManager.preloadOwner(player.getUUID()) + ":tp:" + UUID.randomUUID();
        // A 3x3 neighbourhood, so the safety search has the blocks around the landing spot to work with.
        var region = ChunkLeapRegion.ofChunks(level.dimension(), chunkX - 1, chunkZ - 1, 3, 3);
        runAfterPreload(player, ChunkTicketLeaseManager.acquireAndLoad(level, owner, region), () -> {
            try {
                if (player.isRemoved() || player.hasDisconnected()) return;
                commitPlayerTeleport(player, skill, level, packet.blockX(), packet.blockZ());
            } finally {
                ChunkTicketLeaseManager.release(owner);
            }
        }, () -> reportPreloadFailure(player));
    }

    private static void commitPlayerTeleport(ServerPlayer player, ChunkLeap skill, ServerLevel level,
                                             int blockX, int blockZ) {
        var surface = MapTileBuilder.visibleSurfaceY(level, blockX, blockZ);
        var y = surface >= level.getMinY() ? surface + 1 : level.getMinY();
        var desired = new Vec3(blockX + 0.5, y, blockZ + 0.5);
        var safe = TeleportSafety.findSafe(player, level, desired);
        if (safe == null) {
            MisakaNetworkServer.send(player, new ChunkLeapPackets.TeleportResultPacket(
                    false, "chunk_leap.reason.blocked"));
            return;
        }
        var charged = skill.executeEntityTeleport(player, () -> {
            var ok = TeleportSync.teleportInstantly(player, level, safe,
                    player.getYRot(), player.getXRot());
            if (ok) player.resetFallDistance();
            MisakaNetworkServer.send(player, new ChunkLeapPackets.TeleportResultPacket(
                    ok, ok ? "" : "chunk_leap.reason.blocked"));
        });
        if (!charged) {
            MisakaNetworkServer.send(player, new ChunkLeapPackets.TeleportResultPacket(
                    false, "chunk_leap.reason.cost"));
        }
    }

    // ---------------------------------------------------------------- inspect

    /**
     * Answers a chunk inspection: surface block, biome, entity count and a coarse palette.
     *
     * <p>Reading the chunk may require loading it first, so the load is awaited asynchronously — bounded to a
     * single chunk on purpose, so an inspection cannot pull in an arbitrary amount of terrain.
     */
    public static void onInspectRequest(ServerPlayer player, ChunkLeapPackets.InspectRequestPacket packet) {
        if (player == null || packet == null) return;
        var server = player.level().getServer();
        if (server == null) return;
        var id = Identifier.tryParse(packet.dimensionId());
        var level = id == null ? null : server.getLevel(ResourceKey.create(Registries.DIMENSION, id));
        if (level == null) return;

        var chunkX = packet.chunkX();
        var chunkZ = packet.chunkZ();
        // Already resident: answer immediately without touching tickets.
        if (level.getChunkSource().getChunkNow(chunkX, chunkZ) != null) {
            MisakaNetworkServer.send(player, ChunkLeapPackets.InspectResultPacket.from(
                    ChunkLeapInspector.inspect(level, chunkX, chunkZ)));
            return;
        }
        var owner = ChunkTicketLeaseManager.preloadOwner(player.getUUID());
        var region = ChunkLeapRegion.ofChunks(level.dimension(), chunkX, chunkZ, 1, 1);
        runAfterPreload(player, ChunkTicketLeaseManager.acquireAndLoad(level, owner, region), () -> {
            try {
                if (player.isRemoved() || player.hasDisconnected()) return;
                MisakaNetworkServer.send(player, ChunkLeapPackets.InspectResultPacket.from(
                        ChunkLeapInspector.inspect(level, chunkX, chunkZ)));
            } finally {
                ChunkTicketLeaseManager.release(owner);
            }
        }, () -> reportPreloadFailure(player));
    }

    /** The exact standing position for a clicked block column. */
    private static Vec3 landingPosition(ServerLevel level, net.minecraft.core.BlockPos target) {
        var surface = MapTileBuilder.visibleSurfaceY(level, target.getX(), target.getZ());
        var y = surface >= level.getMinY() ? surface + 1 : target.getY();
        return new Vec3(target.getX() + 0.5, y, target.getZ() + 0.5);
    }

    // ---------------------------------------------------------------- god view

    /**
     * What a god view has streamed into one client, so leaving can repair precisely the damage it did.
     *
     * <p>The client cache is a fixed grid of slots addressed by chunk coordinates modulo its diameter, so a
     * streamed chunk far away can land in the same slot as one of the player's own chunks. That is not
     * cosmetic: {@code ClientChunkCache.isValidChunk} requires the stored chunk's position to equal the
     * requested one, so a displaced slot serves <em>no</em> chunk and the terrain there vanishes — and because
     * the server still considers that chunk tracked, it is never re-sent. Recording the slots actually written
     * is what makes the repair exact instead of a blanket resend.
     */
    private static final class StreamedView {
        private final ResourceKey<net.minecraft.world.level.Level> dimension;
        private int minChunkX;
        private int minChunkZ;
        private int side;
        /** Client cache radius used while streaming, so slot indices can be recomputed exactly. */
        private final int cacheRadius;
        /**
         * Slots this session has ever written into, accumulated across every pan.
         *
         * <p>Accumulated rather than derived from the latest rectangle because moving the camera streams
         * repeatedly; an earlier rectangle could hold a slot that now matters, and only the union describes
         * everything that needs checking on exit.
         */
        private final Set<Integer> writtenSlots = new HashSet<>();

        private StreamedView(ResourceKey<net.minecraft.world.level.Level> dimension, int cacheRadius) {
            this.dimension = dimension;
            this.cacheRadius = cacheRadius;
        }

        private void record(int chunkX, int chunkZ, int radius) {
            this.minChunkX = chunkX - radius;
            this.minChunkZ = chunkZ - radius;
            this.side = radius * 2 + 1;
            var v = cacheRadius * 2 + 1;
            for (var dx = 0; dx < side; dx++) {
                for (var dz = 0; dz < side; dz++) {
                    writtenSlots.add(slotIndex(minChunkX + dx, minChunkZ + dz, v));
                }
            }
        }

        private boolean covers(int chunkX, int chunkZ) {
            return side > 0 && chunkX >= minChunkX && chunkZ >= minChunkZ
                    && chunkX < minChunkX + side && chunkZ < minChunkZ + side;
        }
    }

    private static final Map<UUID, StreamedView> STREAMED = new ConcurrentHashMap<>();
    private static final Map<UUID, UUID> GOD_VIEW_GENERATIONS = new ConcurrentHashMap<>();

    /** Slot a chunk occupies in a client cache of {@code v} chunks per axis. */
    private static int slotIndex(int chunkX, int chunkZ, int v) {
        return Math.floorMod(chunkZ, v) * v + Math.floorMod(chunkX, v);
    }

    /**
     * Client cache radius, mirroring {@code ClientChunkCache.calculateStorageRange}.
     *
     * <p>The client derives this from the view distance in its login packet, which is the server view
     * distance, so the value is knowable here without asking or sending anything — deliberately not pinned
     * with a radius packet, which would shrink the player's cache and silently drop their chunks.
     */
    private static int clientSlotCount(ServerPlayer player) {
        var server = player.level().getServer();
        var serverViewDistance = server == null ? 10 : server.getPlayerList().getViewDistance();
        var clientViewRange = Math.clamp(player.requestedViewDistance(), 2, Math.max(2, serverViewDistance));
        return Math.max(2, clientViewRange) + 3;
    }

    /** How many chunks around the target the god view covers: the view distance of the player, capped. */
    private static int godViewChunkRadius(ServerPlayer player) {
        return Math.clamp(player.requestedViewDistance(), 2, 8);
    }

    /**
     * Streams real terrain around a location so the client can render it from a detached camera.
     *
     * <p>Two things are needed and neither is a picture. First the client chunk-cache window has to be centred
     * on the target, because {@code ClientChunkCache} discards any chunk packet outside it; the window is moved
     * with {@link ClientboundSetChunkCacheCenterPacket}, which only reassigns the centre and evicts nothing —
     * chunk slots are addressed by absolute modular coordinates, so the chunks belonging to the player survive
     * the move and become readable again when the centre returns. Second the chunks themselves must be sent,
     * which the server can do directly for any loaded chunk.
     *
     * <p>Only the shared dimension can be shown: a client holds one level, so terrain belonging to another
     * dimension has nowhere to live.
     */
    public static void onGodViewRequest(ServerPlayer player, ChunkLeapPackets.GodViewRequestPacket packet) {
        if (player == null || packet == null) return;
        if (!packet.enter()) {
            endGodView(player, packet.dimensionId());
            return;
        }
        var server = player.level().getServer();
        if (server == null) return;
        var id = Identifier.tryParse(packet.dimensionId());
        var level = id == null ? null : server.getLevel(ResourceKey.create(Registries.DIMENSION, id));
        if (level == null) return;

        var sameDimension = level == player.level();
        var chunkX = packet.blockX() >> 4;
        var chunkZ = packet.blockZ() >> 4;
        // The camera should sit on the surface, so resolve it here rather than trusting a client guess.
        var surfaceY = MapTileBuilder.visibleSurfaceY(level, packet.blockX(), packet.blockZ());

        if (!sameDimension) {
            MisakaNetworkServer.send(player, new ChunkLeapPackets.GodViewStatusPacket(
                    packet.dimensionId(), true, false, packet.blockX(), surfaceY, packet.blockZ()));
            return;
        }

        var generation = UUID.randomUUID();
        GOD_VIEW_GENERATIONS.put(player.getUUID(), generation);
        var existing = STREAMED.get(player.getUUID());
        var radius = godViewChunkRadius(player);
        // Moving the camera within the area already streamed needs no new packets: re-sending hundreds of
        // chunks per keypress would be wasteful, and the client already holds them.
        if (existing != null && existing.dimension.equals(level.dimension()) && existing.covers(chunkX, chunkZ)) {
            MisakaNetworkServer.send(player, new ChunkLeapPackets.GodViewStatusPacket(
                    packet.dimensionId(), true, true, packet.blockX(), surfaceY, packet.blockZ()));
            return;
        }

        // Load what is not resident, then stream it. Bounded, and paid for by an explicit player action.
        var owner = godViewOwner(player);
        var region = ChunkLeapRegion.ofChunks(level.dimension(),
                chunkX - radius, chunkZ - radius, radius * 2 + 1, radius * 2 + 1);
        runAfterPreload(player, ChunkTicketLeaseManager.acquireAndLoad(level, owner, region), () -> {
            var playerId = player.getUUID();
            var stillCurrent = generation.equals(GOD_VIEW_GENERATIONS.get(playerId));
            if (!stillCurrent || player.isRemoved() || player.hasDisconnected() || player.level() != level) {
                // A newer request owns the shared lease and must not be released by this stale callback.
                // If this request is still current, invalidate it and release its old-dimension tickets.
                if (GOD_VIEW_GENERATIONS.remove(playerId, generation)) {
                    ChunkTicketLeaseManager.release(owner);
                }
                return;
            }
            streamGodView(player, level, chunkX, chunkZ, radius);
            MisakaNetworkServer.send(player, new ChunkLeapPackets.GodViewStatusPacket(
                    packet.dimensionId(), true, true, packet.blockX(), surfaceY, packet.blockZ()));
        }, () -> {
            if (GOD_VIEW_GENERATIONS.remove(player.getUUID(), generation)) reportPreloadFailure(player);
        });
    }

    /** Sends the target area as real chunk packets, after moving the client cache window onto it. */
    private static void streamGodView(ServerPlayer player, ServerLevel level,
                                      int chunkX, int chunkZ, int radius) {
        var streamed = STREAMED.computeIfAbsent(player.getUUID(),
                ignored -> new StreamedView(level.dimension(), clientSlotCount(player)));
        streamed.record(chunkX, chunkZ, radius);
        player.connection.send(new ClientboundSetChunkCacheCenterPacket(chunkX, chunkZ));
        for (var dx = -radius; dx <= radius; dx++) {
            for (var dz = -radius; dz <= radius; dz++) {
                var chunk = level.getChunkSource().getChunkNow(chunkX + dx, chunkZ + dz);
                if (chunk != null) sendWholeChunk(player, level, chunk);
            }
        }
    }

    /** Ends a god view: put the cache window back and repair any chunk the stream displaced. */
    private static void endGodView(ServerPlayer player, String dimensionId) {
        var streamed = STREAMED.remove(player.getUUID());
        invalidateGodView(player);
        var centerX = player.chunkPosition().x();
        var centerZ = player.chunkPosition().z();
        // Hand the cache window back first, so the chunks restored below are in range again.
        player.connection.send(new ClientboundSetChunkCacheCenterPacket(centerX, centerZ));
        if (streamed != null) {
            restoreDisplacedChunks(player, streamed);
        }
        MisakaNetworkServer.send(player, new ChunkLeapPackets.GodViewStatusPacket(
                dimensionId, false, true, 0, 0, 0));
    }

    /**
     * Re-sends the player's own chunks whose cache slot the god view overwrote.
     *
     * <p>Only slots that were actually written are considered, so a pan-heavy session does not turn into a
     * full-view resend. Nothing is forgotten: forgetting would delete legitimate chunks, and a far chunk left
     * in a slot simply fails the position check and is never drawn.
     */
    private static void restoreDisplacedChunks(ServerPlayer player, StreamedView streamed) {
        var level = player.level();
        if (!level.dimension().equals(streamed.dimension)) return;
        var v = streamed.cacheRadius * 2 + 1;
        var center = player.chunkPosition();
        var radius = streamed.cacheRadius;
        for (var dx = -radius; dx <= radius; dx++) {
            for (var dz = -radius; dz <= radius; dz++) {
                var x = center.x() + dx;
                var z = center.z() + dz;
                if (!streamed.writtenSlots.contains(slotIndex(x, z, v))) continue;
                var chunk = level.getChunkSource().getChunkNow(x, z);
                if (chunk != null) sendWholeChunk(player, level, chunk);
            }
        }
    }

    /** One whole chunk, blocks plus light, to one player. */
    private static void sendWholeChunk(ServerPlayer player, ServerLevel level, LevelChunk chunk) {
        player.connection.send(new ClientboundLevelChunkWithLightPacket(
                chunk, level.getLightEngine(), null, null));
    }

    /** Drops the streamed-view record for a player, e.g. on logout. */
    static void forgetStreamedView(ServerPlayer player) {
        if (player == null) return;
        STREAMED.remove(player.getUUID());
        invalidateGodView(player);
    }

    private static String godViewOwner(ServerPlayer player) {
        return ChunkTicketLeaseManager.preloadOwner(player.getUUID()) + ":god";
    }

    /** Cancels callbacks and releases the non-persistent chunks held for an active god view. */
    private static void invalidateGodView(ServerPlayer player) {
        if (player == null) return;
        GOD_VIEW_GENERATIONS.remove(player.getUUID());
        ChunkTicketLeaseManager.release(godViewOwner(player));
    }

    // ---------------------------------------------------------------- shared

    /** Loading failures must never reach CP charging or world mutation. */
    private static void runAfterPreload(ServerPlayer player, CompletableFuture<?> loading,
                                        Runnable onLoaded, Runnable onFailure) {
        loading.whenCompleteAsync((ignored, failure) -> {
            if (failure == null) {
                onLoaded.run();
            } else {
                AcademyCraft.LOGGER.error("Chunk leap preload failed for player {}", player.getUUID(), failure);
                onFailure.run();
            }
        }, player.level().getServer()).exceptionally(failure -> {
            AcademyCraft.LOGGER.error("Chunk leap operation failed after preload for player {}",
                    player.getUUID(), failure);
            return null;
        });
    }

    private static void reportPreloadFailure(ServerPlayer player) {
        if (!player.isRemoved() && !player.hasDisconnected()) {
            MisakaNetworkServer.send(player, new ChunkLeapPackets.TeleportResultPacket(
                    false, "chunk_leap.reason.load_failed"));
        }
    }

    private static void sendPreload(ServerPlayer player, UUID opId, ChunkLeapRegion region,
                                    int loaded, int total, boolean ready) {
        MisakaNetworkServer.send(player, new ChunkLeapPackets.PreloadStatusPacket(
                opId, region.dimension().identifier().toString(), ready, loaded, total));
    }

    /**
     * Releases both sides' preload leases.
     *
     * <p>Centralised because the two sides use distinct owners; releasing only the shared id would leak the
     * per-side tickets and keep chunks resident indefinitely.
     */
    private static void releasePreloadLeases(ServerPlayer player) {
        var base = ChunkTicketLeaseManager.preloadOwner(player.getUUID());
        ChunkTicketLeaseManager.release(base + ":a");
        ChunkTicketLeaseManager.release(base + ":b");
        ChunkTicketLeaseManager.release(base);
    }

    /** Counts non-player entities across every chunk of a selection. */
    private static int countEntities(ServerLevel level, ChunkLeapSelection selection) {
        var count = 0;
        for (var chunk : selection.chunks()) {
            var aabb = new AABB(chunk.getMinBlockX(), level.getMinY(), chunk.getMinBlockZ(),
                    chunk.getMaxBlockX() + 1.0, level.getMaxY(), chunk.getMaxBlockZ() + 1.0);
            count += level.getEntitiesOfClass(Entity.class, aabb,
                    candidate -> !(candidate instanceof ServerPlayer)).size();
        }
        return count;
    }

    // ---------------------------------------------------------------- validation

    /**
     * Pure validation, independent of any running server.
     *
     * <p>Equal size and identical shape cannot fail: the target is the source shape re-anchored, so those are
     * guaranteed by construction rather than checked. What remains is emptiness, the size cap, and a target
     * placed on its own source.
     *
     * @return a translation key describing the problem, or {@code null} when the pair is acceptable
     */
    static String validateSelections(ChunkLeapSelection source, ChunkLeapSelection target) {
        if (source == null || target == null) return "chunk_leap.reason.invalid_region";
        if (source.isEmpty() || target.isEmpty()) return "chunk_leap.reason.invalid_region";
        if (source.count() > MAX_SWAP_CHUNKS) return "chunk_leap.reason.too_large";
        // A target anchored where the source already is would swap every chunk with itself.
        if (source.dimension().equals(target.dimension())
                && source.originChunkX() == target.originChunkX()
                && source.originChunkZ() == target.originChunkZ()) {
            return "chunk_leap.reason.overlap";
        }
        return null;
    }

    static String validate(ChunkLeapSelection source, ChunkLeapSelection target,
                           net.minecraft.server.MinecraftServer server) {
        var reason = validateSelections(source, target);
        if (reason != null) return reason;
        var levelA = server.getLevel(source.dimension());
        var levelB = server.getLevel(target.dimension());
        if (levelA == null || levelB == null) return "chunk_leap.reason.dimension_missing";
        if (levelA == levelB) return null;
        // Cross-dimension: only the shared absolute-Y band can move, so an empty overlap is a refusal rather
        // than a silent no-op the player would not understand.
        if (ChunkVerticalBand.overlap(levelA, levelB).isEmpty()) {
            return "chunk_leap.reason.no_vertical_overlap";
        }
        return null;
    }
}
