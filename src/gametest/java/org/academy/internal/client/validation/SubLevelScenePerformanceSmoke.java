package org.academy.internal.client.validation;

import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RenderFrameEvent;
import org.academy.AcademyCraft;
import org.academy.internal.client.renderer.entity.NativeStructureProjectionRenderer;
import org.academy.internal.common.world.entity.structure.NativeStructureProjectionEntity;

import java.util.ArrayList;
import java.util.TreeMap;

/** Read-only view of a caller-prepared test2 copy, at the saved player position and orientation. */
@EventBusSubscriber(modid = AcademyCraft.MOD_ID, value = Dist.CLIENT)
public final class SubLevelScenePerformanceSmoke {
    private static final boolean ENABLED = Boolean.getBoolean("academy.sublevelSceneSmoke");
    private static final ArrayList<Double> FRAMES = new ArrayList<>();
    private static long ready, previous, start;
    private static boolean initialized, finished, paused;
    private static int screenshotFrames;

    private SubLevelScenePerformanceSmoke() {}

    @SubscribeEvent
    public static void tick(ClientTickEvent.Post event) {
        if (!ENABLED || finished) return;
        var mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) return;
        if (!initialized) {
            if (!mc.gameDirectory.toPath().toAbsolutePath().normalize().getFileName().toString()
                    .startsWith("native-sublevel-scene-smoke-")) {
                throw new IllegalStateException("Scene probe requires a disposable scene-smoke directory");
            }
            initialized = true;
            ready = System.nanoTime();
            mc.options.pauseOnLostFocus = false;
            mc.gui.setScreen(null);
        }
        if (!paused && System.nanoTime() - ready >= 3_000_000_000L) {
            // Reproduce the user's paused F3 scene; no entities, terrain or skills are changed.
            mc.pauseGame(false);
            paused = true;
            ready = System.nanoTime();
        }
    }

    @SubscribeEvent
    public static void frame(RenderFrameEvent.Pre event) {
        if (!ENABLED || finished || !paused) return;
        long now = System.nanoTime();
        if (now - ready < 3_000_000_000L) return;
        if (start == 0) {
            ProjectionMeshClientChecks.run(Minecraft.getInstance().level);
            start = previous = System.nanoTime();
            return;
        }
        FRAMES.add((now - previous) / 1_000_000.0);
        previous = now;
        if (now - start < 15_000_000_000L) return;
        var mc = Minecraft.getInstance();
        int structures = 0, cells = 0, batches = 0, fallback = 0, quads = 0;
        var materials = new TreeMap<String, Integer>();
        for (var entity : mc.level.entitiesForRendering()) {
            if (!(entity instanceof NativeStructureProjectionEntity projection)) continue;
            structures++;
            batches += NativeStructureProjectionRenderer.lastSubmittedBatchCount(projection.getId());
            fallback += NativeStructureProjectionRenderer.lastSubmittedUnbatchedCells(projection.getId());
            quads += NativeStructureProjectionRenderer.lastSubmittedBatchedQuads(projection.getId());
            for (var cell : projection.view().cells()) {
                cells++;
                materials.merge(cell.state().getBlock().toString(), 1, Integer::sum);
            }
        }
        var sorted = new ArrayList<>(FRAMES);
        sorted.sort(Double::compare);
        AcademyCraft.LOGGER.info("SUBLEVEL_SCENE_BENCHMARK structures={} cells={} samples={} fps={} "
                        + "frame_ms_p50={} frame_ms_p95={} position={} yaw={} pitch={} materials={}",
                structures, cells, FRAMES.size(), FRAMES.size() * 1_000_000_000.0 / (now - start),
                sorted.get(sorted.size() / 2), sorted.get((int) Math.ceil(sorted.size() * .95) - 1),
                mc.player.position(), mc.player.getYRot(), mc.player.getXRot(), materials);
        AcademyCraft.LOGGER.info("SUBLEVEL_SCENE_BATCHES submissions={} fallback_cells={} cached_quads={}",
                batches, fallback, quads);
        finished = true;
        // Capture an unobscured view only after the timed paused-scene measurement.
        mc.gui.setScreen(null);
        screenshotFrames = 2;
    }

    @SubscribeEvent
    public static void screenshot(RenderFrameEvent.Post event) {
        if (!ENABLED || screenshotFrames == 0 || --screenshotFrames != 0) return;
        var mc = Minecraft.getInstance();
        Screenshot.grab(mc.gameDirectory, "scene.png", mc.gameRenderer.mainRenderTarget(), 1,
                message -> mc.stop());
    }
}
