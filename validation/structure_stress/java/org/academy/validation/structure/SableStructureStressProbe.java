package org.academy.validation.structure;

import dev.ryanhcode.sable.api.sublevel.ServerSubLevelContainer;
import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.storage.LevelResource;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.server.ServerLifecycleHooks;
import org.academy.AcademyCraft;
import org.academy.api.common.structure.StructureBackendStatus;
import org.academy.api.common.structure.StructureCaptureRequest;
import org.academy.api.common.structure.StructureCaptureResult;
import org.academy.api.common.structure.StructureDirectControl;
import org.academy.api.common.structure.StructureRef;
import org.academy.api.common.structure.StructureSpaces;
import org.academy.api.common.structure.StructureTransactions;
import org.academy.internal.common.structure.sable.SableStructureTraction;
import org.academy.internal.common.structure.sable.SableStructureTransactions;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

/**
 * Opt-in dedicated-server stress probe for the Sable structure backend.
 *
 * <p>Creates the configured number of real Sable sub-levels through the production capture
 * transaction, then runs direct-control lease acquire/validate/launch/release cycles on them,
 * sampling child counts, container save latency, server tick durations and heap usage into a
 * structured result file. Disabled unless {@code academy.structureStress=server-sable}.</p>
 */
@EventBusSubscriber(modid = AcademyCraft.MOD_ID, value = Dist.DEDICATED_SERVER)
public final class SableStructureStressProbe {
    private static final boolean ENABLED = "server-sable".equals(System.getProperty("academy.structureStress"));
    private static final String SOURCE = "stress-server-sable";
    private static final BlockPos BASE = new BlockPos(8, 200, 8);
    private static final int CHILD_SIDE = 2;
    private static final int CHILD_PITCH = 4;
    private static final int CHILD_COLUMNS = 5;
    private static final int MEMORY_SAMPLE_TICKS = 20;
    private static final int SAVE_SAMPLE_EVERY = 10;
    private static final int MOTION_SAMPLE_TICKS = 4;

    private enum Phase {
        PREPARE, CREATE, LOOP_ACQUIRE, LOOP_LAUNCH, LOOP_MOTION, LOOP_RELEASE, SAVE, DRAIN, FINISH
    }

    private static volatile SableStructureStressProbe active;

    private final MinecraftServer server;
    private final ServerLevel host;
    private final Path directory;
    private final ServerSubLevelContainer container;
    private final StringBuilder evidence = new StringBuilder();
    private final List<ChunkPos> tickets = new ArrayList<>();
    private final List<StructureRef> children = new ArrayList<>();
    private final List<Long> tickNanos = new ArrayList<>();
    private final List<Long> memoryBytes = new ArrayList<>();
    private final List<Long> captureNanos = new ArrayList<>();
    private final List<Long> leaseNanos = new ArrayList<>();
    private final List<Long> releaseNanos = new ArrayList<>();
    private final List<Long> launchNanos = new ArrayList<>();
    private final List<Long> saveNanos = new ArrayList<>();
    private final List<Integer> saveChildCounts = new ArrayList<>();
    private int childTarget, operations, sampleSeconds;
    private Phase phase = Phase.PREPARE;
    private int ticks, phaseTicks, loopIndex, createIndex, saveIndex;
    private long tickStartedAt, captureStartedAt, leaseStartedAt, launchStartedAt, saveStartedAt, startedAt;
    private int acquired, occupiedLeases, failedLeases, launched, motionSamples, failedOperations, captureRetries;
    private boolean leaseOk, orderlyStop, watching, complete;
    private String failure;
    private UUID transactionId, leaseControl;
    private int createTicks;
    private Entity actor;

    private SableStructureStressProbe(MinecraftServer server) {
        this.server = server;
        this.host = server.overworld();
        this.directory = verifyDirectory(server);
        this.container = SubLevelContainer.getContainer(host);
        if (container == null) throw new IllegalStateException("Sable did not supply its host container");
        childTarget = Integer.getInteger("academy.structureStressChildren", 10);
        operations = Integer.getInteger("academy.structureStressOperations", 30);
        sampleSeconds = Integer.getInteger("academy.structureStressSeconds", 60);
        if (childTarget < 1 || childTarget > 20) throw new IllegalStateException("children must be 1..20");
        if (operations < 1 || operations > 10_000) throw new IllegalStateException("operations must be 1..10000");
        Runtime.getRuntime().addShutdownHook(Thread.ofPlatform().name("academy-stress-sable-fallback").unstarted(this::writeMissingFailure));
    }

    @SubscribeEvent
    public static void started(ServerStartedEvent event) {
        if (!ENABLED || active != null) return;
        var scenario = new SableStructureStressProbe(event.getServer());
        active = scenario;
        scenario.startedAt = System.nanoTime();
        scenario.guarded(scenario::advance);
    }

    @SubscribeEvent
    public static void preTick(ServerTickEvent.Pre event) {
        var scenario = active;
        if (!ENABLED || scenario == null || scenario.server != event.getServer()) return;
        scenario.tickStartedAt = System.nanoTime();
    }

    @SubscribeEvent
    public static void postTick(ServerTickEvent.Post event) {
        var scenario = active;
        if (!ENABLED || scenario == null || scenario.server != event.getServer()
                || scenario.complete || scenario.failure != null) {
            return;
        }
        scenario.guarded(() -> {
            if (scenario.tickStartedAt != 0) {
                scenario.tickNanos.add(System.nanoTime() - scenario.tickStartedAt);
            }
            if (scenario.ticks % MEMORY_SAMPLE_TICKS == 0) {
                var runtimeJvm = Runtime.getRuntime();
                scenario.memoryBytes.add(runtimeJvm.totalMemory() - runtimeJvm.freeMemory());
            }
            scenario.advance();
        });
    }

    private void advance() {
        ticks++;
        phaseTicks++;
        require(ticks < 20 * (sampleSeconds + 1200), "Sable stress probe exceeded its hard deadline at " + phase);
        switch (phase) {
            case PREPARE -> prepare();
            case CREATE -> create();
            case LOOP_ACQUIRE -> acquire();
            case LOOP_LAUNCH -> launch();
            case LOOP_MOTION -> motion();
            case LOOP_RELEASE -> release();
            case SAVE -> save();
            case DRAIN -> drain();
            case FINISH -> finish();
        }
    }

    private void prepare() {
        if (phaseTicks > 1) return;
        require(StructureSpaces.status(server).mode() == StructureBackendStatus.Mode.SABLE,
                "Sable stress probe requires the Sable backend: " + StructureSpaces.status(server));
        for (int x = -2; x <= CHILD_COLUMNS + 2; x++) {
            for (int z = -2; z <= 8; z++) {
                if (host.setChunkForced(x, z, true)) tickets.add(new ChunkPos(x, z));
                host.getChunk(x, z);
            }
        }
        for (var position : BlockPos.betweenClosed(BASE.offset(-4, -2, -4), BASE.offset(CHILD_COLUMNS * CHILD_PITCH + 4, 8, CHILD_PITCH * 5 + 4))) {
            require(host.getBlockState(position).isAir() && host.getBlockEntity(position) == null,
                    "Sable stress probe refuses existing host content at " + position);
        }
        actor = EntityTypes.ARMOR_STAND.create(host, EntitySpawnReason.COMMAND);
        require(actor != null && host.addFreshEntity(actor), "Cannot create the direct-control actor");
        actor.setNoGravity(true);
        evidence.append("mode=server-sable\n")
                .append("children_requested=").append(childTarget).append('\n')
                .append("operations_requested=").append(operations).append('\n')
                .append("sample_seconds=").append(sampleSeconds).append('\n')
                .append("fixture_forced_chunks=").append(tickets.size()).append('\n');
        next(Phase.CREATE);
    }

    private void create() {
        if (createIndex >= childTarget) {
            evidence.append("children_created=").append(children.size()).append('\n');
            saveIndex++;
            next(Phase.LOOP_ACQUIRE);
            return;
        }
        if (transactionId == null) {
            var origin = childOrigin(createIndex);
            var cells = new ArrayList<BlockPos>(CHILD_SIDE * CHILD_SIDE);
            for (int x = 0; x < CHILD_SIDE; x++) {
                for (int z = 0; z < CHILD_SIDE; z++) {
                    var position = origin.offset(x, 0, z);
                    require(host.getBlockState(position).isAir(), "Sable stress source must start empty: " + position);
                    host.setBlock(position, Blocks.STONE.defaultBlockState(), Block.UPDATE_ALL);
                    cells.add(position);
                }
            }
            createTicks = 0;
            captureStartedAt = System.nanoTime();
            var result = StructureTransactions.capture(host, StructureCaptureRequest.of(cells));
            if (!result.pending() && !result.succeeded() && captureRetries < 3
                    && result.detail() != null && result.detail().contains("journal_")) {
                captureRetries++;
                evidence.append("capture_retry_").append(captureRetries).append('=')
                        .append(result.status()).append('/').append(result.detail()).append('\n');
                return;
            }
            require(result.pending() || result.succeeded(),
                    "Sable stress capture did not start: " + result.status() + "/" + result.detail());
            transactionId = result.transactionId();
            if (result.succeeded()) {
                children.add(result.structure().orElseThrow());
                transactionId = null;
                captureNanos.add(System.nanoTime() - captureStartedAt);
                createIndex++;
            }
            return;
        }
        createTicks++;
        require(createTicks < 200, "Sable stress capture did not converge");
        var result = StructureTransactions.poll(server, transactionId)
                .orElseThrow(() -> new IllegalStateException("Sable stress transaction disappeared"));
        if (result.pending()) return;
        require(result.succeeded(), "Sable stress capture failed: " + result.status() + "/" + result.detail());
        children.add(result.structure().orElseThrow());
        transactionId = null;
        captureNanos.add(System.nanoTime() - captureStartedAt);
        createIndex++;
    }

    private static BlockPos childOrigin(int index) {
        return BASE.offset((index % CHILD_COLUMNS) * CHILD_PITCH, 0, (index / CHILD_COLUMNS) * CHILD_PITCH);
    }

    private void acquire() {
        var reference = children.get(loopIndex % children.size());
        leaseStartedAt = System.nanoTime();
        leaseControl = UUID.randomUUID();
        var status = StructureDirectControl.acquire(host, reference, actor, leaseControl);
        leaseNanos.add(System.nanoTime() - leaseStartedAt);
        switch (status) {
            case ACTIVE -> {
                acquired++;
                leaseOk = true;
                next(Phase.LOOP_LAUNCH);
            }
            case OCCUPIED -> {
                occupiedLeases++;
                leaseOk = true;
                next(Phase.LOOP_LAUNCH);
            }
            case UNAVAILABLE -> {
                failedLeases++;
                leaseOk = false;
                next(Phase.LOOP_RELEASE);
            }
        }
    }

    private void launch() {
        var reference = children.get(loopIndex % children.size());
        launchStartedAt = System.nanoTime();
        var angle = loopIndex * 0.6;
        var velocity = new Vec3(2.0 * Math.cos(angle), 0.6, 2.0 * Math.sin(angle));
        launchOk = SableStructureTraction.setLinearVelocity(host, reference, velocity);
        if (launchOk) {
            launched++;
            launchNanos.add(System.nanoTime() - launchStartedAt);
        }
        next(Phase.LOOP_MOTION);
    }

    private boolean launchOk;

    private void motion() {
        var reference = children.get(loopIndex % children.size());
        if (phaseTicks < MOTION_SAMPLE_TICKS) return;
        var observed = SableStructureTraction.motion(host, reference);
        if (observed.isPresent() && observed.get().linearVelocity().length() > 1.0E-4) motionSamples++;
        next(Phase.LOOP_RELEASE);
    }

    private void release() {
        var reference = children.get(loopIndex % children.size());
        if (leaseOk) {
            var releaseStartedAt = System.nanoTime();
            StructureDirectControl.release(reference, leaseControl);
            releaseNanos.add(System.nanoTime() - releaseStartedAt);
        }
        loopIndex++;
        saveIndex++;
        if (loopIndex >= operations) {
            next(Phase.DRAIN);
            return;
        }
        if (saveIndex % SAVE_SAMPLE_EVERY == 0 || (saveIndex + 1) % SAVE_SAMPLE_EVERY == 0) {
            next(Phase.SAVE);
        } else {
            next(Phase.LOOP_ACQUIRE);
        }
    }

    private void save() {
        if (phaseTicks > 1) {
            next(Phase.LOOP_ACQUIRE);
            return;
        }
        saveStartedAt = System.nanoTime();
        container.getHoldingChunkMap().saveAll();
        saveNanos.add(System.nanoTime() - saveStartedAt);
        saveChildCounts.add(container.getAllSubLevels().size());
        next(Phase.LOOP_ACQUIRE);
    }

    private void drain() {
        var elapsed = (System.nanoTime() - startedAt) / 1_000_000_000L;
        if (elapsed < sampleSeconds || phaseTicks < 40) return;
        var backend = new SableStructureTransactions(server);
        var removed = 0;
        for (var reference : children) {
            if (backend.releaseEmpty(reference)) removed++;
        }
        evidence.append("children_removed=").append(removed)
                .append("\nchildren_before_removal=").append(children.size()).append('\n');
        children.clear();
        next(Phase.FINISH);
    }

    private void finish() {
        if (actor != null && !actor.isRemoved()) actor.discard();
        for (var chunk : tickets) host.setChunkForced(chunk.x(), chunk.z(), false);
        tickets.clear();
        evidence.append(summary());
        write("PASSED\n" + evidence);
        complete = true;
        AcademyCraft.LOGGER.info("SABLE_STRUCTURE_STRESS_PASSED children={} operations={} ticks={}",
                childTarget, operations, ticks);
        server.halt(false);
    }

    private String summary() {
        var builder = new StringBuilder();
        builder.append("children_final=").append(childTarget).append('\n')
                .append("capture_retries=").append(captureRetries).append('\n')
                .append("operations_completed=").append(loopIndex - failedOperations).append('\n')
                .append("operations_failed=").append(failedOperations).append('\n')
                .append("leases_acquired=").append(acquired).append('\n')
                .append("leases_occupied=").append(occupiedLeases).append('\n')
                .append("leases_unavailable=").append(failedLeases).append('\n')
                .append("launches_ok=").append(launched).append('\n')
                .append("motion_samples=").append(motionSamples).append('\n')
                .append("ticks=").append(ticks).append('\n');
        appendDistribution(builder, "tick_ms", tickNanos);
        appendDistribution(builder, "capture_ms", captureNanos);
        appendDistribution(builder, "lease_ms", leaseNanos);
        appendDistribution(builder, "release_ms", releaseNanos);
        appendDistribution(builder, "launch_ms", launchNanos);
        appendDistribution(builder, "save_ms", saveNanos);
        if (!saveChildCounts.isEmpty()) {
            builder.append("save_children_min=").append(saveChildCounts.stream().mapToInt(Integer::intValue).min().orElse(-1)).append('\n')
                    .append("save_children_max=").append(saveChildCounts.stream().mapToInt(Integer::intValue).max().orElse(-1)).append('\n');
        }
        if (!tickNanos.isEmpty()) {
            var tickMs = average(tickNanos) / 1_000_000.0;
            builder.append("mspt_avg=").append(format(tickMs)).append('\n')
                    .append("tps_avg=").append(format(tickMs <= 0 ? 20.0 : Math.min(20.0, 1000.0 / tickMs))).append('\n');
        }
        if (!memoryBytes.isEmpty()) {
            builder.append("heap_used_mb_avg=").append(format(average(memoryBytes) / 1_048_576.0)).append('\n')
                    .append("heap_used_mb_max=").append(format(max(memoryBytes) / 1_048_576.0)).append('\n');
        }
        builder.append("sable_library_note=rapier_steps_are_measured_inside_server_ticks_only\n");
        return builder.toString();
    }

    private static void appendDistribution(StringBuilder builder, String name, List<Long> samples) {
        if (samples.isEmpty()) {
            builder.append(name).append("_count=0\n");
            return;
        }
        var sorted = new ArrayList<>(samples);
        sorted.sort(Long::compareTo);
        builder.append(name).append("_count=").append(sorted.size()).append('\n')
                .append(name).append("_avg=").append(format(averageLong(sorted) / 1_000_000.0)).append('\n')
                .append(name).append("_p50=").append(format(percentile(sorted, 0.50) / 1_000_000.0)).append('\n')
                .append(name).append("_p95=").append(format(percentile(sorted, 0.95) / 1_000_000.0)).append('\n')
                .append(name).append("_p99=").append(format(percentile(sorted, 0.99) / 1_000_000.0)).append('\n')
                .append(name).append("_max=").append(format(sorted.getLast() / 1_000_000.0)).append('\n');
    }

    private static long percentile(List<Long> sorted, double fraction) {
        var index = (int) Math.round(fraction * (sorted.size() - 1));
        return sorted.get(Math.max(0, Math.min(sorted.size() - 1, index)));
    }

    private static double average(List<Long> samples) {
        return averageLong(samples);
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

    private void next(Phase next) {
        phase = next;
        phaseTicks = 0;
    }

    private void guarded(Runnable action) {
        if (complete || failure != null) return;
        try {
            action.run();
        } catch (Throwable error) {
            failure = error.toString();
            AcademyCraft.LOGGER.error("SABLE_STRUCTURE_STRESS_FAILED phase={} operation={} tick={}",
                    phase, loopIndex, ticks, error);
            try {
                if (actor != null && !actor.isRemoved()) actor.discard();
            } catch (Throwable discarding) {
                error.addSuppressed(discarding);
            }
            try {
                for (var chunk : tickets) host.setChunkForced(chunk.x(), chunk.z(), false);
            } catch (Throwable releasing) {
                error.addSuppressed(releasing);
            }
            try {
                evidence.append(summary());
                write("FAILED\nphase=" + phase + "\noperation=" + loopIndex + "\nfailure=" + error + "\n" + evidence);
            } catch (Throwable writing) {
                error.addSuppressed(writing);
            }
            complete = true;
            server.halt(false);
        }
    }

    private void writeMissingFailure() {
        var path = directory.resolve(SOURCE + "-result.txt");
        if (Files.exists(path, LinkOption.NOFOLLOW_LINKS)) return;
        try {
            Files.writeString(path, "FAILED\nfailure=process_exited_before_stress_completion\n" + evidence,
                    StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE);
        } catch (IOException ignored) {
        }
    }

    private void write(String text) {
        try {
            Files.writeString(directory.resolve(SOURCE + "-result.txt"), text,
                    StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE);
        } catch (IOException error) {
            throw new IllegalStateException("Cannot save Sable stress result", error);
        }
    }

    public static Path verifyDirectory(MinecraftServer server) {
        try {
            var root = Path.of(System.getProperty("academy.structureStressRoot")).toRealPath();
            var configured = Path.of(System.getProperty("academy.structureStressDirectory")).toRealPath();
            require(root.endsWith(Path.of("run", "sable-compat")), "Sable stress root must be run/sable-compat");
            var runBase = configured.getParent();
            require(configured.startsWith(root) && configured.getFileName().toString().equals("server")
                            && runBase != null
                            && runBase.getFileName().toString().startsWith(SOURCE),
                    "Sable stress probe requires an isolated run/sable-compat/" + SOURCE + "-*/server directory, got " + configured);
            require(server.getServerDirectory().toRealPath().equals(configured),
                    "Actual server directory differs from the configured stress directory");
            require(server.getWorldPath(LevelResource.ROOT).toRealPath().equals(
                            configured.resolve("academy-sable-stress")),
                    "Sable stress probe refuses linked or unrelated worlds");
            require(!Files.exists(configured.resolve(SOURCE + "-result.txt"), LinkOption.NOFOLLOW_LINKS),
                    "Sable stress result already exists");
            require("127.0.0.1".equals(server.getLocalIp()), "Sable stress server must bind only 127.0.0.1");
            return configured;
        } catch (IOException error) {
            throw new IllegalStateException("Cannot verify Sable stress probe isolation", error);
        }
    }

    @SubscribeEvent
    public static void stopping(ServerStoppingEvent event) {
        var scenario = active;
        if (scenario != null && scenario.server == event.getServer()) {
            scenario.orderlyStop = true;
            if (scenario.actor != null && !scenario.actor.isRemoved()) scenario.actor.discard();
            for (var chunk : scenario.tickets) scenario.host.setChunkForced(chunk.x(), chunk.z(), false);
            scenario.tickets.clear();
        }
    }

    @SubscribeEvent
    public static void stopped(ServerStoppedEvent event) {
        var scenario = active;
        if (!ENABLED || scenario == null || scenario.server != event.getServer() || scenario.watching) return;
        scenario.watching = true;
        Thread.ofPlatform().daemon().name("academy-structure-stress-exit").start(() -> {
            try {
                scenario.server.getRunningThread().join();
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
                return;
            }
            int status = scenario.complete && scenario.failure == null && scenario.orderlyStop
                    && !scenario.server.isCurrentlySaving() ? 0 : 1;
            try {
                var crashDirectory = scenario.directory.resolve("crash-reports");
                if (Files.isDirectory(crashDirectory)) {
                    try (var paths = Files.list(crashDirectory)) {
                        if (paths.findAny().isPresent()) status = 1;
                    }
                }
                Files.writeString(scenario.directory.resolve(SOURCE + "-exit.txt"),
                        "exit_status=" + status + "\nserver_stopped_event=true\nserver_thread_terminated=true\n",
                        StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE);
            } catch (IOException error) {
                status = 1;
            }
            System.out.println("SABLE_STRUCTURE_STRESS_SERVER_THREAD_TERMINATED exit_status=" + status);
            ServerLifecycleHooks.handleExit(status);
        });
    }

    private static void require(boolean value, String message) {
        if (!value) throw new IllegalStateException(message);
    }
}
