package org.academy.internal.common.ability.program;

import com.google.gson.GsonBuilder;
import com.mojang.authlib.GameProfile;
import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.gametest.framework.GameTestInstance;
import net.minecraft.gametest.framework.TestData;
import net.minecraft.gametest.framework.TestEnvironmentDefinition;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.FullChunkStatus;
import net.minecraft.server.level.Ticket;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.Marker;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.storage.LevelResource;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.TicketStorage;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterGameTestsEvent;
import net.neoforged.neoforge.registries.RegisterEvent;
import org.academy.AcademyCraft;
import org.academy.api.common.ability.program.ProgramStructureReference;
import org.academy.api.common.ability.program.ProgramWorldPosition;
import org.academy.api.common.profiler.AcademyProfiler;
import org.academy.api.common.structure.*;
import org.academy.internal.common.structure.StructureBackendRouter;
import org.academy.internal.common.structure.space.NativeStructureTickets;
import org.academy.internal.common.sublevel.SubLevelServer;
import org.academy.internal.common.sublevel.SubLevelStructureHandle;
import org.academy.internal.common.sublevel.SubLevelStructureRefs;
import org.academy.mixin.common.ServerLevelEntityManagerAccessor;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.lang.management.ManagementFactory;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.function.Supplier;

/** Opt-in isolated GameTest: real loaded queries, bounded samples, no query algorithm changes. */
@EventBusSubscriber(modid = AcademyCraft.MOD_ID)
public final class ProgramQueryBenchmarkGameTests {
    private static final Identifier TYPE = AcademyCraft.academy("precision_query_benchmark_function");
    private static final int WARMUP = 32, SAMPLES = 128, PROFILE_SAMPLES = 16, BATCH = 16;

    private ProgramQueryBenchmarkGameTests() {}

    @SubscribeEvent private static void registerType(RegisterEvent event) {
        event.register(Registries.TEST_INSTANCE_TYPE, TYPE, () -> Instance.CODEC);
    }

    @SubscribeEvent private static void registerTests(RegisterGameTestsEvent event) {
        if (!Boolean.getBoolean("academy.precisionQueryBenchmark")) return;
        var environment = event.registerEnvironment(TYPE, new TestEnvironmentDefinition.AllOf(List.of()));
        event.registerTest(AcademyCraft.academy("precision_query_benchmark"), new Instance(new TestData<>(
                environment, Identifier.withDefaultNamespace("empty"), 1200, 0, true,
                Rotation.NONE, false, 1, 1, false, 16)));
    }

    private record Workload(String name, Supplier<?> query, Function<Object, String> signature) {}

    private static final class Scenario {
        private final GameTestHelper helper;
        private final ServerLevel host;
        private final List<Entity> entities = new ArrayList<>();
        private final List<ChunkPos> tickets = new ArrayList<>();
        private final List<ProgramStructureReference> structures = new ArrayList<>();
        private final LinkedHashSet<BlockPos> placed = new LinkedHashSet<>();
        private final List<Map<String, Object>> cases = new ArrayList<>();
        private final long[] samples = new long[SAMPLES];
        private final long[] allocations = new long[SAMPLES];
        private final Map<String, Object> assertions = new LinkedHashMap<>();
        private final ServerPlayer player;
        private final ServerProgramTargetResolver resolver;
        private final Vec3 center;
        private final ProgramWorldPosition origin;
        private int entityCase, workloadIndex, iteration;
        private UUID capture;
        private Workload workload;
        private List<Workload> structureWorkloads;
        private String expected;
        private boolean finished;
        private com.sun.management.ThreadMXBean allocationBean;
        private boolean restoreAllocationDisabled;

        private Scenario(GameTestHelper helper) {
            this.helper = helper;
            host = helper.getLevel();
            // GameTest chooses arbitrary coordinates, which can exceed the native world's
            // supported relative position range. Own an independently checked empty arena.
            center = new Vec3(128.5, host.getMaxY() - 48.5, 128.5);
            var profile = new GameProfile(uuid("player"), "query-benchmark");
            player = new ServerPlayer(host.getServer(), host, profile,
                    CommonListenerCookie.createInitial(profile, false).clientInformation());
            player.snapTo(center.x, center.y, center.z, 0, 0);
            player.setNoGravity(true);
            resolver = new ServerProgramTargetResolver(player, 64, 128);
            origin = new ProgramWorldPosition(host.dimension().identifier(), center.x, center.y, center.z);
        }

        private void start() {
            guarded(() -> {
                check(!AcademyProfiler.isCapturingZones() && !AcademyProfiler.isSampling(), "profiler must start disabled");
                if (ManagementFactory.getThreadMXBean() instanceof com.sun.management.ThreadMXBean bean
                        && bean.isThreadAllocatedMemorySupported()) {
                    try {
                        restoreAllocationDisabled = !bean.isThreadAllocatedMemoryEnabled();
                        if (restoreAllocationDisabled) bean.setThreadAllocatedMemoryEnabled(true);
                        allocationBean = bean;
                    } catch (UnsupportedOperationException | SecurityException ignored) {
                        allocationBean = null;
                    }
                }
                check(StructureBackendRouter.nativeEnabled(host.getServer()), "baseline requires the standalone native backend");
                check(SubLevelServer.runtime(host).isPresent(), "real plot-backed runtime required");
                var ticketStorage = host.getDataStorage().computeIfAbsent(TicketStorage.TYPE);
                for (int x = (BlockPos.containing(center).getX() - 33) >> 4;
                        x <= (BlockPos.containing(center).getX() + 33) >> 4; x++) {
                    for (int z = (BlockPos.containing(center).getZ() - 33) >> 4;
                            z <= (BlockPos.containing(center).getZ() + 33) >> 4; z++) {
                        var chunk = new ChunkPos(x, z);
                        check(ticketStorage.addTicket(chunk.pack(),
                                new Ticket(NativeStructureTickets.ACTIVE.get(), NativeStructureTickets.TICKET_LEVEL)),
                                "arena ticket already owned");
                        tickets.add(chunk);
                    }
                }
                for (var position : BlockPos.betweenClosed(BlockPos.containing(center).offset(-33, -2, -33),
                        BlockPos.containing(center).offset(33, 18, 33))) {
                    host.getChunkAt(position);
                    check(host.getBlockState(position).isAir(), "benchmark arena occupied: " + position);
                }
                check(host.getEntities(player, new AABB(center, center).inflate(33), _ -> true).isEmpty(),
                        "entity benchmark needs a fresh, isolated arena");
                check(StructureQueries.nearby(host, center, 40).isEmpty(), "structure benchmark needs an empty arena");
                awaitArena(200);
            });
        }

        private void awaitArena(int remaining) {
            var manager = ((ServerLevelEntityManagerAccessor) host).academy$getEntityManager();
            boolean loaded = tickets.stream().allMatch(position -> {
                var chunk = host.getChunkSource().getChunkNow(position.x(), position.z());
                return chunk != null && chunk.getFullStatus().isOrAfter(FullChunkStatus.ENTITY_TICKING)
                        && manager.areEntitiesLoaded(position.pack());
            });
            if (!loaded) {
                check(remaining > 0, "arena entity storage/ticking did not become ready");
                later(() -> awaitArena(remaining - 1));
                return;
            }
            assertions.put("arena_entity_ticking_and_storage_ready", true);
            prepareEntities();
        }

        private void prepareEntities() {
            clearEntities();
            int count = new int[]{0, 64, 256, 1024}[entityCase];
            for (int index = 0; index < count; index++) {
                var entity = new Marker(EntityTypes.MARKER, host);
                entity.setUUID(uuid("entity-" + index));
                entity.setPos(center.x + index % 32 - 15.5, center.y, center.z + index / 32 - 15.5);
                entity.setNoGravity(true);
                check(host.addFreshEntity(entity), "benchmark marker could not be added");
                entities.add(entity);
            }
            // Inside the AABB but outside the sphere: counters must distinguish tested and accepted.
            for (int index = 0; index < 8; index++) {
                var entity = new Marker(EntityTypes.MARKER, host);
                entity.setUUID(uuid("outside-" + index));
                entity.setPos(center.x + 31 + index * .05, center.y, center.z + 31);
                check(host.addFreshEntity(entity), "outside-sphere marker could not be added");
                entities.add(entity);
            }
            workload = new Workload("entities_" + count, () -> resolver.entitiesAround(origin, 32), value -> {
                var list = (List<?>) value;
                check(list.size() == Math.min(count, 128), "entity result limit or sphere filter changed: requested="
                        + count + " actual=" + list.size());
                var ids = list.stream().map(Entity.class::cast).map(entity -> entity.getUUID().toString()).toList();
                check(ids.equals(ids.stream().sorted().toList()), "entity UUID ordering changed");
                return ids.toString();
            });
            startWorkload();
        }

        private void startWorkload() {
            iteration = 0;
            expected = workload.signature.apply(workload.query.get());
            later(this::batch);
        }

        private void batch() {
            for (int batch = 0; batch < BATCH && iteration < WARMUP + SAMPLES; batch++, iteration++) {
                long allocatedBefore = allocatedBytes();
                long start = System.nanoTime();
                var result = workload.query.get();
                long elapsed = System.nanoTime() - start;
                long allocatedAfter = allocatedBytes();
                check(expected.equals(workload.signature.apply(result)), "live query result changed in fixed fixture: " + workload.name);
                if (iteration >= WARMUP) {
                    samples[iteration - WARMUP] = elapsed;
                    allocations[iteration - WARMUP] = allocatedBefore >= 0 && allocatedAfter >= allocatedBefore
                            ? allocatedAfter - allocatedBefore : -1;
                }
            }
            if (iteration < WARMUP + SAMPLES) { later(this::batch); return; }
            var row = new LinkedHashMap<String, Object>();
            row.put("name", workload.name);
            row.put("warmup", WARMUP);
            row.put("samples", SAMPLES);
            row.put("timing_capture_enabled", false);
            row.put("profile_queries", PROFILE_SAMPLES);
            var sorted = samples.clone(); Arrays.sort(sorted);
            row.put("p50_ns", sorted[(SAMPLES - 1) / 2]);
            row.put("p95_ns", sorted[(int) Math.ceil(SAMPLES * .95) - 1]);
            row.put("p99_ns", sorted[(int) Math.ceil(SAMPLES * .99) - 1]);
            row.put("max_ns", sorted[SAMPLES - 1]);
            row.put("total_ns", Arrays.stream(samples).sum());
            var allocationSamples = Arrays.stream(allocations).filter(value -> value >= 0).sorted().toArray();
            row.put("allocation_samples", allocationSamples.length);
            if (allocationSamples.length > 0) {
                row.put("allocated_bytes_p50", allocationSamples[(allocationSamples.length - 1) / 2]);
                row.put("allocated_bytes_p95", allocationSamples[(int) Math.ceil(allocationSamples.length * .95) - 1]);
                row.put("allocated_bytes_p99", allocationSamples[(int) Math.ceil(allocationSamples.length * .99) - 1]);
                row.put("allocated_bytes_total", Arrays.stream(allocationSamples).sum());
            }
            row.put("result_signature", expected);
            AcademyProfiler.startZoneCapture();
            try {
                for (int sample = 0; sample < PROFILE_SAMPLES; sample++) {
                    check(expected.equals(workload.signature.apply(workload.query.get())), "capture changed query results");
                }
            } finally { AcademyProfiler.stopZoneCapture(); }
            var zones = AcademyProfiler.snapshot().getZones().get(Thread.currentThread().getName());
            check(zones != null, "server-thread profiler results missing");
            row.put("zones", zones.topSlices(128, true));
            cases.add(row);
            if (structureWorkloads == null) {
                if (++entityCase < 4) { later(this::prepareEntities); return; }
                verifyLiveEntityChange(); clearEntities(); later(this::captureStructure); return;
            }
            if (++workloadIndex < structureWorkloads.size()) {
                workload = structureWorkloads.get(workloadIndex); startWorkload(); return;
            }
            verifyLiveStructureChange();
            finish();
        }

        private void captureStructure() {
            var seed = BlockPos.containing(center).offset(structures.isEmpty() ? 4 : 10, 0, 0);
            if (capture == null) {
                var cells = new LinkedHashSet<BlockPos>(); cells.add(seed);
                if (!structures.isEmpty()) {
                    for (int offset = 1; offset < 16; offset++) {
                        cells.add(seed.offset(offset, 0, 0)); cells.add(seed.offset(0, offset, 0));
                        cells.add(seed.offset(0, 0, offset));
                    }
                }
                for (var cell : cells) {
                    place(cell, Blocks.IRON_BLOCK.defaultBlockState());
                    if (cell.getY() == seed.getY()) place(cell.below(), Blocks.STONE.defaultBlockState());
                }
                var result = StructureTransactions.capture(host, new StructureCaptureRequest(List.copyOf(cells),
                        cells.size(), 16, StructureCaptureRequest.ALLOW, true));
                check(result.pending() || result.succeeded(), "benchmark capture rejected: " + result);
                capture = result.transactionId();
            }
            var result = StructureTransactions.poll(host.getServer(), capture).orElseThrow();
            if (result.pending()) { later(this::captureStructure); return; }
            check(result.succeeded(), "benchmark capture failed: " + result);
            var reference = result.structure().orElseThrow();
            var handle = StructureSpaces.resolve(host.getServer(), reference).orElse(null);
            if (handle == null || StructureDynamics.motion(host, reference).isEmpty()) { later(this::captureStructure); return; }
            structures.add(new ProgramStructureReference(reference, handle.runtimeId())); capture = null;
            if (structures.size() < 2) { later(this::captureStructure); return; }
            check(resolver.structureContents(structures.get(0)).orElseThrow().scannedCells() == 1, "small summary bounds changed");
            check(resolver.structureContents(structures.get(1)).orElseThrow().scannedCells() == 4096, "sparse 16-cubed summary bounds changed");
            var sparse = structures.get(1);
            var stale = new ProgramStructureReference(sparse.reference(), uuid("stale-runtime"));
            structureWorkloads = List.of(
                    new Workload("structures_around_2", () -> resolver.structuresAround(origin, 32), value -> {
                        var hits = (List<?>) value;
                        check(hits.equals(structures), "nearest-surface structure ordering or authorization changed");
                        return "small,sparse4096";
                    }),
                    new Workload("structure_distance_sparse", () -> resolver.structureDistanceTo(sparse, origin), value -> {
                        check(((java.util.OptionalDouble) value).isPresent(), "live structure distance unavailable"); return "present";
                    }),
                    contents("structure_contents_small", structures.get(0), 1, 1),
                    contents("structure_contents_sparse4096", sparse, 4096, 46),
                    new Workload("structure_contents_stale", () -> resolver.structureContents(stale), value -> {
                        check(((java.util.Optional<?>) value).isEmpty(), "stale runtime admitted"); return "unavailable";
                    }),
                    new Workload("summary_budget_reject", () -> StructureSpaces.resolve(host.getServer(), sparse.reference())
                            .orElseThrow().world().summarize(4095), value -> {
                        check(((java.util.Optional<?>) value).isEmpty(), "partial over-budget summary escaped"); return "unavailable";
                    }));
            workload = structureWorkloads.getFirst(); workloadIndex = 0; startWorkload();
        }

        private Workload contents(String name, ProgramStructureReference reference, int cells, int blocks) {
            return new Workload(name, () -> resolver.structureContents(reference), value -> {
                var summary = (StructureBlockSummary) ((java.util.Optional<?>) value).orElseThrow();
                check(summary.scannedCells() == cells && summary.counts().equals(Map.of(Blocks.IRON_BLOCK.defaultBlockState(), blocks)),
                        "summary cells or block states changed: " + name);
                return cells + ":iron=" + blocks;
            });
        }

        private void verifyLiveEntityChange() {
            var target = entities.getFirst();
            target.setPos(center.x, center.y, center.z);
            check(resolver.entitiesAround(origin, 1).contains(target), "live moved entity missing");
            target.setPos(center.x + 40, center.y, center.z);
            check(!resolver.entitiesAround(origin, 1).contains(target), "query reused stale entity position");
            assertions.put("same_tick_entity_position", true);
        }

        private void verifyLiveStructureChange() {
            var target = structures.get(0);
            var handle = StructureSpaces.resolve(host.getServer(), target.reference()).orElseThrow();
            check(handle instanceof SubLevelStructureHandle, "physical capture must use plot-backed structures");
            var bounds = ((SubLevelStructureHandle) handle).subLevel().localBounds();
            var cell = BlockPos.containing(bounds.minX, bounds.minY, bounds.minZ);
            var before = handle.world().read(cell);
            check(handle.world().compareAndSet(cell, before, new StructureBlockData(Blocks.GOLD_BLOCK.defaultBlockState()))
                    == StructureWriteResult.APPLIED, "fixture content edit failed");
            check(resolver.structureContents(target).orElseThrow().counts().containsKey(Blocks.GOLD_BLOCK.defaultBlockState()),
                    "same-tick content edit was cached");
            check(handle.world().compareAndSet(cell, handle.world().read(cell), before) == StructureWriteResult.APPLIED,
                    "fixture content restore failed");
            var shortRange = new ServerProgramTargetResolver(player, 1, 128);
            check(shortRange.structureContents(target).isEmpty(), "actor range guard changed");
            try { resolver.entitiesAround(new ProgramWorldPosition(origin.dimension(), center.x + 65, center.y, center.z), 1);
                throw new AssertionError("out-of-range query origin admitted");
            } catch (IllegalArgumentException expected) { }
            check(SubLevelServer.runtime(host).orElseThrow().suspendCapture(SubLevelStructureRefs.id(target.reference()).orElseThrow()),
                    "fixture suspend failed");
            check(!resolver.isStructureValid(target) && resolver.structureContents(target).isEmpty(), "inactive body admitted");
            assertions.put("same_tick_content_change", true); assertions.put("range_and_origin_guards", true);
            assertions.put("inactive_and_stale_runtime_rejected", true); assertions.put("capture_on_off_equivalence", true);
        }

        private void place(BlockPos position, net.minecraft.world.level.block.state.BlockState state) {
            var key = position.immutable();
            if (placed.contains(key)) return;
            check(host.getBlockState(key).isAir() && host.getBlockEntity(key) == null, "fixture would replace existing block");
            check(host.setBlock(key, state, 3), "fixture block could not be placed"); placed.add(key);
        }

        private void later(Runnable action) { helper.runAfterDelay(1, () -> guarded(action)); }
        private void guarded(Runnable action) {
            if (finished) return;
            try { action.run(); } catch (Throwable failure) {
                finished = true; AcademyProfiler.stopZoneCapture();
                try { write(false, failure); } catch (Exception saveFailure) { failure.addSuppressed(saveFailure); }
                cleanup(); helper.fail("Precision query benchmark: " + failure);
            }
        }

        private void finish() {
            try { write(true, null); } catch (Exception failure) { throw new IllegalStateException(failure); }
            cleanup(); finished = true;
            AcademyCraft.LOGGER.info("PRECISION_QUERY_BENCHMARK_PASSED cases={}", cases.size()); helper.succeed();
        }

        private void write(boolean passed, Throwable failure) throws java.io.IOException {
            var result = new LinkedHashMap<String, Object>();
            result.put("passed", passed); result.put("baseline", "0ad66ef7");
            result.put("stage", System.getProperty("academy.precisionQueryBenchmark.stage", "baseline"));
            result.put("source", "real isolated GameTest ServerLevel; production resolver and plot-backed structures");
            result.put("timing", "System.nanoTime wall-clock ns per unprofiled query, not thread CPU time; result checks excluded; 16-query batches across ticks");
            result.put("allocation", "ThreadMXBean current server-thread allocated bytes when supported; sampled outside the timing interval; result checks excluded");
            result.put("profile_scope", "separate 16 query pass per case; fixed zone/counter labels only");
            result.put("entity_fixture", "0/64/256/1024 non-player markers plus 8 AABB-only outside-sphere markers; UUID sort; maxResults=128");
            result.put("structure_fixture", "2 actual physical captures: 1 cell and 46 connected iron cells in a 16x16x16 box");
            result.put("arena", List.of(center.x, center.y, center.z));
            result.put("limitations", "single-server-thread microbenchmark; not multiplayer TPS or PVP-player load measurement");
            result.put("assertions", assertions); result.put("cases", cases);
            if (failure != null) result.put("failure", failure.toString());
            Files.writeString(host.getServer().getWorldPath(LevelResource.ROOT).resolve("precision-query-benchmark.json"),
                    new GsonBuilder().setPrettyPrinting().create().toJson(result) + "\n");
        }

        private void clearEntities() { entities.forEach(Entity::discard); entities.clear(); }
        private long allocatedBytes() {
            return allocationBean == null ? -1 : allocationBean.getThreadAllocatedBytes(Thread.currentThread().threadId());
        }
        private void cleanup() {
            if (allocationBean != null && restoreAllocationDisabled) allocationBean.setThreadAllocatedMemoryEnabled(false);
            clearEntities();
            for (var reference : structures) {
                SubLevelServer.runtime(host).ifPresent(runtime ->
                        SubLevelStructureRefs.id(reference.reference()).ifPresent(runtime::remove));
            }
            for (var position : placed) host.setBlock(position, Blocks.AIR.defaultBlockState(), 3);
            var storage = host.getDataStorage().computeIfAbsent(TicketStorage.TYPE);
            for (var position : tickets) storage.removeTicket(position.pack(),
                    new Ticket(NativeStructureTickets.ACTIVE.get(), NativeStructureTickets.TICKET_LEVEL));
            tickets.clear();
        }

        private static UUID uuid(String label) {
            return UUID.nameUUIDFromBytes(("academy-precision-query-" + label).getBytes(StandardCharsets.UTF_8));
        }
        private static void check(boolean condition, String message) { if (!condition) throw new AssertionError(message); }
    }

    private static final class Instance extends GameTestInstance {
        private static final MapCodec<Instance> CODEC = TestData.CODEC.xmap(Instance::new, Instance::info);
        private Instance(TestData<Holder<TestEnvironmentDefinition<?>>> info) { super(info); }
        @Override public void run(GameTestHelper helper) { new Scenario(helper).start(); }
        @Override public MapCodec<? extends GameTestInstance> codec() { return CODEC; }
        @Override protected MutableComponent typeDescription() {
            return Component.literal("Production program query timings, fixed profiler counters and live-query semantics");
        }
    }
}
