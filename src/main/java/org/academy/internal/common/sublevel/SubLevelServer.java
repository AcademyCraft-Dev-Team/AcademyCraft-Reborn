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
import org.academy.internal.common.network.SubLevelStatePacket;
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

/** Phase 1 server owner: one plot storage and one shared physics universe per running server. */
@EventBusSubscriber(modid = AcademyCraft.MOD_ID)
public final class SubLevelServer {
    private static final Map<MinecraftServer, Runtime> RUNTIMES = new WeakHashMap<>();
    private static final Map<net.minecraft.server.level.ServerPlayer, Map<SubLevelId, org.academy.internal.common.structure.space.NativeStructureView>> SENT_VIEWS
            = new WeakHashMap<>();
    private static final boolean SEND_COLLISION_BOXES = Boolean.getBoolean("academy.sublevel.clientCollision");

    private SubLevelServer() {
    }

    @SubscribeEvent
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
            var library = NativePhysicsLibrary.openConfigured();
            var world = library.createWorld(PhysicsVector.ZERO, new PhysicsVector(0, -9.81, 0));
            var storage = new PlotStorage(plot);
            var universe = new SubLevelPhysicsUniverse(world);
            var manifest = new SubLevelManifest(server.getWorldPath(LevelResource.ROOT)
                    .resolve("data/academy/sublevels"));
            var terrain = new TerrainColliderCache(server.overworld(), world);
            var runtime = new SubLevelRuntime(server.overworld(), storage, universe, manifest, terrain);
            RUNTIMES.put(server, new Runtime(library, world, storage, runtime));
            var restored = runtime.restore();
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
        }
    }

    @SubscribeEvent
    public static void tick(ServerTickEvent.Post event) {
        var runtime = RUNTIMES.get(event.getServer());
        if (runtime == null) return;
        try {
            runtime.runtime.tick(1.0 / 20.0, 2);
            carryEntities(event.getServer(), runtime.runtime);
        } catch (RuntimeException | AssertionError failure) {
            AcademyCraft.LOGGER.error("SubLevel physics tick failed", failure);
            RUNTIMES.remove(event.getServer());
            runtime.close();
            return;
        }
        if (event.getServer().getTickCount() % 5 == 0) {
            try { sendSnapshots(event.getServer(), runtime.runtime); }
            catch (RuntimeException failure) { AcademyCraft.LOGGER.error("SubLevel snapshot send failed", failure); }
        }
        if (event.getServer().getTickCount() % 100 == 0) {
            try { runtime.runtime.persist(); }
            catch (RuntimeException failure) { AcademyCraft.LOGGER.error("SubLevel manifest checkpoint failed", failure); }
        }
    }

    @SubscribeEvent
    public static void stopping(ServerStoppingEvent event) {
        var runtime = RUNTIMES.remove(event.getServer());
        if (runtime != null) runtime.close();
        SENT_VIEWS.clear();
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

    /** Translation-only entity carry; passengers/vehicles are moved by their owning chain. */
    private static void carryTree(Entity root, Vec3 move) {
        var passengers = root.getPassengers();
        var targets = new ArrayList<Vec3>(passengers.size());
        for (var passenger : passengers) targets.add(passenger.position().add(move));
        root.teleportTo(root.getX() + move.x, root.getY() + move.y, root.getZ() + move.z);
        for (int i = 0; i < passengers.size(); i++) {
            var target = targets.get(i);
            passengers.get(i).teleportTo(target.x, target.y, target.z);
        }
    }

    private static void carryEntities(MinecraftServer server, SubLevelRuntime runtime) {
        var host = runtime.host();
        var entities = host.getAllEntities();
        if (!entities.iterator().hasNext()) return;
        for (var subLevel : runtime.subLevels()) {
            var previous = subLevel.previousPose();
            var current = subLevel.pose();
            if (previous.equals(current)) continue;
            var boxes = runtime.localCollisionBoxes(subLevel.id());
            if (boxes.isEmpty()) continue;
            var delta = current.worldOrigin().subtract(previous.worldOrigin());
            if (delta.lengthSqr() > 4.0 || delta.lengthSqr() < 1.0E-8) continue;
            for (var entity : entities) {
                if (entity.isPassenger() || entity.isSpectator()) continue;
                var local = previous.worldToLocalPoint(entity.position());
                double top = Double.NaN;
                for (var box : boxes) {
                    if (local.x < box.minX - 0.1 || local.x > box.maxX + 0.1
                            || local.z < box.minZ - 0.1 || local.z > box.maxZ + 0.1) continue;
                    if (local.y >= box.maxY - 0.3 && local.y <= box.maxY + 0.4) {
                        top = Math.max(Double.isNaN(top) ? Double.NEGATIVE_INFINITY : top, box.maxY);
                    }
                }
                if (Double.isNaN(top)) continue;
                var newWorld = current.localToWorldPoint(local);
                var move = newWorld.subtract(entity.position());
                carryTree(entity, move);
                AcademyCraft.LOGGER.debug("SubLevel carried entity {} by {}", entity.getType(), delta);
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
        for (var subLevel : runtime.subLevels()) {
            var boxes = SEND_COLLISION_BOXES ? runtime.localCollisionBoxes(subLevel.id()) : List.<AABB>of();
            var view = runtime.view(subLevel.id());
            for (var player : players) {
                if (player.level() != host) continue;
                if (player.position().distanceToSqr(subLevel.pose().worldOrigin()) > 256.0 * 256.0) continue;
                var sent = SENT_VIEWS.computeIfAbsent(player, ignored -> new HashMap<>());
                boolean fullView = sent.put(subLevel.id(), view) != view;
                SubLevelStatePacket.send(player, subLevel.id(), subLevel.plot(), subLevel.pose(), boxes,
                        view, false, fullView);
            }
        }
    }

    public static java.util.Optional<SubLevelRuntime> runtime(MinecraftServer server) {
        var runtime = RUNTIMES.get(server);
        return runtime == null ? java.util.Optional.empty() : java.util.Optional.of(runtime.runtime);
    }

    public static Status status(MinecraftServer server) {
        var runtime = RUNTIMES.get(server);
        if (runtime == null) return new Status(false, "unavailable", 0, 0, 0, 0, 0);
        var terrain = runtime.runtime.terrainDiagnostics();
        return new Status(true, "ready", runtime.storage.allocated(), runtime.runtime.size(),
                runtime.runtime.universe().bodyCount(), terrain.sections(), terrain.boxes());
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
        var created = runtime.runtime.create(id, bounds, pose, geometry.boxes());
        if (created.isEmpty()) {
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
        var runtime = RUNTIMES.get(server);
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
            var result = SubLevelCaptureService.capture(level, runtime.runtime, positions);
            var encoded = Unpooled.buffer();
            SubLevelStatePacket.CODEC.encode(encoded, new SubLevelStatePacket(result.id(), result.plot(),
                    runtime.runtime.get(result.id()).orElseThrow().pose(),
                    runtime.runtime.localCollisionBoxes(result.id()), runtime.runtime.view(result.id()), false));
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
                    runtime.storage.blockState(result.plot(), BlockPos.ZERO).isAir(), afterBreak);
            for (int x = 0; x < 2; x++) {
                for (int y = 0; y < 2; y++) {
                    for (int z = 0; z < 2; z++) {
                        var local = new BlockPos(x, y, z);
                        var candidate = runtime.storage.blockEntity(result.plot(), local);
                        if (candidate != null) {
                            AcademyCraft.LOGGER.info("SubLevel plot block entity at {}: {} item0={}", local,
                                    candidate.getClass().getSimpleName(),
                                    candidate instanceof ChestBlockEntity chestEntity
                                            ? chestEntity.getItem(0).toString() : "n/a");
                        }
                    }
                }
            }
            var stored = runtime.storage.blockEntity(result.plot(), new BlockPos(0, 1, 0));
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
        private final NativePhysicsLibrary library;
        private final NativePhysicsWorld world;
        private final PlotStorage storage;
        private final SubLevelRuntime runtime;
        private SubLevelId probe;

        private Runtime(NativePhysicsLibrary library, NativePhysicsWorld world,
                        PlotStorage storage, SubLevelRuntime runtime) {
            this.library = library;
            this.world = world;
            this.storage = storage;
            this.runtime = runtime;
        }

        private void close() {
            try { runtime.close(); }
            catch (RuntimeException failure) { AcademyCraft.LOGGER.warn("SubLevel runtime close failed", failure); }
            try { storage.close(); }
            catch (RuntimeException failure) { AcademyCraft.LOGGER.warn("SubLevel plot close failed", failure); }
            try { world.close(); }
            catch (RuntimeException failure) { AcademyCraft.LOGGER.warn("SubLevel world close failed", failure); }
            try { library.close(); }
            catch (RuntimeException failure) { AcademyCraft.LOGGER.warn("SubLevel library close failed", failure); }
        }
    }
}
