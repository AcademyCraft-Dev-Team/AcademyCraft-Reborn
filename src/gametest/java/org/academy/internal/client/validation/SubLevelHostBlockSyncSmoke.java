package org.academy.internal.client.validation;

import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.FullChunkStatus;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.Ticket;
import net.minecraft.server.level.TicketType;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.TicketStorage;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import org.academy.AcademyCraft;
import org.academy.api.common.structure.StructureCaptureRequest;
import org.academy.api.common.structure.StructureCaptureResult;
import org.academy.api.common.structure.StructureRef;
import org.academy.api.common.structure.StructureTransactions;
import org.academy.internal.common.structure.StructureHostBlockUpdates;
import org.academy.internal.common.structure.space.NativeStructureTickets;
import org.academy.internal.common.sublevel.SubLevelServer;
import org.academy.internal.common.sublevel.SubLevelStructureRefs;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Real packets and real ticket demotion in an isolated world; no production synchronization bypass. */
@EventBusSubscriber(modid = AcademyCraft.MOD_ID, value = Dist.CLIENT)
public final class SubLevelHostBlockSyncSmoke {
    private static final boolean ENABLED = Boolean.getBoolean("academy.sublevelHostBlockSyncSmoke");
    private static final int VIEW_DISTANCE = 10;
    private static final int SIMULATION_DISTANCE = 5;
    private static final int TARGET_CHUNK_DISTANCE = 8;
    private static final int FLAGS = Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE
            | Block.UPDATE_SKIP_ON_PLACE | Block.UPDATE_SKIP_BLOCK_ENTITY_SIDEEFFECTS
            | Block.UPDATE_SUPPRESS_DROPS;
    private static final Map<Long, List<Ticket>> REMOVED_TICKETS = new LinkedHashMap<>();
    private static volatile Phase phase = Phase.START;
    private static volatile MinecraftServer server;
    private static volatile ServerLevel host;
    private static volatile UUID playerId;
    private static volatile BlockPos capturePosition, controlPosition;
    private static volatile String failure;
    private static ChunkPos targetChunk;
    private static StructureCaptureResult capture;
    private static StructureRef created;
    private static boolean ownsFullTicket, cleaned;
    private static int ticks, age;
    private static Phase observedPhase = Phase.START;

    private SubLevelHostBlockSyncSmoke() {
    }

    @SubscribeEvent
    public static void client(ClientTickEvent.Post event) {
        if (!ENABLED || phase == Phase.FINISHED) return;
        var mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null) return;
        try {
            require(failure == null, failure);
            require(++ticks < 1400, "Host block sync timeout: " + phase);
            if (phase != observedPhase) {
                observedPhase = phase;
                age = 0;
            }
            age++;
            switch (phase) {
                case START -> {
                    require(mc.gameDirectory.toPath().toAbsolutePath().normalize().getFileName().toString()
                            .startsWith("native-sublevel-host-sync-"), "Host block sync requires a disposable copy");
                    server = mc.getSingleplayerServer();
                    require(server != null, "Host block sync requires an integrated server");
                    playerId = mc.player.getUUID();
                    // Ordinary 26.2 clients reject simulation distances below 5 and silently
                    // fall back to the initial value (12); never use the debug-only value 2.
                    mc.options.renderDistance().set(VIEW_DISTANCE);
                    mc.options.simulationDistance().set(SIMULATION_DISTANCE);
                    require(mc.options.renderDistance().get() == VIEW_DISTANCE
                            && mc.options.simulationDistance().get() == SIMULATION_DISTANCE,
                            "Client rejected fixture view/simulation distances");
                    mc.options.pauseOnLostFocus = false;
                    mc.gui.setScreen(null);
                    phase = Phase.PREPARING;
                    server.execute(SubLevelHostBlockSyncSmoke::prepare);
                }
                case WAIT_STONE -> {
                    if (age < 40 || !mc.level.getBlockState(capturePosition).is(Blocks.STONE)
                            || !mc.level.getBlockState(controlPosition).is(Blocks.STONE)) return;
                    AcademyCraft.LOGGER.info("SUBLEVEL_HOST_SYNC_BASELINE client_capture=stone client_control=stone"
                            + " capture={} control={}", capturePosition, controlPosition);
                    phase = Phase.REQUEST_FULL;
                }
                case WAIT_RESULTS -> {
                    if (age < 40) return;
                    require(mc.level.getBlockState(capturePosition).isAir(),
                            "Real capture did not publish source air: " + mc.level.getBlockState(capturePosition));
                    require(mc.level.getBlockState(controlPosition).is(Blocks.STONE),
                            "Control did not reproduce the non-ticking update gate: "
                                    + mc.level.getBlockState(controlPosition));
                    AcademyCraft.LOGGER.info("SUBLEVEL_HOST_SYNC_GATE_CONFIRMED"
                            + " capture_client=air control_server=air control_client=stone");
                    phase = Phase.REQUEST_CONTROL_SYNC;
                }
                case WAIT_CONTROL_SYNC -> {
                    if (!mc.level.getBlockState(controlPosition).isAir()) return;
                    require(mc.level.getBlockState(capturePosition).isAir(), "Captured source reverted");
                    finish(mc, null);
                }
                default -> {
                }
            }
        } catch (Throwable problem) {
            finish(mc, problem);
        }
    }

    private static void prepare() {
        try {
            var actor = server.getPlayerList().getPlayer(playerId);
            require(actor != null, "Missing test player");
            host = actor.level();
            server.getPlayerList().setViewDistance(VIEW_DISTANCE);
            server.getPlayerList().setSimulationDistance(SIMULATION_DISTANCE);
            var playerChunk = actor.chunkPosition();
            // BLOCK_TICKING extends one chunk beyond the entity simulation radius.
            targetChunk = new ChunkPos(playerChunk.x() + TARGET_CHUNK_DISTANCE, playerChunk.z());
            AcademyCraft.LOGGER.info("SUBLEVEL_HOST_SYNC_DISTANCES view={} simulation={} target_distance={}"
                            + " player_chunk={} target_chunk={}",
                    VIEW_DISTANCE, SIMULATION_DISTANCE, TARGET_CHUNK_DISTANCE, playerChunk, targetChunk);
            int y = Math.min(host.getMaxY() - 16, Math.max(200, actor.getBlockY() + 32));
            capturePosition = new BlockPos(targetChunk.x() * 16 + 6, y, targetChunk.z() * 16 + 8);
            controlPosition = capturePosition.offset(3, 0, 0);
            host.getChunk(targetChunk.x(), targetChunk.z());
            // Above the terrain, away from water, neighbors, gravity blocks and player prediction.
            for (var pos : BlockPos.betweenClosed(capturePosition.offset(-1, -1, -1),
                    controlPosition.offset(1, 1, 1))) {
                require(host.setBlock(pos, Blocks.AIR.defaultBlockState(), FLAGS)
                        || host.getBlockState(pos).isAir(), "Could not clear isolated sync fixture");
            }
            host.setBlock(capturePosition, Blocks.STONE.defaultBlockState(), FLAGS);
            host.setBlock(controlPosition, Blocks.STONE.defaultBlockState(), FLAGS);
            StructureHostBlockUpdates.publish(host, List.of(capturePosition, controlPosition));
            phase = Phase.WAIT_STONE;
        } catch (Throwable problem) {
            failServer(problem);
        }
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void server(ServerTickEvent.Post event) {
        if (!ENABLED || event.getServer() != server || host == null || phase == Phase.FINISHED) return;
        try {
            var actor = server.getPlayerList().getPlayer(playerId);
            require(actor != null && actor.level() == host, "Test player changed level");
            if (phase == Phase.WAIT_STONE && event.getServer().getTickCount() % 20 == 0) {
                StructureHostBlockUpdates.publish(host, List.of(capturePosition, controlPosition));
            }
            if (phase == Phase.REQUEST_FULL) {
                ownsFullTicket = NativeStructureTickets.acquireCollision(host, targetChunk);
                require(ownsFullTicket, "Fixture target already owns a collision ticket");
                phase = Phase.WAIT_FULL;
            }
            if (phase == Phase.WAIT_FULL || phase == Phase.WAIT_CAPTURE
                    || phase == Phase.WAIT_RESULTS || phase == Phase.REQUEST_CONTROL_SYNC
                    || phase == Phase.WAIT_CONTROL_SYNC) {
                removeFixturePlayerTickets();
            }
            if (phase == Phase.WAIT_FULL) {
                var chunk = host.getChunkSource().getChunkNow(targetChunk.x(), targetChunk.z());
                var holder = host.getChunkSource().chunkMap.getVisibleChunkIfPresent(targetChunk.pack());
                if (chunk == null || holder == null || chunk.getFullStatus() != FullChunkStatus.FULL
                        || holder.getTickingChunk() != null) return;
                require(host.getChunkSource().chunkMap.isChunkTracked(actor, targetChunk.x(), targetChunk.z()),
                        "Target is no longer tracked");
                require(!host.getChunkSource().chunkMap.getDistanceManager().inBlockTickingRange(targetChunk.pack()),
                        "Target is still in player simulation range");
                require(host.getBlockState(capturePosition).is(Blocks.STONE)
                        && host.getBlockState(controlPosition).is(Blocks.STONE), "Fixture changed");
                AcademyCraft.LOGGER.info("SUBLEVEL_HOST_SYNC_FULL full={} ticking_chunk=false tracked=true"
                                + " simulation=false loading_level={} simulation_level={}"
                                + " removed_player_tickets={} capture={} control={}",
                        chunk.getFullStatus(),
                        host.getChunkSource().chunkMap.getDistanceManager().getChunkLevel(targetChunk.pack(), false),
                        host.getChunkSource().chunkMap.getDistanceManager().getChunkLevel(targetChunk.pack(), true),
                        REMOVED_TICKETS.values().stream().mapToInt(List::size).sum(),
                        capturePosition, controlPosition);
                // Unchanged vanilla flags intentionally omit the explicit helper for this control.
                require(host.setBlock(controlPosition, Blocks.AIR.defaultBlockState(), FLAGS),
                        "Could not clear control");
                capture = StructureTransactions.capture(host, new StructureCaptureRequest(
                        List.of(capturePosition), 1, 1, StructureCaptureRequest.ALLOW, true));
                phase = Phase.WAIT_CAPTURE;
            }
            if (phase == Phase.WAIT_CAPTURE) {
                if (capture.pending()) capture = StructureTransactions.poll(server, capture.transactionId()).orElse(capture);
                if (capture.pending()) return;
                require(capture.succeeded(), "Real capture rejected: " + capture);
                created = capture.structure().orElseThrow();
                require(SubLevelStructureRefs.isSubLevel(created), "Expected local SubLevel backend");
                require(host.getBlockState(capturePosition).isAir()
                        && host.getBlockState(controlPosition).isAir(), "Authoritative source changed");
                phase = Phase.WAIT_RESULTS;
            }
            if (phase == Phase.WAIT_RESULTS || phase == Phase.REQUEST_CONTROL_SYNC) {
                var chunk = host.getChunkSource().getChunkNow(targetChunk.x(), targetChunk.z());
                require(chunk != null && chunk.getFullStatus() == FullChunkStatus.FULL,
                        "Target was promoted during gate observation");
                require(host.getBlockState(capturePosition).isAir()
                        && host.getBlockState(controlPosition).isAir(), "Water or another block contaminated the fixture");
            }
            if (phase == Phase.REQUEST_CONTROL_SYNC) {
                StructureHostBlockUpdates.publish(host, List.of(controlPosition));
                phase = Phase.WAIT_CONTROL_SYNC;
            }
        } catch (Throwable problem) {
            failServer(problem);
        }
    }

    private static void removeFixturePlayerTickets() {
        var storage = host.getDataStorage().computeIfAbsent(TicketStorage.TYPE);
        // PLAYER_LOADING is level 31 even outside simulation distance. Remove its radius-one
        // propagation sources, leaving distance-two sources at FULL level 33 and a FULL fixture ticket.
        for (int dx = -1; dx <= 1; dx++) for (int dz = -1; dz <= 1; dz++) {
            long pos = ChunkPos.pack(targetChunk.x() + dx, targetChunk.z() + dz);
            for (var ticket : List.copyOf(storage.getTickets(pos))) {
                if (ticket.getType() == TicketType.PLAYER_LOADING && storage.removeTicket(pos, ticket)) {
                    REMOVED_TICKETS.computeIfAbsent(pos, ignored -> new ArrayList<>()).add(ticket);
                }
            }
        }
    }

    private static void failServer(Throwable problem) {
        failure = problem.toString();
        AcademyCraft.LOGGER.error("Host block sync server failure", problem);
    }

    private static void finish(Minecraft mc, Throwable problem) {
        if (phase == Phase.FINISHED) return;
        phase = Phase.FINISHED;
        if (server == null) {
            AcademyCraft.LOGGER.error("SUBLEVEL_HOST_SYNC_FAILED", problem);
            mc.stop();
            return;
        }
        server.execute(() -> {
            Throwable result = problem;
            try {
                cleanup();
            } catch (Throwable cleanupFailure) {
                if (result == null) result = cleanupFailure;
                else result.addSuppressed(cleanupFailure);
            }
            if (result == null) {
                AcademyCraft.LOGGER.info("SUBLEVEL_HOST_SYNC_PASSED real_client=true full_non_ticking=true"
                        + " initial_stone=true vanilla_control_stale=true capture_source_air=true"
                        + " manual_control_publish_air=true restored_tickets=true");
            } else {
                AcademyCraft.LOGGER.error("SUBLEVEL_HOST_SYNC_FAILED", result);
            }
            mc.execute(mc::stop);
        });
    }

    @SubscribeEvent
    public static void stopping(ServerStoppingEvent event) {
        if (ENABLED && event.getServer() == server) cleanup();
    }

    private static void cleanup() {
        if (cleaned || host == null) return;
        try {
            if (created != null) SubLevelStructureRefs.id(created).ifPresent(id ->
                    SubLevelServer.runtime(host).ifPresent(runtime -> runtime.remove(id)));
        } finally {
            var storage = host.getDataStorage().computeIfAbsent(TicketStorage.TYPE);
            for (var entry : REMOVED_TICKETS.entrySet()) {
                for (var ticket : entry.getValue()) storage.addTicket(entry.getKey(), ticket);
            }
            REMOVED_TICKETS.clear();
            if (ownsFullTicket) {
                NativeStructureTickets.releaseCollision(host, targetChunk);
                ownsFullTicket = false;
            }
            cleaned = true;
        }
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new IllegalStateException(message);
    }

    private enum Phase {
        START, PREPARING, WAIT_STONE, REQUEST_FULL, WAIT_FULL, WAIT_CAPTURE,
        WAIT_RESULTS, REQUEST_CONTROL_SYNC, WAIT_CONTROL_SYNC, FINISHED
    }
}
