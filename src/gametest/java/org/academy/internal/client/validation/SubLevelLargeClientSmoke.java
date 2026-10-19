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

/** Opt-in 24x24x24 hollow-shell client render/FPS probe. */
@EventBusSubscriber(modid = AcademyCraft.MOD_ID, value = Dist.CLIENT)
public final class SubLevelLargeClientSmoke {
    private static final boolean ENABLED = Boolean.getBoolean("academy.sublevelLargeSmoke");
    private static final boolean SOLID = Boolean.getBoolean("academy.sublevelLargeSolid");
    private static final int SIDE = 24;
    private static volatile boolean requested;
    private static volatile boolean captured;
    private static volatile SubLevelCaptureService.Result result;
    private static volatile String failure;
    private static int ticks;
    private static int warmup;
    private static int measured;
    private static long fpsSum;
    private static int fpsMin = Integer.MAX_VALUE;

    private SubLevelLargeClientSmoke() {
    }

    @SubscribeEvent
    public static void clientTick(ClientTickEvent.Post event) {
        if (!ENABLED || failure != null) return;
        var minecraft = Minecraft.getInstance();
        if (minecraft.level == null || minecraft.player == null) return;
        if (++ticks > 2400) { fail("timeout"); return; }
        if (!requested) {
            var server = minecraft.getSingleplayerServer();
            if (server == null) return;
            requested = true;
            server.execute(() -> capture(server));
            return;
        }
        if (!captured) return;
        NativeStructureProjectionEntity projection = null;
        for (var entity : minecraft.level.entitiesForRendering()) {
            if (entity instanceof NativeStructureProjectionEntity candidate && candidate.isClientOnly()
                    && candidate.view().cells().size() == result.blocks()) {
                projection = candidate;
                break;
            }
        }
        if (projection == null) return;
        if (!(minecraft.gui.screen() instanceof net.minecraft.client.gui.screens.inventory.AbstractContainerScreen<?>)) {
            var target = projection.renderPose(1.0F).localToWorldPoint(
                    new net.minecraft.world.phys.Vec3(SIDE / 2.0, SIDE / 2.0, SIDE / 2.0));
            var eye = minecraft.player.getEyePosition();
            var delta = target.subtract(eye);
            var horizontal = Math.sqrt(delta.x * delta.x + delta.z * delta.z);
            minecraft.player.setYRot((float) Math.toDegrees(Math.atan2(-delta.x, delta.z)));
            minecraft.player.setXRot((float) Math.toDegrees(-Math.atan2(delta.y, horizontal)));
        }
        if (warmup++ < 40) return;
        int fps = minecraft.getFps();
        fpsSum += fps;
        fpsMin = Math.min(fpsMin, fps);
        if (++measured < 120) return;
        AcademyCraft.LOGGER.info("SUBLEVEL_LARGE_BENCHMARK side={} cells={} fps_avg={} fps_min={} ticks={}",
                SIDE, result.blocks(), fpsSum / (double) measured, fpsMin, measured);
        minecraft.stop();
    }

    private static void capture(MinecraftServer server) {
        try {
            var runtime = SubLevelServer.runtime(server).orElseThrow(() -> new IllegalStateException("SubLevel backend unavailable"));
            var player = server.getPlayerList().getPlayers().getFirst();
            var level = player.level();
            var origin = player.blockPosition().above(3);
            var positions = new ArrayList<BlockPos>(4_000);
            for (int x = 0; x < SIDE; x++) {
                for (int y = 0; y < SIDE; y++) {
                    for (int z = 0; z < SIDE; z++) {
                        if (!SOLID && x != 0 && x != SIDE - 1 && y != 0 && y != SIDE - 1
                                && z != 0 && z != SIDE - 1) continue;
                        var position = origin.offset(x, y, z);
                        level.setBlock(position, Blocks.GLOWSTONE.defaultBlockState(), 3);
                        positions.add(position);
                    }
                }
            }
            result = SubLevelCaptureService.capture(level, runtime, positions);
            ticks = 0;
            captured = true;
            AcademyCraft.LOGGER.info("SUBLEVEL_LARGE_CAPTURED side={} solid={} blocks={} boxes={}",
                    SIDE, SOLID, result.blocks(), result.boxes());
        } catch (Throwable error) {
            AcademyCraft.LOGGER.error("SUBLEVEL_LARGE_CAPTURE_FAILED", error);
            fail(error.toString());
        }
    }

    private static void fail(String message) {
        failure = message;
        AcademyCraft.LOGGER.error("SUBLEVEL_LARGE_BENCHMARK_FAILED {}", message, new IllegalStateException(message));
        Minecraft.getInstance().stop();
    }
}
