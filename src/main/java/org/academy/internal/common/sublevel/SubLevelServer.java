package org.academy.internal.common.sublevel;

import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.PathNavigationRegion;
import net.minecraft.world.level.storage.LevelResource;
import io.netty.buffer.Unpooled;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import org.academy.AcademyCraft;
import org.academy.api.common.structure.StructureInteractionController;
import org.academy.internal.common.network.SubLevelMiningProgressPacket;
import org.academy.internal.common.network.SubLevelPosePacket;
import org.academy.internal.common.network.SubLevelStatePacket;
import org.academy.internal.common.structure.NativeStructureCollisionBoxes;
import org.academy.internal.common.structure.StructureBackendRouter;
import org.academy.internal.common.structure.physics.NativePhysicsLibrary;
import org.academy.internal.common.structure.physics.NativePhysicsWorld;
import org.academy.internal.common.structure.physics.PhysicsPose;
import org.academy.internal.common.structure.physics.PhysicsQuaternion;
import org.academy.internal.common.structure.physics.PhysicsVector;
import org.academy.internal.common.structure.space.NativeStructureDimensions;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;

/** One plot allocator per server, with an isolated physics universe for each host dimension. */
@EventBusSubscriber(modid = AcademyCraft.MOD_ID)
public final class SubLevelServer {
    private static final Map<MinecraftServer, Runtime> RUNTIMES = new WeakHashMap<>();
    private static final Map<net.minecraft.server.level.ServerPlayer, Map<SubLevelId, Sent>> SENT_VIEWS
            = new WeakHashMap<>();
    /** Feedback sink for asynchronous mining receipts; it never trusts client progress. */
    public static final StructureInteractionController.Feedback MINING_FEEDBACK = SubLevelServer::publishMining;
    private static final boolean SEND_COLLISION_BOXES = Boolean.parseBoolean(
            System.getProperty("academy.sublevel.clientCollision", "true"));
    private static final int MAX_SYNC_BOXES = Math.min(16_384, Math.max(1, Integer.getInteger(
            "academy.sublevel.maxSyncBoxes", 16_384)));
    /** Empty plots kept resident; zero disables prewarming. Chunk work stays on the server thread. */
    private static final int PLOT_PREWARM = Math.max(0, Math.min(4, Integer.getInteger(
            "academy.sublevel.plotPrewarm", 2)));

    private SubLevelServer() {
    }

    @SubscribeEvent(priority = net.neoforged.bus.api.EventPriority.HIGH)
    public static void started(ServerStartedEvent event) {
        var server = event.getServer();
        if (!StructureBackendRouter.nativeEnabled(server)) {
            AcademyCraft.LOGGER.info("SubLevel backend skipped because the native structure backend is not selected");
            return;
        }
        var plot = server.getLevel(NativeStructureDimensions.PLOT_KEY);
        if (plot == null) {
            AcademyCraft.LOGGER.error("SubLevel plot dimension is not loaded; native SubLevel backend disabled");
            return;
        }
        try {
            var storage = new PlotStorage(plot);
            var owner = new Runtime(storage);
            RUNTIMES.put(server, owner);
            var hosts = new ArrayList<ServerLevel>();
            for (var host : server.getAllLevels()) {
                if (!NativeStructureDimensions.isStorageLevel(host.dimension())) hosts.add(host);
            }
            var manifests = SubLevelManifest.loadHosts(server.getWorldPath(LevelResource.ROOT)
                    .resolve("data/academy/sublevels"), hosts.stream().map(host -> host.dimension().identifier()).toList());
            var reservations = new HashMap<SubLevelId, PlotStorage.Lease>();
            for (var saved : manifests.values()) for (var entry : saved.entries()) {
                if (entry.state() == SubLevelState.REMOVED) continue;
                var lease = storage.claim(entry.plot()).orElseThrow(() ->
                        new IllegalStateException("Cannot reserve persisted SubLevel plot " + entry.plot()));
                reservations.put(entry.id(), lease);
            }
            int restored = 0;
            for (var host : hosts) {
                var owned = HostRuntime.open(host, storage, manifests.get(host.dimension().identifier()));
                owner.hosts.put(host.dimension(), owned);
                if (host == server.overworld()) owner.runtime = owned.runtime;
                restored += owned.runtime.restore(reservations);
                AcademyCraft.LOGGER.info("SubLevel host ready: host={} stored={} active={}",
                        host.dimension().identifier(), owned.runtime.size(), owned.runtime.universe().activeCount());
            }
            for (var owned : owner.hosts.values()) {
                SubLevelCaptureJournal.recover(server, owned.runtime);
                SubLevelStaticizeJournal.recover(server, owned.runtime);
            }
            var runtime = owner.runtime;
            var universe = runtime.universe();
            var manifest = manifests.get(server.overworld().dimension().identifier());
            for (var entry : manifest.entries()) {
                AcademyCraft.LOGGER.debug("SubLevel manifest entry: id={} plot={} state={}",
                        entry.id().value(), entry.plot(), entry.state());
            }
            AcademyCraft.LOGGER.info("SubLevel backend ready: plot={} capacity={} stored={} active={} restored={}",
                    plot.dimension().identifier(), storage.capacity(), runtime.size(), universe.activeCount(), restored);
            if (Boolean.getBoolean("academy.sublevel.terrainProbeOnStart")) {
                var spawn = server.overworld().getRespawnData().pos();
                for (int dx = -1; dx <= 1; dx++) {
                    for (int dz = -1; dz <= 1; dz++) {
                        server.overworld().getChunk((spawn.getX() >> 4) + dx, (spawn.getZ() >> 4) + dz);
                    }
                }
                var diag = runtime.probeTerrain(new AABB(spawn.getX(), spawn.getY(), spawn.getZ(),
                        spawn.getX() + 1, spawn.getY() + 1, spawn.getZ() + 1));
                AcademyCraft.LOGGER.info("SubLevel terrain probe: {}", diag);
                if (Boolean.getBoolean("academy.sublevel.terrainProbeAndStop")) {
                    requestStop(server);
                }
            }
            if (Boolean.getBoolean("academy.sublevel.captureProbeOnStart")) {
                var spawn = server.overworld().getRespawnData().pos();
                var base = spawn.above(20);
                var result = captureProbe(server, server.overworld(), base);
                AcademyCraft.LOGGER.info("SubLevel capture probe: {}", result);
                if (Boolean.getBoolean("academy.sublevel.captureProbeAndStop")) {
                    requestStop(server);
                }
            }
            if (Boolean.getBoolean("academy.sublevel.persistentProbeOnStart")) {
                int requested = Integer.getInteger("academy.sublevel.persistentProbeCount", 256);
                var probe = persistentProbe(server, runtime, requested);
                AcademyCraft.LOGGER.info("SubLevel persistent probe: {}", probe);
                if (Boolean.getBoolean("academy.sublevel.persistentProbeAndStop")) {
                    requestStop(server);
                }
            }
            if (Boolean.getBoolean("academy.sublevel.probeOnStart")) {
                var probe = probe(server);
                AcademyCraft.LOGGER.info("SubLevel startup probe: {}", probe);
                if (Boolean.getBoolean("academy.sublevel.probeAndStop")) {
                    if (Boolean.getBoolean("academy.sublevel.probePersist")) {
                        AcademyCraft.LOGGER.info("SubLevel startup probe persisted: {}", RUNTIMES.get(server).probe);
                    } else {
                        AcademyCraft.LOGGER.info("SubLevel startup probe cleanup: {}", removeProbe(server));
                    }
                    server.execute(() -> server.halt(false));
                }
            }
            if (Boolean.getBoolean("academy.sublevel.verifyOnStart")) {
                AcademyCraft.LOGGER.info("SubLevel restore verify: stored={} active={} entries={}",
                        runtime.size(), runtime.universe().activeCount(), manifest.size());
                var first = runtime.subLevels().stream().findFirst().orElse(null);
                if (first != null) {
                    var restoredEntity = storage.blockEntity(first.plot(), new BlockPos(0, 1, 0));
                    AcademyCraft.LOGGER.info("SubLevel restore first plot={} blockEntity={} item0={}", first.plot(),
                            restoredEntity == null ? "null" : restoredEntity.getClass().getSimpleName(),
                            restoredEntity instanceof ChestBlockEntity restoredChest
                                    ? restoredChest.getItem(0).toString() : "n/a");
                }
                if (Boolean.getBoolean("academy.sublevel.verifyAndStop")) {
                    requestStop(server);
                }
            }
        } catch (IOException | RuntimeException | LinkageError failure) {
            AcademyCraft.LOGGER.error("SubLevel backend unavailable; legacy structure path remains active", failure);
            shutdown(server);
        }
    }

    @SubscribeEvent
    public static void tick(ServerTickEvent.Post event) {
        var runtime = RUNTIMES.get(event.getServer());
        try {
            // Common leases also exist when the optional Sable backend replaces this runtime.
            org.academy.api.common.structure.StructureDirectControl.tick(event.getServer());
            if (runtime == null) return;
            try {
                runtime.storage.prewarmTick(PLOT_PREWARM);
            } catch (RuntimeException failure) {
                AcademyCraft.LOGGER.warn("SubLevel plot prewarm failed", failure);
            }
            SubLevelTraction.tick(event.getServer());
            SubLevelMotionController.tick(event.getServer());
            SubLevelDirectControl.tick(event.getServer());
            for (var owned : runtime.hosts.values()) {
                owned.runtime.tick(1.0 / 20.0, 2);
                carryEntities(event.getServer(), owned.runtime);
            }
        } catch (RuntimeException | AssertionError failure) {
            AcademyCraft.LOGGER.error("SubLevel physics tick failed", failure);
            shutdown(event.getServer());
            return;
        }
        try {
            // Interaction sessions advance once per server tick; a session fault must not tear
            // down the physics backend or every other structure.
            SubLevelEnhancementBootstrap.INTERACTIONS.tick(event.getServer(), MINING_FEEDBACK);
        } catch (RuntimeException | AssertionError failure) {
            AcademyCraft.LOGGER.error("SubLevel interaction tick failed", failure);
        }
        try {
            // Moving bodies need one pose delta per physics tick; unchanged views and poses stay silent.
            for (var owned : runtime.hosts.values()) sendSnapshots(event.getServer(), owned.runtime);
        }
        catch (RuntimeException failure) { AcademyCraft.LOGGER.error("SubLevel snapshot send failed", failure); }
        if (event.getServer().getTickCount() % 100 == 0) {
            try {
                for (var owned : runtime.hosts.values()) owned.runtime.persist();
            }
            catch (RuntimeException failure) { AcademyCraft.LOGGER.error("SubLevel manifest checkpoint failed", failure); }
        }
    }

    @SubscribeEvent
    public static void stopping(ServerStoppingEvent event) {
        shutdown(event.getServer());
        SubLevelMotionController.clear();
        SubLevelDirectControl.clear();
        SubLevelActivationRegistry.clear();
        SENT_VIEWS.clear();
    }

    /** Revokes live controls and client projections when this backend is stopped or disabled. */
    public static void shutdown(MinecraftServer server) {
        if (!server.isSameThread()) throw new IllegalStateException("SubLevel shutdown requires the server thread");
        var runtime = RUNTIMES.remove(server);
        try {
            for (var entry : Map.copyOf(SENT_VIEWS).entrySet()) {
                var player = entry.getKey();
                if (player.level().getServer() != server) continue;
                try {
                    if (!player.hasDisconnected()) {
                        for (var id : List.copyOf(entry.getValue().keySet())) {
                            try {
                                SubLevelStatePacket.send(player, id, new PlotPos(0, 0),
                                        org.academy.api.common.structure.StructurePose.identity(Vec3.ZERO), List.of(),
                                        org.academy.internal.common.structure.space.NativeStructureView.EMPTY, true);
                            } catch (RuntimeException failure) {
                                AcademyCraft.LOGGER.warn("Could not remove SubLevel client snapshot {}", id, failure);
                            }
                        }
                    }
                } finally {
                    SENT_VIEWS.remove(player);
                }
            }
        } finally {
            try {
                SubLevelTraction.clear(server);
                // The missing runtime makes every SubLevel probe unavailable; unrelated
                // backend leases are retained by this server-scoped lifecycle sweep.
                org.academy.api.common.structure.StructureDirectControl.tick(server);
                SubLevelEnhancementBootstrap.INTERACTIONS.close(server, MINING_FEEDBACK);
                SubLevelMotionController.clear();
                SubLevelDirectControl.clear();
                SubLevelTerrainBreaker.clear();
            } finally {
                if (runtime != null) runtime.close();
            }
        }
    }

    /** A new client level has no full-view baseline, even when the server player is unchanged. */
    @SubscribeEvent
    public static void dimensionChanged(net.neoforged.neoforge.event.entity.player.PlayerEvent.PlayerChangedDimensionEvent event) {
        SENT_VIEWS.remove(event.getEntity());
        if (event.getEntity() instanceof net.minecraft.server.level.ServerPlayer player) {
            SubLevelEnhancementBootstrap.INTERACTIONS.cancelActor(player,
                    StructureInteractionController.CancelReason.ACTOR_UNAVAILABLE, MINING_FEEDBACK);
        }
    }

    @SubscribeEvent
    public static void playerRespawned(net.neoforged.neoforge.event.entity.player.PlayerEvent.PlayerRespawnEvent event) {
        SENT_VIEWS.remove(event.getEntity());
        if (event.getEntity() instanceof net.minecraft.server.level.ServerPlayer player) {
            SubLevelEnhancementBootstrap.INTERACTIONS.cancelActor(player,
                    StructureInteractionController.CancelReason.ACTOR_UNAVAILABLE, MINING_FEEDBACK);
        }
    }

    @SubscribeEvent
    public static void playerLoggedOut(net.neoforged.neoforge.event.entity.player.PlayerEvent.PlayerLoggedOutEvent event) {
        SENT_VIEWS.remove(event.getEntity());
        if (event.getEntity() instanceof net.minecraft.server.level.ServerPlayer player) {
            SubLevelEnhancementBootstrap.INTERACTIONS.cancelActor(player,
                    StructureInteractionController.CancelReason.ACTOR_UNAVAILABLE, MINING_FEEDBACK);
        }
    }

    /** Probe stop must not re-enter the server task queue from ServerStartedEvent. */
    private static void requestStop(MinecraftServer server) {
        var thread = new Thread(() -> {
            try {
                Thread.sleep(250L);
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
                return;
            }
            try {
                server.halt(false);
            } catch (RuntimeException failure) {
                AcademyCraft.LOGGER.warn("SubLevel probe stop failed", failure);
            }
        }, "academy-sublevel-probe-stop");
        thread.setDaemon(true);
        thread.start();
    }

    /**
     * Exact support carry: an entity resting on the previous pose is moved through the new pose
     * at most once. Only entities near a moved body are queried, and passengers share one delta.
     */
    private static void carryEntities(MinecraftServer server, SubLevelRuntime runtime) {
        var host = runtime.host();
        var subLevels = runtime.subLevels();
        try {
            var moved = new ArrayList<SubLevel>();
            for (var subLevel : subLevels) {
                if (!published(subLevel) || subLevel.carryPose().equals(subLevel.pose())) continue;
                moved.add(subLevel);
            }
            if (moved.isEmpty()) return;
            var applied = java.util.Collections.newSetFromMap(new java.util.IdentityHashMap<Entity, Boolean>());
            for (var subLevel : moved) {
                var boxes = runtime.localCollisionBoxes(subLevel.id());
                if (boxes.isEmpty()) continue;
                var previous = subLevel.carryPose();
                var current = subLevel.pose();
                var previousBounds = NativeStructureCollisionBoxes.transformBox(subLevel.localBounds(), previous);
                var sweep = previousBounds.minmax(subLevel.worldBounds()).inflate(1.0);
                // Query each body's neighborhood, never the world-spanning union of distant bodies.
                var entities = host.getEntities((Entity) null, sweep);
                for (var entity : entities) {
                    SubLevelContactResolver.carry(host, entity, previous, current, boxes, applied, null);
                }
            }
        } finally {
            // Consume even when there were no nearby riders, the body became inactive, or
            // physics is waiting for terrain. Explicit rotate/shift deltas survive until here.
            for (var subLevel : subLevels) {
                subLevel.consumeCarryPose();
            }
        }
    }

    private static boolean snapshotLogged;

    private static void sendSnapshots(MinecraftServer server, SubLevelRuntime runtime) {
        var host = runtime.host();
        var players = host.players();
        if (players.isEmpty()) return;
        if (!snapshotLogged) {
            snapshotLogged = true;
            AcademyCraft.LOGGER.info("SubLevel snapshots started: players={} sublevels={}", players.size(), runtime.size());
        }
        long started = System.nanoTime();
        int published = 0;
        for (var subLevel : runtime.subLevels()) {
            if (!published(subLevel)) continue;
            published++;
            var boxes = SEND_COLLISION_BOXES
                    ? runtime.localCollisionBoxes(subLevel.id())
                    : List.<AABB>of();
            if (boxes.size() > MAX_SYNC_BOXES) {
                boxes = boxes.subList(0, MAX_SYNC_BOXES);
            }
            var view = runtime.view(subLevel.id());
            var pose = subLevel.pose();
            for (var player : players) {
                if (player.level() != host) continue;
                if (player.position().distanceToSqr(pose.worldOrigin()) > 256.0 * 256.0) continue;
                var sent = SENT_VIEWS.computeIfAbsent(player, ignored -> new HashMap<>());
                var previous = sent.get(subLevel.id());
                boolean fullView = previous == null || previous.view() != view;
                if (fullView) {
                    SubLevelStatePacket.send(player, subLevel.id(), subLevel.plot(), pose, boxes, view, false, true);
                    sent.put(subLevel.id(), new Sent(view, pose));
                } else if (!previous.pose().equals(pose)) {
                    // Local collision boxes and view cells never change with a pose, so a delta
                    // packet is enough and the client rebuilds the oriented contact data.
                    SubLevelPosePacket.send(player, subLevel.id(), pose);
                    sent.put(subLevel.id(), new Sent(view, pose));
                }
            }
        }
        for (var player : players) {
            var sent = SENT_VIEWS.get(player);
            if (sent == null) continue;
            var iterator = sent.keySet().iterator();
            while (iterator.hasNext()) {
                var id = iterator.next();
                var subLevel = runtime.get(id).orElse(null);
                if (subLevel != null && published(subLevel)
                        && player.position().distanceToSqr(subLevel.pose().worldOrigin()) <= 256.0 * 256.0) continue;
                SubLevelStatePacket.send(player, id, new PlotPos(0, 0),
                        org.academy.api.common.structure.StructurePose.identity(Vec3.ZERO), List.of(),
                        org.academy.internal.common.structure.space.NativeStructureView.EMPTY, true);
                iterator.remove();
            }
        }
        runtime.recordSnapshot(System.nanoTime() - started, published);
    }

    static boolean published(SubLevel subLevel) {
        return subLevel.state() == SubLevelState.ACTIVE || subLevel.state() == SubLevelState.SLEEPING
                || subLevel.state() == SubLevelState.INACTIVE;
    }

    /** Sends one authoritative mining receipt; the actor entity id scopes multi-player cracks. */
    public static void publishMining(net.minecraft.server.level.ServerPlayer recipient, java.util.UUID actorId,
                                     StructureInteractionController.MiningReceipt receipt) {
        if (recipient == null || receipt == null || recipient.connection == null
                || !recipient.connection.isAcceptingMessages()) return;
        try {
            var server = recipient.level().getServer();
            net.minecraft.server.level.ServerPlayer actor = server == null ? null
                    : server.getPlayerList().getPlayer(actorId);
            SubLevelMiningProgressPacket.send(recipient, receipt, actor == null ? 0 : actor.getId());
        } catch (RuntimeException failure) {
            AcademyCraft.LOGGER.warn("Could not publish SubLevel mining receipt to {}", recipient.getUUID(), failure);
        }
    }

    private record Sent(org.academy.internal.common.structure.space.NativeStructureView view,
                        org.academy.api.common.structure.StructurePose pose) {
    }

    /** Host mutations invalidate only the already running world's terrain cache. */
    public static void onBlockChanged(ServerLevel host, BlockPos position) {
        if (!host.getServer().isSameThread()) return;
        runtime(host).ifPresent(runtime -> runtime.onHostBlockChanged(position));
    }

    public static java.util.Optional<SubLevelRuntime> runtime(MinecraftServer server) {
        return runtime(server.overworld());
    }

    /** Read-only lookup: never initializes a backend or accepts a detached/replaced host level. */
    public static java.util.Optional<SubLevelRuntime> runtime(ServerLevel host) {
        var server = host.getServer();
        if (!server.isSameThread()) throw new IllegalStateException("SubLevel lookup requires the server thread");
        if (server.getLevel(host.dimension()) != host || NativeStructureDimensions.isStorageLevel(host.dimension())) {
            return java.util.Optional.empty();
        }
        var owner = RUNTIMES.get(server);
        var owned = owner == null ? null : owner.hosts.get(host.dimension());
        return owned == null || owned.runtime.host() != host ? java.util.Optional.empty()
                : java.util.Optional.of(owned.runtime);
    }

    /** Identity lookup for internal APIs whose caller already owns the SubLevel identifier. */
    public static java.util.Optional<SubLevelRuntime> runtime(MinecraftServer server, SubLevelId id) {
        return runtimes(server).stream().filter(runtime -> runtime.get(id).isPresent()).findFirst();
    }

    public static List<SubLevelRuntime> runtimes(MinecraftServer server) {
        if (!server.isSameThread()) throw new IllegalStateException("SubLevel lookup requires the server thread");
        var owner = RUNTIMES.get(server);
        return owner == null ? List.of() : owner.hosts.values().stream().map(owned -> owned.runtime).toList();
    }

    public static Status status(MinecraftServer server) {
        var runtime = RUNTIMES.get(server);
        if (runtime == null) return new Status(false, "unavailable", 0, 0, 0, 0, 0);
        var hosts = runtimes(server);
        return new Status(true, "ready", runtime.storage.allocated(), hosts.stream().mapToInt(SubLevelRuntime::size).sum(),
                hosts.stream().mapToInt(host -> host.universe().bodyCount()).sum(),
                hosts.stream().mapToInt(host -> host.terrainDiagnostics().sections()).sum(),
                hosts.stream().mapToInt(host -> host.terrainDiagnostics().boxes()).sum());
    }

    /** Allocates one real plot and creates a one-block stone SubLevel for an in-game smoke check. */
    public static String probe(MinecraftServer server) {
        var runtime = RUNTIMES.get(server);
        if (runtime == null) return "unavailable";
        if (runtime.probe != null) removeProbe(server);
        var lease = runtime.storage.allocate().orElse(null);
        if (lease == null) return "plot_capacity_exhausted";
        var local = new BlockPos(0, 0, 0);
        runtime.storage.setBlock(lease.plot(), local, Blocks.STONE.defaultBlockState(), 3);
        var bounds = new AABB(0, 0, 0, 1, 1, 1);
        var geometry = PlotGeometry.capture(runtime.storage, lease.plot(), bounds);
        if (geometry.boxes().isEmpty()) {
            runtime.storage.release(lease);
            return "empty_geometry";
        }
        var world = runtime.storage.worldPosition(lease.plot(), local);
        var pose = new PhysicsPose(new PhysicsVector(world.getX(), world.getY(), world.getZ()),
                PhysicsQuaternion.IDENTITY);
        var id = SubLevelId.random();
        try {
            runtime.runtime.createInLease(id, lease, bounds, pose, geometry.boxes());
        } catch (RuntimeException failure) {
            runtime.storage.release(lease);
            return "create_failed";
        }
        runtime.probe = id;
        runtime.runtime.persist();
        return "created " + id.value() + " plot=" + lease.plot() + " boxes=" + geometry.boxes().size();
    }

    private static String persistentProbe(MinecraftServer server, SubLevelRuntime runtime, int requested) {
        var level = server.overworld();
        var base = server.overworld().getRespawnData().pos().above(30);
        int active = 0;
        int created = 0;
        for (int index = 0; index < requested; index++) {
            var origin = base.offset((index % 16) * 3, 0, (index / 16) * 3);
            var positions = new ArrayList<BlockPos>(8);
            for (int x = 0; x < 2; x++) {
                for (int y = 0; y < 2; y++) {
                    for (int z = 0; z < 2; z++) {
                        var position = origin.offset(x, y, z);
                        level.setBlock(position, Blocks.STONE.defaultBlockState(), 3);
                        positions.add(position);
                    }
                }
            }
            var result = SubLevelCaptureService.capture(level, runtime, positions);
            created++;
            if (runtime.get(result.id()).orElseThrow().state() == SubLevelState.ACTIVE) active++;
        }
        runtime.persist();
        return "requested=" + requested + " created=" + created + " active=" + active
                + " inactive=" + (created - active) + " universe=" + runtime.universe().activeCount()
                + " stored=" + runtime.size();
    }

    /** Debug command entry: writes a 2x2x2 stone selection above the player and captures it. */
    public static String captureProbe(MinecraftServer server, net.minecraft.server.level.ServerLevel level, BlockPos base) {
        var runtime = runtime(level).orElse(null);
        if (runtime == null) return "unavailable";
        var positions = new ArrayList<BlockPos>(8);
        for (int x = 0; x < 2; x++) {
            for (int y = 0; y < 2; y++) {
                for (int z = 0; z < 2; z++) {
                    var position = base.offset(x, y, z);
                    level.setBlock(position, Blocks.STONE.defaultBlockState(), 3);
                    positions.add(position);
                }
            }
        }
        var chestPosition = base.offset(0, 1, 0);
        level.setBlock(chestPosition, Blocks.CHEST.defaultBlockState(), 3);
        var chest = new ChestBlockEntity(chestPosition, Blocks.CHEST.defaultBlockState());
        chest.setItem(0, new ItemStack(Items.DIAMOND, 3));
        level.setBlockEntity(chest);
        try {
            var result = SubLevelCaptureService.capture(level, runtime, positions);
            var encoded = Unpooled.buffer();
            SubLevelStatePacket.CODEC.encode(encoded, new SubLevelStatePacket(result.id(), result.plot(),
                    runtime.get(result.id()).orElseThrow().pose(),
                    runtime.localCollisionBoxes(result.id()), runtime.view(result.id()), false));
            var decodedView = SubLevelStatePacket.CODEC.decode(encoded).view();
            encoded.release();
            AcademyCraft.LOGGER.info("SubLevel snapshot codec probe: cells={}", decodedView.cells().size());
            var origin = result.origin();
            var insideQuery = new AABB(origin.x + 0.1, origin.y + 0.1, origin.z + 0.1,
                    origin.x + 1.9, origin.y + 1.9, origin.z + 1.9);
            var outsideQuery = new AABB(origin.x + 3.0, origin.y + 0.1, origin.z + 0.1,
                    origin.x + 4.0, origin.y + 1.9, origin.z + 1.9);
            int inside = 0, outside = 0;
            for (var shape : level.getBlockCollisions(null, insideQuery)) if (!shape.isEmpty()) inside++;
            for (var shape : level.getBlockCollisions(null, outsideQuery)) if (!shape.isEmpty()) outside++;
            AcademyCraft.LOGGER.info("SubLevel collision probe: inside={} outside={}", inside, outside);
            var originBlock = BlockPos.containing(origin);
            var navigation = new PathNavigationRegion(level, originBlock.offset(-2, -2, -2),
                    originBlock.offset(4, 4, 4));
            int navInside = 0, navOutside = 0;
            for (var shape : navigation.getBlockCollisions(null, insideQuery)) if (!shape.isEmpty()) navInside++;
            for (var shape : navigation.getBlockCollisions(null, outsideQuery)) if (!shape.isEmpty()) navOutside++;
            AcademyCraft.LOGGER.info("SubLevel path collision probe: inside={} outside={}", navInside, navOutside);
            var broke = SubLevelInteractionService.breakBlock(server, result.id(), BlockPos.ZERO, null);
            int afterBreak = 0;
            for (var shape : level.getBlockCollisions(null, insideQuery)) if (!shape.isEmpty()) afterBreak++;
            AcademyCraft.LOGGER.info("SubLevel break probe: removed={} air={} inside_after={}", broke,
                    runtime.storage().blockState(result.plot(), BlockPos.ZERO).isAir(), afterBreak);
            for (int x = 0; x < 2; x++) {
                for (int y = 0; y < 2; y++) {
                    for (int z = 0; z < 2; z++) {
                        var local = new BlockPos(x, y, z);
                        var candidate = runtime.storage().blockEntity(result.plot(), local);
                        if (candidate != null) {
                            AcademyCraft.LOGGER.info("SubLevel plot block entity at {}: {} item0={}", local,
                                    candidate.getClass().getSimpleName(),
                                    candidate instanceof ChestBlockEntity chestEntity
                                            ? chestEntity.getItem(0).toString() : "n/a");
                        }
                    }
                }
            }
            var stored = runtime.storage().blockEntity(result.plot(), new BlockPos(0, 1, 0));
            var contents = stored instanceof ChestBlockEntity storedChest ? storedChest.getItem(0).toString() : "none";
            AcademyCraft.LOGGER.info("SubLevel capture chest contents: {}", contents);
            return "captured id=" + result.id().value() + " plot=" + result.plot()
                    + " blocks=" + result.blocks() + " boxes=" + result.boxes()
                    + " block_entities=" + result.blockEntities();
        } catch (RuntimeException failure) {
            return "capture_failed: " + failure.getMessage();
        }
    }

    /** Test hook: shifts the first SubLevel and removes its body so carry is deterministic. */
    public static String launchFirst(MinecraftServer server, Vec3 direction, double speed) {
        var runtime = RUNTIMES.get(server);
        if (runtime == null) return "unavailable";
        var subLevel = runtime.runtime.subLevels().stream().findFirst().orElse(null);
        if (subLevel == null) return "none";
        return runtime.runtime.launch(subLevel.id(), direction, speed) ? "launched " + subLevel.id().value() : "failed";
    }

    public static String rotateFirst(MinecraftServer server, double yawDegrees) {
        var runtime = RUNTIMES.get(server);
        if (runtime == null) return "unavailable";
        var subLevel = runtime.runtime.subLevels().stream().findFirst().orElse(null);
        if (subLevel == null) return "none";
        return runtime.runtime.rotate(subLevel.id(), yawDegrees) ? "rotated " + subLevel.id().value() : "failed";
    }

    public static String shiftFirst(MinecraftServer server, double dx, double dy, double dz) {
        var runtime = RUNTIMES.get(server);
        if (runtime == null) return "unavailable";
        var subLevel = runtime.runtime.subLevels().stream().findFirst().orElse(null);
        if (subLevel == null) return "none";
        runtime.runtime.deactivate(subLevel.id());
        boolean shifted = runtime.runtime.shift(subLevel.id(), new Vec3(dx, dy, dz));
        return shifted ? "shifted " + subLevel.id().value() : "failed";
    }

    public static String removeProbe(MinecraftServer server) {
        var runtime = RUNTIMES.get(server);
        if (runtime == null || runtime.probe == null) return "no_probe";
        var id = runtime.probe;
        runtime.probe = null;
        var subLevel = runtime.runtime.get(id).orElse(null);
        if (subLevel != null) {
            try { runtime.storage.setBlock(subLevel.plot(), BlockPos.ZERO, Blocks.AIR.defaultBlockState(), 3); }
            catch (RuntimeException ignored) { }
        }
        var removed = runtime.runtime.remove(id);
        runtime.runtime.persist();
        return removed ? "removed " + id.value() : "missing " + id.value();
    }

    public record Status(boolean ready, String detail, int allocated, int subLevels, int bodies,
                         int terrainSections, int terrainBoxes) {
    }

    private static final class Runtime {
        private final PlotStorage storage;
        private final Map<net.minecraft.resources.ResourceKey<net.minecraft.world.level.Level>, HostRuntime> hosts
                = new java.util.LinkedHashMap<>();
        // Compatibility for the existing overworld-only debug probes.
        private SubLevelRuntime runtime;
        private SubLevelId probe;

        private Runtime(PlotStorage storage) {
            this.storage = storage;
        }

        private void close() {
            for (var owned : List.copyOf(hosts.values())) owned.close();
            hosts.clear();
            try { storage.close(); }
            catch (RuntimeException failure) { AcademyCraft.LOGGER.warn("SubLevel plot close failed", failure); }
        }
    }

    private static final class HostRuntime {
        private final NativePhysicsLibrary library;
        private final NativePhysicsWorld world;
        private final SubLevelRuntime runtime;

        private HostRuntime(NativePhysicsLibrary library, NativePhysicsWorld world, SubLevelRuntime runtime) {
            this.library = library;
            this.world = world;
            this.runtime = runtime;
        }

        private static HostRuntime open(ServerLevel host, PlotStorage storage, SubLevelManifest manifest) throws IOException {
            var library = NativePhysicsLibrary.openConfigured();
            try {
                var world = library.createWorld(PhysicsVector.ZERO, new PhysicsVector(0, -9.81, 0));
                var universe = new SubLevelPhysicsUniverse(world);
                var terrain = new TerrainColliderCache(host, world);
                var runtime = new SubLevelRuntime(host, storage, universe, manifest, terrain);
                return new HostRuntime(library, world, runtime);
            } catch (RuntimeException | Error failure) {
                try { library.close(); }
                catch (RuntimeException | Error cleanup) { failure.addSuppressed(cleanup); }
                throw failure;
            }
        }

        private void close() {
            try { runtime.close(); }
            catch (RuntimeException failure) { AcademyCraft.LOGGER.warn("SubLevel runtime close failed", failure); }
            try { world.close(); }
            catch (RuntimeException failure) { AcademyCraft.LOGGER.warn("SubLevel world close failed", failure); }
            try { library.close(); }
            catch (RuntimeException failure) { AcademyCraft.LOGGER.warn("SubLevel library close failed", failure); }
        }
    }
}
