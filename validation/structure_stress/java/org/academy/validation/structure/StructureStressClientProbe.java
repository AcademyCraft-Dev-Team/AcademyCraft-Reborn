package org.academy.validation.structure;

import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RenderFrameEvent;
import net.neoforged.neoforge.common.NeoForge;
import org.academy.AcademyCraft;
import org.academy.api.common.structure.StructureCaptureRequest;
import org.academy.api.common.structure.StructureCaptureResult;
import org.academy.api.common.structure.StructureRef;
import org.academy.api.common.structure.StructureTransactions;

import java.io.IOException;
import java.lang.management.ManagementFactory;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Opt-in client stress probe. Measures world load time from JVM start to an operable
 * player, then samples frame times, client tick times and heap usage for the configured
 * window while the requested number of real structures are created in the integrated
 * server and stay present. Writes {@code stress-client-result.txt} into the game
 * directory and stops the client cleanly. Disabled unless {@code academy.structureStress=client}.
 */
@EventBusSubscriber(modid = AcademyCraft.MOD_ID, value = Dist.CLIENT)
public final class StructureStressClientProbe {
    private static final boolean ENABLED = "client".equals(System.getProperty("academy.structureStress"));
    private static final String RESULT = "stress-client-result.txt";

    private enum Phase { WAIT_LEVEL, SETUP, WARMUP, SAMPLE, FINISH }

    private static boolean registered;
    private static Phase phase = Phase.WAIT_LEVEL;
    private static int ticks, sampleTicks, requested, created, warmupTicks;
    private static long runtimeStartMillis, levelReadyMillis, operableMillis, sampleStartedAt, warmupStarted;
    private static long framePreAt, lastFramePreAt, tickPreAt;
    private static boolean complete, stopping, operableSeen;
    private static String failure;
    private static final List<Long> frameNanos = new ArrayList<>();
    private static final List<Long> renderNanos = new ArrayList<>();
    private static final List<Long> tickNanos = new ArrayList<>();
    private static final List<Long> memoryBytes = new ArrayList<>();
    private static final List<StructureRef> structures = new ArrayList<>();
    private static volatile String captureState = "IDLE";
    private static volatile StructureCaptureResult captureResult;
    private static volatile StructureRef captureRef;
    private static volatile String captureFailure;

    private StructureStressClientProbe() {}

    public static void initialize() {
        if (registered) return;
        NeoForge.EVENT_BUS.register(StructureStressClientProbe.class);
        registered = true;
    }

    @SubscribeEvent
    public static void preTick(ClientTickEvent.Pre event) {
        if (!ENABLED) return;
        tickPreAt = System.nanoTime();
    }

    @SubscribeEvent
    public static void postTick(ClientTickEvent.Post event) {
        if (!ENABLED || complete) return;
        try {
            if (tickPreAt != 0 && phase == Phase.SAMPLE) tickNanos.add(System.nanoTime() - tickPreAt);
            advance();
        } catch (Throwable error) {
            failure = error.toString();
            AcademyCraft.LOGGER.error("STRUCTURE_STRESS_CLIENT_FAILED phase={} tick={}", phase, ticks, error);
        }
    }

    @SubscribeEvent
    public static void framePre(RenderFrameEvent.Pre event) {
        if (!ENABLED) return;
        var now = System.nanoTime();
        if (phase == Phase.SAMPLE && lastFramePreAt != 0) frameNanos.add(now - lastFramePreAt);
        lastFramePreAt = now;
        framePreAt = now;
    }

    @SubscribeEvent
    public static void framePost(RenderFrameEvent.Post event) {
        if (!ENABLED || complete || phase != Phase.SAMPLE || framePreAt == 0) return;
        renderNanos.add(System.nanoTime() - framePreAt);
        framePreAt = 0;
    }

    private static void advance() {
        var mc = Minecraft.getInstance();
        ticks++;
        if (failure != null || ticks > 20 * 1_800) {
            if (!stopping) {
                stopping = true;
                writeResult("FAILED\nphase=" + phase + "\nticks=" + ticks + "\nfailure="
                        + (failure == null ? "deadline" : failure) + "\n" + summary());
                mc.stop();
            }
            return;
        }
        switch (phase) {
            case WAIT_LEVEL -> waitLevel(mc);
            case SETUP -> setup(mc);
            case WARMUP -> warmup(mc);
            case SAMPLE -> sample(mc);
            case FINISH -> finish(mc);
        }
    }

    private static void waitLevel(Minecraft mc) {
        runtimeStartMillis = runtimeStartMillis == 0
                ? ManagementFactory.getRuntimeMXBean().getStartTime() : runtimeStartMillis;
        requested = Integer.getInteger("academy.structureStressStructures", 1);
        if (mc.level == null || mc.player == null || mc.getSingleplayerServer() == null) return;
        levelReadyMillis = System.currentTimeMillis() - runtimeStartMillis;
        if (!operableSeen && mc.gui.screen() == null) {
            operableSeen = true;
            operableMillis = System.currentTimeMillis() - runtimeStartMillis;
            mc.options.pauseOnLostFocus = false;
            phase = Phase.SETUP;
        }
    }

    private static void setup(Minecraft mc) {
        var server = mc.getSingleplayerServer();
        if (server == null) throw new IllegalStateException("Integrated server disappeared during setup");
        if (captureState.equals("IDLE")) {
            captureState = "RUNNING";
            server.execute(() -> startCapture(server));
            return;
        }
        if (captureState.equals("RUNNING")) return;
        if (captureState.equals("FAILED")) {
            throw new IllegalStateException("Structure capture failed: " + captureFailure);
        }
        if (captureRef != null) {
            structures.add(captureRef);
            created++;
            captureRef = null;
            captureState = "IDLE";
        }
        if (created < requested) return;
        warmupStarted = System.nanoTime();
        warmupTicks = 0;
        phase = Phase.WARMUP;
    }

    private static void warmup(Minecraft mc) {
        warmupTicks++;
        if (warmupTicks < 200) return;
        sampleStartedAt = System.nanoTime();
        phase = Phase.SAMPLE;
        AcademyCraft.LOGGER.info("STRUCTURE_STRESS_CLIENT_SAMPLE_START structures={} load_ms={} operable_ms={}",
                created, levelReadyMillis, operableMillis);
    }

    private static void startCapture(MinecraftServer server) {
        try {
            var level = server.overworld();
            var player = firstPlayer(server);
            var origin = sourceOrigin(level, player);
            var cells = new ArrayList<BlockPos>(8);
            for (int x = 0; x < 2; x++) {
                for (int y = 0; y < 2; y++) {
                    for (int z = 0; z < 2; z++) {
                        var position = origin.offset(x, y, z);
                        if (!level.getBlockState(position).isAir()) {
                            throw new IllegalStateException("Stress source is occupied at " + position);
                        }
                        level.setBlock(position, Blocks.STONE.defaultBlockState(), Block.UPDATE_ALL);
                        cells.add(position.immutable());
                    }
                }
            }
            var result = StructureTransactions.capture(level, StructureCaptureRequest.of(cells));
            if (!result.pending() && !result.succeeded()) {
                throw new IllegalStateException("Capture did not start: " + result.status() + "/" + result.detail());
            }
            var transaction = result.transactionId();
            if (result.succeeded()) {
                captureRef = result.structure().orElseThrow();
                captureState = "DONE";
                return;
            }
            awaitCapture(server, transaction, 0);
        } catch (Throwable error) {
            captureFailure = error.toString();
            captureState = "FAILED";
        }
    }

    private static void awaitCapture(MinecraftServer server, java.util.UUID transaction, int attempts) {
        if (attempts > 240) {
            captureFailure = "transaction_timeout";
            captureState = "FAILED";
            return;
        }
        server.execute(() -> {
            try {
                var current = StructureTransactions.poll(server, transaction);
                if (current.isEmpty()) {
                    captureFailure = "transaction_disappeared";
                    captureState = "FAILED";
                    return;
                }
                var result = current.get();
                if (result.pending()) {
                    server.execute(() -> awaitCapture(server, transaction, attempts + 1));
                    return;
                }
                if (!result.succeeded()) {
                    captureFailure = result.status() + "/" + result.detail();
                    captureState = "FAILED";
                    return;
                }
                captureResult = result;
                captureRef = result.structure().orElseThrow();
                captureState = "DONE";
            } catch (Throwable error) {
                captureFailure = error.toString();
                captureState = "FAILED";
            }
        });
    }

    private static ServerPlayer firstPlayer(MinecraftServer server) {
        var players = server.getPlayerList().getPlayers();
        if (players.isEmpty()) throw new IllegalStateException("Integrated server has no player yet");
        return players.getFirst();
    }

    private static BlockPos sourceOrigin(ServerLevel level, ServerPlayer player) {
        var base = player.blockPosition();
        for (int lift = 0; lift < 6; lift++) {
            var candidate = base.offset(2, lift, 0);
            var free = true;
            for (int x = 0; x < 2 && free; x++) {
                for (int y = 0; y < 2 && free; y++) {
                    for (int z = 0; z < 2 && free; z++) {
                        if (!level.getBlockState(candidate.offset(x, y, z)).isAir()) free = false;
                    }
                }
            }
            if (free) return candidate;
        }
        throw new IllegalStateException("No free capture volume near the player");
    }

    private static void sample(Minecraft mc) {
        if (ticks % 20 == 0) {
            var runtimeJvm = Runtime.getRuntime();
            memoryBytes.add(runtimeJvm.totalMemory() - runtimeJvm.freeMemory());
        }
        sampleTicks++;
        var elapsed = (System.nanoTime() - sampleStartedAt) / 1_000_000_000L;
        if (elapsed < Integer.getInteger("academy.structureStressSampleSeconds", 60)) return;
        phase = Phase.FINISH;
    }

    private static void finish(Minecraft mc) {
        complete = true;
        writeResult("PASSED\n" + summary());
        AcademyCraft.LOGGER.info("STRUCTURE_STRESS_CLIENT_PASSED structures={} frames={} load_ms={}",
                created, frameNanos.size(), levelReadyMillis);
        mc.stop();
    }

    private static String summary() {
        var builder = new StringBuilder();
        builder.append("mode=client\n")
                .append("structures_requested=").append(requested).append('\n')
                .append("structures_created=").append(created).append('\n')
                .append("load_ms=").append(levelReadyMillis).append('\n')
                .append("operable_ms=").append(operableMillis).append('\n')
                .append("sample_ticks=").append(sampleTicks).append('\n')
                .append("warmup_ticks=").append(warmupTicks).append('\n')
                .append("ticks_total=").append(ticks).append('\n');
        appendDistribution(builder, "frame_ms", frameNanos);
        appendDistribution(builder, "render_ms", renderNanos);
        appendDistribution(builder, "tick_ms", tickNanos);
        if (!frameNanos.isEmpty()) {
            var average = averageLong(frameNanos) / 1_000_000.0;
            builder.append("fps_avg=").append(format(average <= 0 ? 0 : 1000.0 / average)).append('\n');
        }
        if (!memoryBytes.isEmpty()) {
            builder.append("heap_used_mb_avg=").append(format(averageLong(memoryBytes) / 1_048_576.0)).append('\n')
                    .append("heap_used_mb_max=").append(format(max(memoryBytes) / 1_048_576.0)).append('\n');
        }
        builder.append("structures=");
        for (var index = 0; index < structures.size(); index++) {
            if (index > 0) builder.append(',');
            builder.append(structures.get(index).id());
        }
        builder.append('\n');
        return builder.toString();
    }

    private static void appendDistribution(StringBuilder builder, String name, List<Long> samples) {
        builder.append(name).append("_count=").append(samples.size()).append('\n');
        if (samples.isEmpty()) return;
        var sorted = new ArrayList<>(samples);
        sorted.sort(Long::compareTo);
        builder.append(name).append("_avg=").append(format(averageLong(sorted) / 1_000_000.0)).append('\n')
                .append(name).append("_p50=").append(format(percentile(sorted, 0.50) / 1_000_000.0)).append('\n')
                .append(name).append("_p95=").append(format(percentile(sorted, 0.95) / 1_000_000.0)).append('\n')
                .append(name).append("_p99=").append(format(percentile(sorted, 0.99) / 1_000_000.0)).append('\n')
                .append(name).append("_max=").append(format(sorted.getLast() / 1_000_000.0)).append('\n');
    }

    private static long percentile(List<Long> sorted, double fraction) {
        var index = (int) Math.round(fraction * (sorted.size() - 1));
        return sorted.get(Math.max(0, Math.min(sorted.size() - 1, index)));
    }

    private static double averageLong(List<Long> samples) {
        double sum = 0;
        for (var sample : samples) sum += sample;
        return sum / samples.size();
    }

    private static long max(List<Long> samples) {
        long maximum = Long.MIN_VALUE;
        for (var sample : samples) maximum = Math.max(maximum, sample);
        return maximum;
    }

    private static String format(double value) {
        return String.format(Locale.ROOT, "%.4f", value);
    }

    private static void writeResult(String text) {
        try {
            var directory = Minecraft.getInstance().gameDirectory.toPath();
            Files.writeString(directory.resolve(RESULT), text,
                    StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE);
        } catch (IOException error) {
            AcademyCraft.LOGGER.error("Cannot write client stress result", error);
        }
    }
}
