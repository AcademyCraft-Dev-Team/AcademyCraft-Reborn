package org.academy.validation.structure;

import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.LevelResource;
import net.minecraft.world.phys.AABB;
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
import org.academy.api.common.structure.StructureBlockData;
import org.academy.api.common.structure.StructureCaptureRequest;
import org.academy.api.common.structure.StructureCaptureResult;
import org.academy.api.common.structure.StructureHandle;
import org.academy.api.common.structure.StructureLifecycle;
import org.academy.api.common.structure.StructurePhysics;
import org.academy.api.common.structure.StructurePhysicsControl;
import org.academy.api.common.structure.StructurePhysicsRegion;
import org.academy.api.common.structure.StructurePose;
import org.academy.api.common.structure.StructureRef;
import org.academy.api.common.structure.StructureSpaces;
import org.academy.api.common.structure.StructureTransactions;
import org.academy.api.common.structure.StructureWriteResult;
import org.academy.internal.common.structure.NativeStructureRuntime;

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
 * Opt-in dedicated-server stress probe for the native structure backend.
 *
 * <p>Runs the configured number of capture/lift/launch/release/recycle operations across up to
 * three concurrent physics regions with resident structures, measures per-stage transaction
 * latencies, server tick durations, region/slot occupancy and heap usage, and records every
 * value into a structured result file. Disabled unless {@code academy.structureStress=server-native}.</p>
 */
@EventBusSubscriber(modid = AcademyCraft.MOD_ID, value = Dist.DEDICATED_SERVER)
public final class NativeStructureStressProbe {
    private static final boolean ENABLED = "server-native".equals(System.getProperty("academy.structureStress"));
    private static final String SOURCE = "stress-server-native";
    private static final int SOURCE_SIDE = 3;
    private static final int REGION_AXIS = 20;
    private static final int REGION_PITCH = 24;
    private static final int BASE_X = 40;
    private static final int BASE_Y = 170;
    private static final int BASE_Z = 40;
    private static final int MEMORY_SAMPLE_TICKS = 20;
    private static final int LIFT_TICKS = 10;
    private static final int LAUNCH_TICKS = 10;
    private static final int MOTION_WAIT_TICKS = 60;

    private enum Phase {
        PREPARE, REGIONS, CAP_PROBE, RESIDENT_ATTACH, SOURCE, CAPTURE, COMMIT, ATTACH, MOTION,
        LIFT, LAUNCH, DETACH, RECYCLE, DRAIN, FINISH
    }

    private static volatile NativeStructureStressProbe active;

    private final MinecraftServer server;
    private final ServerLevel host;
    private final NativeStructureRuntime runtime;
    private final Path directory;
    private final StringBuilder evidence = new StringBuilder();
    private final List<ChunkPos> tickets = new ArrayList<>();
    private final List<StructurePhysicsRegion> regions = new ArrayList<>();
    private final List<StructureHandle> residents = new ArrayList<>();
    private final List<Long> tickNanos = new ArrayList<>();
    private final List<Long> memoryBytes = new ArrayList<>();
    private final List<Long> captureNanos = new ArrayList<>();
    private final List<Long> commitNanos = new ArrayList<>();
    private final List<Long> recycleNanos = new ArrayList<>();
    private final List<Integer> liftRiseTicks = new ArrayList<>();
    private int operations, regionCount, sampleSeconds;
    private Phase phase = Phase.PREPARE;
    private int ticks, phaseTicks, loopIndex, residentIndex, captureTicks, clearedTotal;
    private long tickStartedAt, captureStartedAt, recycleStartedAt, startedAt;
    private long firstSourcePlacedAt, lastRecycleAt;
    private double maxLiftRise, maxLaunchTravel;
    private int completedOperations, failedOperations, captureRejections, slotRecyclesOk, slotRecyclesFailed;
    private int captureRetries, operationRetries;
    private int maxConcurrentStructures, launchSamples;
    private boolean capProbeRejected, capProbeRan, orderlyStop, watching, complete;
    private String failure;
    private UUID transactionId;
    private StructureHandle current;
    private StructureHandle pendingResident;
    private StructurePhysicsRegion currentRegion;
    private Vec3 launchStart;
    private double liftStartY;

    private NativeStructureStressProbe(MinecraftServer server) {
        this.server = server;
        this.host = server.overworld();
        this.runtime = NativeStructureRuntime.backend(server);
        this.directory = verifyDirectory(server);
        operations = Integer.getInteger("academy.structureStressOperations", 30);
        regionCount = Integer.getInteger("academy.structureStressRegions", 2);
        sampleSeconds = Integer.getInteger("academy.structureStressSeconds", 60);
        if (operations < 1 || operations > 10_000) throw new IllegalStateException("operations must be 1..10000");
        if (regionCount < 1 || regionCount > 3) throw new IllegalStateException("regions must be 1..3");
        if (sampleSeconds < 1 || sampleSeconds > 3_600) throw new IllegalStateException("seconds must be 1..3600");
        Runtime.getRuntime().addShutdownHook(Thread.ofPlatform().name("academy-stress-native-fallback").unstarted(this::writeMissingFailure));
    }

    @SubscribeEvent
    public static void started(ServerStartedEvent event) {
        if (!ENABLED || active != null) return;
        var scenario = new NativeStructureStressProbe(event.getServer());
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
        require(ticks < 20 * (sampleSeconds + 600), "Stress probe exceeded its hard deadline at " + phase);
        if (!residentDrive()) return;
        switch (phase) {
            case PREPARE -> prepare();
            case REGIONS -> openRegions();
            case CAP_PROBE -> capProbe();
            case RESIDENT_ATTACH -> residentAttach();
            case SOURCE -> source();
            case CAPTURE -> capture();
            case COMMIT -> commit();
            case ATTACH -> attach();
            case MOTION -> motion();
            case LIFT -> lift();
            case LAUNCH -> launch();
            case DETACH -> detach();
            case RECYCLE -> recycle();
            case DRAIN -> drain();
            case FINISH -> finish();
        }
    }

    private boolean residentDrive() {
        // Residents settle on the per-region stone platform; only the per-operation
        // structure moves. Nothing to drive here, kept as the single hook point.
        return true;
    }

    private void prepare() {
        if (phaseTicks > 1) return;
        require(StructureSpaces.status(server).mode() == StructureBackendStatus.Mode.NATIVE,
                "Native stress probe requires the independent backend");
        for (int region = 0; region < regionCount; region++) {
            var minX = BASE_X + region * REGION_PITCH;
            for (int cx = (minX - 2) >> 4; cx <= (minX + REGION_AXIS + 2) >> 4; cx++) {
                for (int cz = (BASE_Z - 2) >> 4; cz <= (BASE_Z + REGION_AXIS + 2) >> 4; cz++) {
                    if (host.setChunkForced(cx, cz, true)) tickets.add(new ChunkPos(cx, cz));
                    host.getChunk(cx, cz);
                }
            }
        }
        for (var position : BlockPos.betweenClosed(
                new BlockPos(BASE_X - 2, BASE_Y - 16, BASE_Z - 2),
                new BlockPos(BASE_X + regionCount * REGION_PITCH + 2, BASE_Y + 16, BASE_Z + REGION_AXIS + 2))) {
            require(host.getBlockState(position).isAir() && host.getBlockEntity(position) == null,
                    "Stress probe refuses existing host content at " + position);
        }
        for (int region = 0; region < regionCount; region++) {
            var minX = BASE_X + region * REGION_PITCH;
            for (var position : BlockPos.betweenClosed(
                    new BlockPos(minX, BASE_Y, BASE_Z),
                    new BlockPos(minX + REGION_AXIS - 1, BASE_Y, BASE_Z + REGION_AXIS - 1))) {
                host.setBlock(position, Blocks.STONE.defaultBlockState(), Block.UPDATE_CLIENTS);
            }
        }
        evidence.append("mode=server-native\n")
                .append("operations_requested=").append(operations).append('\n')
                .append("regions_requested=").append(regionCount).append('\n')
                .append("sample_seconds=").append(sampleSeconds).append('\n')
                .append("fixture_forced_chunks=").append(tickets.size()).append('\n');
        next(Phase.REGIONS);
    }

    private void openRegions() {
        if (phaseTicks > 1 || regions.size() == regionCount) {
            next(regions.size() == regionCount ? (regionCount == 3 ? Phase.CAP_PROBE : Phase.RESIDENT_ATTACH) : Phase.REGIONS);
            return;
        }
        regions.add(openRegion(regions.size()));
        if (regions.size() == regionCount) next(regionCount == 3 ? Phase.CAP_PROBE : Phase.RESIDENT_ATTACH);
    }

    private StructurePhysicsRegion openRegion(int index) {
        var minX = BASE_X + index * REGION_PITCH;
        var bounds = new AABB(minX, BASE_Y, BASE_Z,
                minX + REGION_AXIS, BASE_Y + REGION_AXIS, BASE_Z + REGION_AXIS);
        var region = StructurePhysics.openRegion(host, bounds);
        require(region.status() == StructurePhysicsRegion.Status.READY, "Region did not open ready: " + region.diagnostic());
        return region;
    }

    private void capProbe() {
        if (phaseTicks > 1) return;
        var rejected = false;
        try {
            openRegion(regionCount);
        } catch (IllegalStateException | IllegalArgumentException expected) {
            rejected = true;
        }
        require(rejected, "Opening a fourth physics region was not rejected");
        capProbeRan = true;
        capProbeRejected = true;
        evidence.append("region_cap_rejected=true\nregions_open=").append(regions.size()).append('\n');
        next(Phase.RESIDENT_ATTACH);
    }

    private void residentAttach() {
        if (residentIndex == regionCount) {
            next(Phase.SOURCE);
            return;
        }
        var region = regions.get(residentIndex);
        var minX = BASE_X + residentIndex * REGION_PITCH;
        if (pendingResident == null) {
            pendingResident = runtime.createEmpty(host, StructurePose.identity(Vec3.ZERO)).orElseThrow(() ->
                    new IllegalStateException("Resident structure needs a free native slot"));
            return;
        }
        if (pendingResident.lifecycle() != StructureLifecycle.ACTIVE) return;
        for (int x = 0; x < 2; x++) {
            for (int z = 0; z < 2; z++) {
                put(pendingResident, new BlockPos(x, 0, z), Blocks.STONE.defaultBlockState());
            }
        }
        pendingResident.setPose(new StructurePose(new Vec3(minX + 16.5, BASE_Y + 4.5, BASE_Z + 16.5), new org.joml.Quaterniond()));
        region.attach(pendingResident);
        residents.add(pendingResident);
        evidence.append("resident_").append(residentIndex + 1).append("=").append(pendingResident.reference()).append('\n');
        pendingResident = null;
        residentIndex++;
        if (residentIndex == regionCount) next(Phase.SOURCE);
    }

    private void source() {
        currentRegion = regions.get(loopIndex % regions.size());
        var index = loopIndex % regions.size();
        var minX = BASE_X + index * REGION_PITCH;
        var origin = new BlockPos(minX + 8, BASE_Y + 8, BASE_Z + 8);
        for (int x = 0; x < SOURCE_SIDE; x++) {
            for (int y = 0; y < SOURCE_SIDE; y++) {
                for (int z = 0; z < SOURCE_SIDE; z++) {
                    var position = origin.offset(x, y, z);
                    require(host.getBlockState(position).isAir(), "Operation source must start empty: " + position);
                    host.setBlock(position, Blocks.STONE.defaultBlockState(), Block.UPDATE_ALL);
                }
            }
        }
        firstSourcePlacedAt = System.nanoTime();
        next(Phase.CAPTURE);
    }

    private void capture() {
        var index = loopIndex % regions.size();
        var minX = BASE_X + index * REGION_PITCH;
        var origin = new BlockPos(minX + 8, BASE_Y + 8, BASE_Z + 8);
        var cells = new ArrayList<BlockPos>(SOURCE_SIDE * SOURCE_SIDE * SOURCE_SIDE);
        for (var position : BlockPos.betweenClosed(origin, origin.offset(SOURCE_SIDE - 1, SOURCE_SIDE - 1, SOURCE_SIDE - 1))) {
            cells.add(position.immutable());
        }
        captureStartedAt = System.nanoTime();
        var result = StructureTransactions.capture(host,
                new StructureCaptureRequest(cells, StructureCaptureRequest.MAXIMUM_BLOCKS,
                        StructureCaptureRequest.MAXIMUM_SPAN, StructureCaptureRequest.ALLOW));
        if (!result.pending() && !result.succeeded() && operationRetries < 3
                && result.detail() != null && result.detail().contains("journal_")) {
            operationRetries++;
            captureRetries++;
            evidence.append("capture_retry_").append(captureRetries).append('=')
                    .append(result.status()).append('/').append(result.detail()).append('\n');
            next(Phase.CAPTURE);
            return;
        }
        require(result.pending() || result.succeeded(),
                "Operation capture failed: " + result.status() + "/" + result.detail());
        transactionId = result.transactionId();
        captureTicks = 0;
        if (result.succeeded()) {
            current = resolve(result);
            next(Phase.ATTACH);
        } else {
            next(Phase.COMMIT);
        }
    }

    private void commit() {
        captureTicks++;
        require(captureTicks < 200, "Capture transaction did not converge");
        var result = StructureTransactions.poll(server, transactionId)
                .orElseThrow(() -> new IllegalStateException("Capture transaction disappeared"));
        if (result.pending()) return;
        require(result.succeeded(), "Capture transaction failed: " + result.status() + "/" + result.detail());
        captureNanos.add(System.nanoTime() - captureStartedAt);
        current = resolve(result);
        next(Phase.ATTACH);
    }

    private StructureHandle resolve(StructureCaptureResult result) {
        var reference = result.structure().orElseThrow();
        return StructureSpaces.resolve(server, reference).orElseThrow(() ->
                new IllegalStateException("Committed capture must stay resolvable: " + reference));
    }

    private void attach() {
        currentRegion.attach(current);
        next(Phase.MOTION);
    }

    private void motion() {
        if (phaseTicks < MOTION_WAIT_TICKS && currentRegion.motion(current.reference()).isEmpty()) return;
        require(currentRegion.motion(current.reference()).isPresent(),
                "Attached structure never produced solved motion");
        liftStartY = current.pose().localToWorldPoint(Vec3.atCenterOf(new BlockPos(1, 1, 1))).y;
        maxLiftRise = 0;
        next(Phase.LIFT);
    }

    private void lift() {
        var motion = currentRegion.motion(current.reference()).orElseThrow();
        var localCenter = Vec3.atCenterOf(new BlockPos(1, 1, 1));
        currentRegion.setControl(current.reference(),
                new StructurePhysicsControl.Force(localCenter, new Vec3(0, motion.mass() * 24, 0)));
        var rise = current.pose().localToWorldPoint(localCenter).y - liftStartY;
        maxLiftRise = Math.max(maxLiftRise, rise);
        if (phaseTicks >= LIFT_TICKS) {
            liftRiseTicks.add(phaseTicks);
            currentRegion.clearControl(current.reference());
            launchStart = current.pose().localToWorldPoint(localCenter);
            maxLaunchTravel = 0;
            next(Phase.LAUNCH);
        }
    }

    private void launch() {
        var localCenter = Vec3.atCenterOf(new BlockPos(1, 1, 1));
        if (phaseTicks == 1) {
            currentRegion.setVelocity(current.reference(), new Vec3(-8.0, 2.0, 0.0), Vec3.ZERO);
        }
        var travel = current.pose().localToWorldPoint(localCenter).distanceTo(launchStart);
        maxLaunchTravel = Math.max(maxLaunchTravel, travel);
        if (phaseTicks >= LAUNCH_TICKS || travel > 4.0) {
            launchSamples++;
            next(Phase.DETACH);
        }
    }

    private void detach() {
        require(currentRegion.detach(current.reference()), "Detach rejected the current attachment");
        recycleStartedAt = System.nanoTime();
        next(Phase.RECYCLE);
    }

    private void recycle() {
        var reference = current.reference();
        var handle = StructureSpaces.resolve(server, reference).orElse(null);
        int cleared = 0;
        if (handle != null) {
            for (var local : BlockPos.betweenClosed(new BlockPos(0, 0, 0), new BlockPos(SOURCE_SIDE - 1, SOURCE_SIDE - 1, SOURCE_SIDE - 1))) {
                var before = handle.world().read(local);
                if (before.state().isAir()) continue;
                require(handle.world().compareAndSet(local, before, new StructureBlockData(Blocks.AIR.defaultBlockState()))
                        == StructureWriteResult.APPLIED, "Recycle clear failed at " + local);
                cleared++;
            }
        }
        require(cleared == SOURCE_SIDE * SOURCE_SIDE * SOURCE_SIDE,
                "Recycle cleared " + cleared + " cells instead of " + (SOURCE_SIDE * SOURCE_SIDE * SOURCE_SIDE));
        clearedTotal += cleared;
        require(runtime.unload(reference), "Recycle unload rejected the operation structure");
        var discarded = runtime.discardEmpty(reference);
        recycleNanos.add(System.nanoTime() - recycleStartedAt);
        current = null;
        currentRegion = null;
        operationRetries = 0;
        if (discarded) {
            slotRecyclesOk++;
        } else {
            slotRecyclesFailed++;
        }
        completedOperations++;
        lastRecycleAt = System.nanoTime();
        if (completedOperations >= operations) {
            evidence.append("slot_recycle_ok=").append(slotRecyclesOk)
                    .append("\nslot_recycle_failed=").append(slotRecyclesFailed).append('\n');
            next(Phase.DRAIN);
        } else {
            loopIndex++;
            next(Phase.SOURCE);
        }
    }

    private void drain() {
        var elapsed = (System.nanoTime() - startedAt) / 1_000_000_000L;
        if (elapsed < sampleSeconds || phaseTicks < 40) return;
        for (var index = 0; index < residents.size(); index++) {
            var handle = residents.get(index);
            var region = regions.get(index);
            region.detach(handle.reference());
            for (var local : BlockPos.betweenClosed(new BlockPos(0, 0, 0), new BlockPos(1, 0, 1))) {
                var before = handle.world().read(local);
                if (!before.state().isAir()) {
                    handle.world().compareAndSet(local, before, new StructureBlockData(Blocks.AIR.defaultBlockState()));
                }
            }
            runtime.unload(handle.reference());
            require(runtime.discardEmpty(handle.reference()), "Resident recycle must release its slot");
        }
        residents.clear();
        next(Phase.FINISH);
    }

    private void finish() {
        for (var region : regions) region.close();
        regions.clear();
        for (var chunk : tickets) host.setChunkForced(chunk.x(), chunk.z(), false);
        tickets.clear();
        var summary = summary();
        evidence.append(summary);
        write("PASSED\n" + evidence);
        complete = true;
        AcademyCraft.LOGGER.info("NATIVE_STRUCTURE_STRESS_PASSED operations={} regions={} ticks={}",
                completedOperations, regionCount, ticks);
        server.halt(false);
    }

    private String summary() {
        var builder = new StringBuilder();
        builder.append("operations_completed=").append(completedOperations).append('\n')
                .append("operations_failed=").append(failedOperations).append('\n')
                .append("capture_rejections=").append(captureRejections).append('\n')
                .append("capture_retries=").append(captureRetries).append('\n')
                .append("regions_open=").append(regionCount).append('\n')
                .append("region_cap_probe=").append(capProbeRan && capProbeRejected ? "rejected" : "not_run").append('\n')
                .append("resident_structures=").append(regionCount).append('\n')
                .append("max_concurrent_structures=").append(regionCount + 1).append('\n')
                .append("slot_recycle_ok=").append(slotRecyclesOk).append('\n')
                .append("slot_recycle_failed=").append(slotRecyclesFailed).append('\n')
                .append("cleared_cells=").append(clearedTotal).append('\n')
                .append("ticks=").append(ticks).append('\n');
        appendDistribution(builder, "tick_ms", tickNanos);
        appendDistribution(builder, "capture_ms", captureNanos);
        appendDistribution(builder, "recycle_ms", recycleNanos);
        if (!tickNanos.isEmpty()) {
            var tickMs = average(tickNanos) / 1_000_000.0;
            builder.append("mspt_avg=").append(format(tickMs)).append('\n')
                    .append("tps_avg=").append(format(tickMs <= 0 ? 20.0 : Math.min(20.0, 1000.0 / tickMs))).append('\n');
        }
        if (!memoryBytes.isEmpty()) {
            builder.append("heap_used_mb_avg=").append(format(average(memoryBytes) / 1_048_576.0)).append('\n')
                    .append("heap_used_mb_max=").append(format(max(memoryBytes) / 1_048_576.0)).append('\n');
        }
        builder.append("lift_rise_max=").append(format(maxLiftRise)).append('\n')
                .append("launch_travel_max=").append(format(maxLaunchTravel)).append('\n')
                .append("launch_samples=").append(launchSamples).append('\n');
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
        AcademyCraft.LOGGER.info("NATIVE_STRUCTURE_STRESS_PHASE {} operation={} tick={}", phase, loopIndex, ticks);
    }

    private static void put(StructureHandle handle, BlockPos pos, BlockState state) {
        var before = handle.world().read(pos);
        if (before.state().equals(state)) return;
        require(handle.world().compareAndSet(pos, before, new StructureBlockData(state)) == StructureWriteResult.APPLIED,
                "Stress storage CAS failed at " + pos);
    }

    private void guarded(Runnable action) {
        if (complete || failure != null) return;
        try {
            action.run();
        } catch (Throwable error) {
            failure = error.toString();
            AcademyCraft.LOGGER.error("NATIVE_STRUCTURE_STRESS_FAILED phase={} operation={} tick={}",
                    phase, loopIndex, ticks, error);
            try {
                for (var region : regions) region.close();
            } catch (Throwable closing) {
                error.addSuppressed(closing);
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
            throw new IllegalStateException("Cannot save stress result", error);
        }
    }

    public static Path verifyDirectory(MinecraftServer server) {
        try {
            var configured = Path.of(System.getProperty("academy.structureStressDirectory")).toRealPath();
            var root = Path.of(System.getProperty("academy.structureStressRunRoot")).toRealPath();
            require(configured.startsWith(root) && configured.getFileName().toString().startsWith(SOURCE),
                    "Stress probe requires an isolated run/" + SOURCE + "-* directory");
            require(server.getServerDirectory().toRealPath().equals(configured),
                    "Actual server directory differs from the configured stress directory");
            require(server.getWorldPath(LevelResource.ROOT).toRealPath().equals(
                            configured.resolve("native-structure-stress")),
                    "Stress probe refuses linked or unrelated worlds");
            require(!Files.exists(configured.resolve(SOURCE + "-result.txt"), LinkOption.NOFOLLOW_LINKS),
                    "Stress result already exists");
            return configured;
        } catch (IOException error) {
            throw new IllegalStateException("Cannot verify stress probe isolation", error);
        }
    }

    @SubscribeEvent
    public static void stopping(ServerStoppingEvent event) {
        var scenario = active;
        if (scenario != null && scenario.server == event.getServer()) {
            scenario.orderlyStop = true;
            for (var region : scenario.regions) {
                try {
                    region.close();
                } catch (RuntimeException ignored) {
                }
            }
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
            System.out.println("NATIVE_STRUCTURE_STRESS_SERVER_THREAD_TERMINATED exit_status=" + status);
            ServerLifecycleHooks.handleExit(status);
        });
    }

    private static void require(boolean value, String message) {
        if (!value) throw new IllegalStateException(message);
    }
}
