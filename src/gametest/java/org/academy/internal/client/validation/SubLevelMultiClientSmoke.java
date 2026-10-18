package org.academy.internal.client.validation;

import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import org.academy.AcademyCraft;
import org.academy.internal.common.sublevel.SubLevelCaptureService;
import org.academy.internal.common.sublevel.SubLevelServer;
import org.academy.internal.common.world.entity.structure.NativeStructureProjectionEntity;

import java.util.ArrayList;
import java.util.List;

/** Opt-in 64-active client render/FPS probe. */
@EventBusSubscriber(modid = AcademyCraft.MOD_ID, value = Dist.CLIENT)
public final class SubLevelMultiClientSmoke {
    private static final boolean ENABLED = Boolean.getBoolean("academy.sublevelMultiSmoke");
    private static final int STRUCTURES = Integer.getInteger("academy.sublevelMultiSmokeCount", 64);
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

    private SubLevelMultiClientSmoke() {
    }

    @SubscribeEvent
    public static void clientTick(ClientTickEvent.Post event) {
        if (!ENABLED || failure != null) return;
        var minecraft = Minecraft.getInstance();
        if (minecraft.level == null || minecraft.player == null) return;
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
        if (warmup++ < 40) return;
        int fps = minecraft.getFps();
        fpsSum += fps;
        fpsMin = Math.min(fpsMin, fps);
        if (++measured < 120) return;
        AcademyCraft.LOGGER.info("SUBLEVEL_MULTI_BENCHMARK structures={} fps_avg={} fps_min={} ticks={}",
                STRUCTURES, fpsSum / (double) measured, fpsMin, measured);
        minecraft.stop();
    }

    private static void capture(MinecraftServer server) {
        try {
            var runtime = SubLevelServer.runtime(server).orElseThrow(() -> new IllegalStateException("SubLevel backend unavailable"));
            var player = server.getPlayerList().getPlayers().getFirst();
            var level = player.level();
            var base = player.blockPosition().above(8);
            var captured = new ArrayList<SubLevelCaptureService.Result>(STRUCTURES);
            for (int gridX = 0; gridX < GRID; gridX++) {
                for (int gridZ = 0; gridZ < GRID && captured.size() < STRUCTURES; gridZ++) {
                    var origin = base.offset(gridX * 3, 0, gridZ * 3);
                    var positions = new ArrayList<BlockPos>(8);
                    for (int x = 0; x < 2; x++) {
                        for (int y = 0; y < 2; y++) {
                            for (int z = 0; z < 2; z++) {
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
