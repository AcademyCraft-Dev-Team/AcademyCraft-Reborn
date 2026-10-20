package org.academy.internal.client.validation;

import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RenderFrameEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import org.academy.AcademyCraft;
import org.academy.internal.common.sublevel.SubLevelCaptureService;
import org.academy.internal.common.sublevel.SubLevelServer;
import org.academy.internal.common.structure.physics.PhysicsVector;
import org.academy.internal.common.world.entity.structure.NativeStructureProjectionEntity;

import java.util.ArrayList;
import java.util.List;

/** Opt-in 64-active client render/FPS probe. */
@EventBusSubscriber(modid = AcademyCraft.MOD_ID, value = Dist.CLIENT)
public final class SubLevelMultiClientSmoke {
    private static final boolean ENABLED = Boolean.getBoolean("academy.sublevelMultiSmoke");
    private static final int STRUCTURES = Integer.getInteger("academy.sublevelMultiSmokeCount", 64);
    private static final boolean LARGE = Boolean.getBoolean("academy.sublevelMultiSmoke128");
    private static final boolean MOVING = Boolean.getBoolean("academy.sublevelMultiSmokeMoving");
    private static final int SAMPLE_TICKS = Integer.getInteger("academy.sublevelMultiSmokeTicks", 240);
    private static final int GRID = (int) Math.ceil(Math.sqrt(STRUCTURES));
    private static volatile boolean captureRequested;
    private static volatile boolean captureDone;
    private static volatile String failure;
    private static volatile List<SubLevelCaptureService.Result> results = List.of();
    private static int ticks;
    private static int warmup;
    private static int measured;
    private static long fpsSum;
    private static int fpsMin = Integer.MAX_VALUE;
    private static volatile boolean sampling;
    private static volatile MinecraftServer owner;
    private static final List<Double> frameMillis = new ArrayList<>();
    private static final List<Double> tickMillis = new ArrayList<>();
    private static long lastFrame, tickStart;
    private static int motionTick;

    private SubLevelMultiClientSmoke() {
    }

    @SubscribeEvent
    public static void clientTick(ClientTickEvent.Post event) {
        if (!ENABLED || failure != null) return;
        var minecraft = Minecraft.getInstance();
        if (minecraft.level == null || minecraft.player == null) return;
        minecraft.options.pauseOnLostFocus = false;
        if (minecraft.gui.screen() != null) minecraft.gui.setScreen(null);
        if (++ticks > 3600) {
            fail("timeout");
            return;
        }
        if (!captureRequested) {
            var server = minecraft.getSingleplayerServer();
            if (server == null) return;
            captureRequested = true;
            server.execute(() -> capture(server));
            return;
        }
        if (!captureDone) return;
        int count = 0;
        for (var entity : minecraft.level.entitiesForRendering()) {
            if (entity instanceof NativeStructureProjectionEntity projection && projection.isClientOnly()) count++;
        }
        if (count < STRUCTURES) return;
        if (warmup++ < 80) return;
        sampling = true;
        int fps = minecraft.getFps();
        fpsSum += fps;
        fpsMin = Math.min(fpsMin, fps);
        if (++measured < SAMPLE_TICKS) return;
        sampling = false;
        synchronized (tickMillis) {
            AcademyCraft.LOGGER.info("SUBLEVEL_MULTI_BENCHMARK structures={} cells_each={} moving={} "
                            + "fps_avg={} fps_min={} ticks={} frames={} frame_ms_p50={} frame_ms_p95={} "
                            + "server_samples={} server_ms_p50={} server_ms_p95={}",
                    STRUCTURES, LARGE ? 128 : 8, MOVING, fpsSum / (double) measured, fpsMin, measured,
                    frameMillis.size(), percentile(frameMillis, .5), percentile(frameMillis, .95),
                    tickMillis.size(), percentile(tickMillis, .5), percentile(tickMillis, .95));
        }
        minecraft.stop();
    }

    @SubscribeEvent
    public static void frame(RenderFrameEvent.Pre event) {
        if (!ENABLED) return;
        long now = System.nanoTime();
        if (sampling && lastFrame != 0) frameMillis.add((now - lastFrame) / 1_000_000.0);
        lastFrame = now;
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void beforeTick(ServerTickEvent.Pre event) {
        if (!ENABLED || !captureDone || event.getServer() != owner) return;
        if (MOVING) {
            var runtime = SubLevelServer.runtime(owner).orElseThrow();
            double horizontal = (motionTick++ / 40 % 2 == 0) ? 0.6 : -0.6;
            for (var result : results) runtime.universe().setVelocity(result.id(),
                    new PhysicsVector(horizontal, 0.5, 0), PhysicsVector.ZERO);
        }
        tickStart = System.nanoTime();
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void afterTick(ServerTickEvent.Post event) {
        if (!ENABLED || !sampling || event.getServer() != owner || tickStart == 0) return;
        synchronized (tickMillis) { tickMillis.add((System.nanoTime() - tickStart) / 1_000_000.0); }
    }

    private static double percentile(List<Double> samples, double percentile) {
        if (samples.isEmpty()) return Double.NaN;
        var sorted = new ArrayList<>(samples);
        sorted.sort(Double::compareTo);
        return sorted.get((int) Math.ceil(sorted.size() * percentile) - 1);
    }

    private static void capture(MinecraftServer server) {
        try {
            var runtime = SubLevelServer.runtime(server).orElseThrow(() -> new IllegalStateException("SubLevel backend unavailable"));
            var player = server.getPlayerList().getPlayers().getFirst();
            owner = server;
            var level = player.level();
            var base = player.blockPosition().above(8);
            var captured = new ArrayList<SubLevelCaptureService.Result>(STRUCTURES);
            for (int gridX = 0; gridX < GRID; gridX++) {
                for (int gridZ = 0; gridZ < GRID && captured.size() < STRUCTURES; gridZ++) {
                    var origin = base.offset(gridX * (LARGE ? 12 : 3), 0, gridZ * (LARGE ? 12 : 3));
                    level.getChunkAt(origin);
                    level.getChunkAt(origin.offset(7, 0, 7));
                    var positions = new ArrayList<BlockPos>(LARGE ? 128 : 8);
                    for (int x = 0; x < (LARGE ? 8 : 2); x++) {
                        for (int y = 0; y < 2; y++) {
                            for (int z = 0; z < (LARGE ? 8 : 2); z++) {
                                var position = origin.offset(x, y, z);
                                level.setBlock(position, Blocks.GLOWSTONE.defaultBlockState(), 3);
                                positions.add(position);
                            }
                        }
                    }
                    captured.add(SubLevelCaptureService.capture(level, runtime, positions));
                }
            }
            results = List.copyOf(captured);
            if (LARGE) {
                var eye = Vec3.atLowerCornerOf(base).add(GRID * 6, 16, -32);
                player.setGameMode(net.minecraft.world.level.GameType.SPECTATOR);
                player.connection.teleport(eye.x, eye.y, eye.z, 0, 20);
            }
            captureDone = true;
            AcademyCraft.LOGGER.info("SUBLEVEL_MULTI_CAPTURED structures={}", captured.size());
        } catch (Throwable error) {
            fail(error.toString());
        }
    }

    private static void fail(String message) {
        failure = message;
        AcademyCraft.LOGGER.error("SUBLEVEL_MULTI_BENCHMARK_FAILED {}", message);
        Minecraft.getInstance().stop();
    }
}
