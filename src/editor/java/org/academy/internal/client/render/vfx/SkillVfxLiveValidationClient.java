package org.academy.internal.client.render.vfx;

import com.mojang.blaze3d.systems.TimerQuery;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.InactivityFpsLimit;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RenderFrameEvent;
import org.academy.AcademyCraft;
import org.academy.api.client.render.vfxgraph.runtime.VfxGraphManager;

/** Records actual game frames and GPU timestamp queries; never active in ordinary runs. */
@EventBusSubscriber(modid = AcademyCraft.MOD_ID, value = Dist.CLIENT)
public final class SkillVfxLiveValidationClient {
    private static final String ROLE = System.getProperty("academy.vfxValidation.role", "");
    private static final List<String> SAMPLES = new ArrayList<>();
    private static String phase = "loading", sampledPhase = "loading";
    private static int ticks, frames, screenshots;
    private static long began, phaseBegan;
    private static TimerQuery gpu;
    private static boolean profiling, ready, finished;
    private SkillVfxLiveValidationClient() {}
    private static Path output() { return Path.of(System.getProperty("academy.vfxValidation.output")); }
    private static void write(String name, String body) {
        try { Files.createDirectories(output()); Files.writeString(output().resolve(name), body); }
        catch (IOException e) { throw new IllegalStateException("Cannot save VFX validation", e); }
    }

    @SubscribeEvent
    public static void screen(net.neoforged.neoforge.client.event.ScreenEvent.Init.Post event) {
        if (!ROLE.isEmpty()) AcademyCraft.getLogger().info("VFX_LIVE_SCREEN {} {}", ROLE, event.getScreen().getClass().getName());
    }
    @SubscribeEvent
    public static void tick(ClientTickEvent.Post event) {
        if (ROLE.isEmpty()) return;
        var mc = Minecraft.getInstance();
        if (finished) { if (mc.level == null) mc.stop(); return; }
        if (mc.level == null || mc.player == null) return;
        if (!ready && frames > 120) {
            mc.options.renderDistance().set(6);
            mc.options.simulationDistance().set(5);
            mc.options.enableVsync().set(false);
            mc.options.framerateLimit().set(120);
            mc.options.inactivityFpsLimit().set(InactivityFpsLimit.MINIMIZED);
            mc.options.pauseOnLostFocus = false;
            write(ROLE + ".ready", mc.player.getGameProfile().name());
            ready = true;
        }
        if (++ticks % 5 != 0) return;
        try {
            if (Files.exists(output().resolve("phase.txt"))) {
                var current = Files.readString(output().resolve("phase.txt")).trim();
                // The writer may be between truncate and write during this diagnostic poll.
                if (!current.isEmpty()) phase = current;
            }
        } catch (IOException e) { AcademyCraft.getLogger().warn("Unable to read validation phase", e); }
    }

    @SubscribeEvent
    public static void before(RenderFrameEvent.Pre event) {
        if (ROLE.isEmpty() || finished || Minecraft.getInstance().level == null) return;
        frames++;
        began = System.nanoTime();
        if (gpu == null) gpu = new TimerQuery();
        profiling = gpu.getStatus() == TimerQuery.Status.NOT_RECORDING;
        if (profiling) gpu.beginProfile();
    }

    @SubscribeEvent
    public static void after(RenderFrameEvent.Post event) {
        if (ROLE.isEmpty() || finished || began == 0 || Minecraft.getInstance().level == null) return;
        long now = System.nanoTime();
        if (profiling) { gpu.endProfile(); profiling = false; }
        if (!phase.equals(sampledPhase)) {
            sampledPhase = phase; phaseBegan = now; screenshots = 0;
            AcademyCraft.getLogger().info("VFX_LIVE_CLIENT {} phase={}", ROLE, phase);
        }
        var stats = VfxGraphManager.INSTANCE.frameStatistics();
        SAMPLES.add(sampledPhase + "," + (now - phaseBegan) / 1e9 + "," + (now - began) / 1e6
                + "," + gpu.get() / 1e6 + "," + stats.active() + "," + stats.visible() + "," + stats.culled()
                + "," + stats.simulated() + "," + SkillVfxClient.receivedPackets());
        double elapsed = (now - phaseBegan) / 1e9;
        double nextScreenshot = switch (screenshots) { case 0 -> 1.13; case 1 -> 3.17; case 2 -> 6.19; default -> 11.13; };
        if (!sampledPhase.equals("loading") && !sampledPhase.equals("done") && screenshots < 4 && elapsed >= nextScreenshot) {
            screenshots++;
            var mc = Minecraft.getInstance();
            Screenshot.grab(output().toFile(), ROLE + "-" + sampledPhase + "-" + screenshots + ".png",
                    mc.gameRenderer.mainRenderTarget(), 1, message -> AcademyCraft.getLogger().info("VFX_CAPTURE {}", message.getString()));
        }
        if (phase.equals("done")) {
            finished = true;
            write(ROLE + "-frames.csv", "phase,phase_seconds,render_cpu_ms,gpu_rolling_ms,active,visible,culled,simulated,received_packets\n"
                    + String.join("\n", SAMPLES) + "\n");
            gpu.close();
            // Remain connected until the test server finishes its result flush and shuts down.
        }
        began = 0;
    }
}
