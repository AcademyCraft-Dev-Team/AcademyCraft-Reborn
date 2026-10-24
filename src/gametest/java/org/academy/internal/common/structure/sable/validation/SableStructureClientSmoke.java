package org.academy.internal.common.structure.sable.validation;

import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.common.NeoForge;
import org.academy.AcademyCraft;

import java.nio.file.Files;
import java.nio.file.StandardOpenOption;

/** Observes actual Sable client data and captures the rendered window; never synthesizes input. */
public final class SableStructureClientSmoke {
    private static boolean initialized, pending, captured, finished;
    private static int ticks, visibleTicks, motionSamples;
    private static Vec3 previous;
    private static volatile String failure;

    private SableStructureClientSmoke() {}

    public static synchronized void initialize() {
        if (initialized) return;
        require(Boolean.getBoolean("academy.sable.smoke") && "client".equals(System.getProperty("academy.sable.smoke.side")),
                "Client Sable probe requires explicit opt-in");
        NeoForge.EVENT_BUS.register(SableStructureClientSmoke.class);
        initialized = true;
    }

    @SubscribeEvent public static void tick(ClientTickEvent.Post event) {
        if (finished) return;
        var mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) return;
        require(++ticks < 1000, "Client Sable probe exceeded its finite deadline");
        require(failure == null, "Sable screenshot failed: " + failure);
        var state = SableStructureSmoke.clientState();
        require(state.failure() == null, "Server Sable smoke failed: " + state.failure());
        if (state.complete()) {
            require(captured && motionSamples >= 3, "Missing Sable client evidence");
            try {
                Files.writeString(mc.gameDirectory.toPath().resolve("sable-structure-client-observation.txt"),
                        "PASSED\nreal_sable_plot_received=true\nmoving_pose_samples=" + motionSamples
                                + "\nrender_screenshot_saved=true\nactual_keyboard_actions=false\n",
                        StandardOpenOption.CREATE_NEW);
            } catch (java.io.IOException error) { throw new IllegalStateException("Cannot save Sable client evidence", error); }
            AcademyCraft.LOGGER.info("SABLE_STRUCTURE_CLIENT_PASSED moving_samples={} actual_keyboard_actions=false", motionSamples);
            finished = true; mc.stop(); return;
        }
        var container = SubLevelContainer.getContainer(mc.level);
        var body = container == null || state.structure() == null ? null : container.getSubLevel(state.structure());
        if (body == null || body.isRemoved()) return;
        var cell = body.getPlot().getCenterBlock();
        var chunk = body.getPlot().getChunkHolder(body.getPlot().toLocal(new net.minecraft.world.level.ChunkPos(cell.getX() >> 4, cell.getZ() >> 4)));
        if (chunk == null || chunk.getChunk() == null || !chunk.getChunk().getBlockState(cell).is(Blocks.STONE)) return;
        var point = body.logicalPose().transformPosition(Vec3.atCenterOf(cell));
        if (previous != null && point.distanceTo(previous) > 1.0E-5) motionSamples++;
        previous = point;
        if (++visibleTicks < 25 || motionSamples < 3 || pending || captured || !state.phase().equals("SKILL")) return;
        pending = true;
        var name = "academy-sable-structure-" + ProcessHandle.current().pid() + ".png";
        var path = mc.gameDirectory.toPath().resolve("screenshots").resolve(name).toAbsolutePath();
        Screenshot.grab(mc.gameDirectory, name, mc.gameRenderer.mainRenderTarget(), 1, message -> {
            try {
                require(Files.isRegularFile(path) && Files.size(path) > 0, "Screenshot missing: " + message.getString());
                mc.execute(() -> {
                    captured = true; pending = false;
                    SableStructureSmoke.captured(path, motionSamples);
                    AcademyCraft.LOGGER.info("SABLE_STRUCTURE_CLIENT_SCREENSHOT path={} moving_samples={}", path, motionSamples);
                });
            } catch (Throwable error) { failure = error.toString(); }
        });
    }

    private static void require(boolean value, String message) { if (!value) throw new IllegalStateException(message); }
}
