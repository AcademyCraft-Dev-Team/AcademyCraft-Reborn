package org.academy.internal.client.validation;

import com.google.gson.GsonBuilder;
import net.minecraft.client.Minecraft;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import org.academy.AcademyCraft;
import org.academy.api.common.structure.upgrade.StructureCaptureExtractions;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;

/** Focused entry for the unchanged direct-skill matrix, independent of content/plot probes. */
@EventBusSubscriber(modid = AcademyCraft.MOD_ID, value = Dist.CLIENT)
public final class SubLevelDefaultSkillsSmoke {
    private static final boolean ENABLED = Boolean.getBoolean("academy.sublevelDefaultSkillsSmoke");
    private static final int MAX_TICKS = 1500;
    private static volatile boolean ready, finished, failed, closing;
    private static Path output;
    private static int serverTicks, clientWaitTicks, lastServerTick = -1;

    private SubLevelDefaultSkillsSmoke() {}

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void serverTick(ServerTickEvent.Post event) {
        if (!ENABLED || !ready || finished || failed) return;
        var server = event.getServer();
        if (lastServerTick == server.getTickCount()) return;
        lastServerTick = server.getTickCount();
        try {
            if (++serverTicks > MAX_TICKS) throw new IllegalStateException("default direct-skill matrix timed out");
            if (server.getPlayerList().getPlayers().isEmpty()) return;
            SubLevelDirectSkillSmoke.tick(server);
            if (!SubLevelDirectSkillSmoke.complete()) return;
            writeResult(true, "passed", null);
            AcademyCraft.LOGGER.info("SUBLEVEL_DEFAULT_SKILLS_SMOKE_PASSED server_ticks={} direct_complete=true",
                    serverTicks);
            finished = true;
        } catch (Throwable failure) {
            fail(failure);
        }
    }

    @SubscribeEvent
    public static void clientTick(ClientTickEvent.Post event) {
        if (!ENABLED || closing) return;
        var client = Minecraft.getInstance();
        if (finished || failed) {
            closing = true;
            client.stop();
            return;
        }
        try {
            if (output == null) {
                var directory = client.gameDirectory.toPath().toRealPath();
                if (!directory.getFileName().toString().startsWith("native-sublevel-direct-smoke")
                        || directory.getParent() == null
                        || !directory.getParent().getFileName().toString().equals("run")) {
                    throw new IllegalStateException("default skills smoke requires an isolated run/native-sublevel-direct-smoke* directory");
                }
                output = directory.resolve("sublevel-default-skills-result.json");
                // A later abrupt exit must not expose a previous passed report as current evidence.
                writeResult(false, "running", null);
            }
            if (client.level == null || client.player == null || client.getConnection() == null) {
                if (++clientWaitTicks > MAX_TICKS) throw new IllegalStateException("default skills client readiness timed out");
                return;
            }
            client.player.setNoGravity(true);
            client.player.setDeltaMovement(Vec3.ZERO);
            client.player.resetFallDistance();
            ready = true;
        } catch (IOException | RuntimeException failure) {
            fail(failure);
        }
    }

    private static void fail(Throwable failure) {
        if (failed || finished) return;
        try {
            if (output != null) writeResult(false, "failed", failure);
        } catch (IOException | RuntimeException reportFailure) {
            failure.addSuppressed(reportFailure);
        }
        AcademyCraft.LOGGER.error("SUBLEVEL_DEFAULT_SKILLS_SMOKE_FAILED server_ticks={}", serverTicks, failure);
        failed = true;
    }

    private static void writeResult(boolean passed, String status, Throwable failure) throws IOException {
        var report = new LinkedHashMap<String, Object>();
        report.put("passed", passed);
        report.put("status", status);
        report.put("fixture", "SubLevelDirectSkillSmoke");
        report.put("direct_complete", SubLevelDirectSkillSmoke.complete());
        report.put("content_matrix_executed", false);
        report.put("extraction_gameplay_enabled", StructureCaptureExtractions.enabled());
        report.put("server_ticks", serverTicks);
        report.put("maximum_server_ticks", MAX_TICKS);
        report.put("failure", failure == null ? "" : failure.toString());
        report.put("failure_stack", failure == null ? List.of()
                : Arrays.stream(failure.getStackTrace()).map(StackTraceElement::toString).toList());
        Files.writeString(output, new GsonBuilder().setPrettyPrinting().create().toJson(report));
    }
}
