package org.academy.internal.common.ability.program;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonParser;
import com.mojang.serialization.MapCodec;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.*;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.Ticket;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.Marker;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.TicketStorage;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.storage.LevelResource;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterGameTestsEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.registries.RegisterEvent;
import org.academy.AcademyCraft;
import org.academy.api.common.ability.program.*;
import org.academy.api.common.profiler.AcademyProfiler;
import org.academy.api.server.ability.AbilitySystemServer;
import org.academy.internal.common.ability.AbilityCategories;
import org.academy.internal.common.ability.accelerator.program.AcceleratorProgramNodeCatalog;
import org.academy.internal.common.ability.program.compile.ProgramBookCodec;
import org.academy.internal.common.ability.program.registry.AbilityProgramDefinitions;
import org.academy.internal.common.ability.program.registry.CommonProgramNodeIds;
import org.academy.internal.common.structure.space.NativeStructureTickets;
import org.academy.mixin.common.ServerLevelEntityManagerAccessor;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.security.MessageDigest;
import java.util.*;

/** Natural registered-player ticks and production admission, with simulated in-memory connections. */
@EventBusSubscriber(modid = AcademyCraft.MOD_ID)
public final class ProgramAutomaticBenchmarkGameTests {
    private static final Identifier TYPE = AcademyCraft.academy("precision_automatic_benchmark_function");
    private static final Identifier CATEGORY = AcceleratorProgramNodeCatalog.ACCELERATOR;
    private static final int WARMUP = 32, SAMPLES = 128, PROFILE = 16;
    private static Scenario active;

    private ProgramAutomaticBenchmarkGameTests() {}

    @SubscribeEvent private static void registerType(RegisterEvent event) {
        event.register(Registries.TEST_INSTANCE_TYPE, TYPE, () -> Instance.CODEC);
    }

    @SubscribeEvent private static void registerTests(RegisterGameTestsEvent event) {
        if (!Boolean.getBoolean("academy.precisionAutomaticBenchmark")) return;
        var environment = event.registerEnvironment(TYPE, new TestEnvironmentDefinition.AllOf(List.of()));
        event.registerTest(AcademyCraft.academy("precision_automatic_benchmark"), new Instance(new TestData<>(
                environment, Identifier.withDefaultNamespace("empty"), 6000, 0, true,
                Rotation.NONE, false, 1, 1, false, 16)));
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST) private static void beforeTick(ServerTickEvent.Pre event) {
        if (active != null) active.beforeTick();
    }

    @SubscribeEvent(priority = EventPriority.LOWEST) private static void afterTick(ServerTickEvent.Post event) {
        if (active != null) active.guarded(active::afterTick);
    }

    static AbilityProgram program(int slot, int waitTicks) {
        var nodes = new ArrayList<ProgramGraph.Node>();
        var edges = new ArrayList<ProgramGraph.Edge>();
        node(nodes, 1, CommonProgramNodeIds.TRIGGER_LOOP, "{\"enabled\":true,\"interval\":1}");
        node(nodes, 3, CommonProgramNodeIds.SCALAR_CONSTANT, "{\"type\":\"integer\",\"value\":\"1\"}");
        node(nodes, 4, CommonProgramNodeIds.SHARED_VARIABLE_GET, variable("count_" + slot));
        node(nodes, 5, CommonProgramNodeIds.NUMERIC_ARITHMETIC, "{\"type\":\"integer\",\"operator\":\"add\"}");
        node(nodes, 6, CommonProgramNodeIds.SHARED_VARIABLE_SET, variable("count_" + slot));
        node(nodes, 7, CommonProgramNodeIds.CASTER, "{}");
        node(nodes, 8, CommonProgramNodeIds.ENTITY_POSITION, "{}");
        node(nodes, 9, CommonProgramNodeIds.SCALAR_CONSTANT, "{\"type\":\"float\",\"value\":\"16.0\"}");
        node(nodes, 10, CommonProgramNodeIds.ENTITIES_AROUND, "{}");
        node(nodes, 11, CommonProgramNodeIds.collection("entity", "size"), "{}");
        node(nodes, 12, CommonProgramNodeIds.SHARED_VARIABLE_SET, variable("query_" + slot));
        if (waitTicks > 0) {
            node(nodes, 2, CommonProgramNodeIds.WAIT, "{}");
            node(nodes, 13, CommonProgramNodeIds.SCALAR_CONSTANT, "{\"type\":\"integer\",\"value\":\"" + waitTicks + "\"}");
            edge(edges, 1, "flow", 2, "flow"); edge(edges, 13, "value", 2, "ticks");
            edge(edges, 2, "flow", 6, "flow");
        } else edge(edges, 1, "flow", 6, "flow");
        edge(edges, 4, "value", 5, "left"); edge(edges, 3, "value", 5, "right");
        edge(edges, 5, "result", 6, "value"); edge(edges, 6, "flow", 12, "flow");
        edge(edges, 7, "entity", 8, "entity"); edge(edges, 8, "position", 10, "center");
        edge(edges, 9, "value", 10, "radius"); edge(edges, 10, "entities", 11, "values");
        edge(edges, 11, "size", 12, "value");
        return new AbilityProgram(AbilityProgram.CURRENT_SCHEMA_VERSION, uuid("graph-" + slot + "-" + waitTicks),
                "Automatic query " + slot, CATEGORY, new ProgramGraph(nodes, edges), ProgramEditorLayout.EMPTY);
    }

    private static String variable(String name) {
        return "{\"name\":\"" + name + "\",\"type\":\"" + ProgramValueTypes.INTEGER.id() + "\"}";
    }
    private static void node(List<ProgramGraph.Node> nodes, int id, Identifier type, String json) {
        nodes.add(new ProgramGraph.Node(id, type, 1, JsonParser.parseString(json)));
    }
    private static void edge(List<ProgramGraph.Edge> edges, int a, String out, int b, String in) {
        edges.add(new ProgramGraph.Edge(new ProgramGraph.Endpoint(a, out), new ProgramGraph.Endpoint(b, in)));
    }
    private static UUID uuid(String label) { return UUID.nameUUIDFromBytes(("academy-automatic-" + label).getBytes(StandardCharsets.UTF_8)); }

    private record Workload(int players, int graphs, int waitTicks, boolean boundary) {}

    private static final class Scenario {
        private final GameTestHelper helper;
        private final ServerLevel level;
        private final Vec3 center;
        private final List<Workload> workloads = new ArrayList<>();
        private final List<CapturedProgramPlayer> peers = new ArrayList<>();
        private final List<Entity> markers = new ArrayList<>();
        private final List<ChunkPos> tickets = new ArrayList<>();
        private final List<Map<String, Object>> results = new ArrayList<>();
        private final List<Map<String, Object>> measured = new ArrayList<>();
        private final Map<String, Long> caseCounters = new LinkedHashMap<>();
        private final Map<String, Long> measuredNetwork = new LinkedHashMap<>();
        private final Map<String, Long> profileNetwork = new LinkedHashMap<>();
        private final Map<String, Object> assertions = new LinkedHashMap<>();
        private Map<String, Object> latestObservations = Map.of();
        private Workload workload;
        private ProgramBook installed;
        private Map<String, Object> row;
        private String phase;
        private int caseIndex, phaseTicks, runningPeak;
        private long serverWall, serverCpu, serverAllocation;
        private boolean finished, metricsStarted;

        Scenario(GameTestHelper helper) {
            this.helper = helper; level = helper.getLevel();
            center = new Vec3(128.5, level.getMaxY() - 48.5, 128.5);
            for (int wait : new int[]{0, 4}) for (int players : new int[]{1, 8, 32}) for (int graphs : new int[]{1, 4})
                workloads.add(new Workload(players, graphs, wait, false));
            workloads.add(new Workload(32, 9, 72000, true));
        }

        void start() { guarded(() -> {
            check(active == null, "benchmark must own its isolated server");
            check(!AcademyProfiler.isCapturingZones() && !AcademyProfiler.isSampling(), "profilers must start disabled");
            check(level.getServer().getPlayerList().getPlayers().isEmpty(), "unexpected non-fixture online player");
            active = this;
            var storage = level.getDataStorage().computeIfAbsent(TicketStorage.TYPE);
            for (int x = 7; x <= 9; x++) for (int z = 7; z <= 9; z++) {
                var pos = new ChunkPos(x, z); tickets.add(pos);
                storage.addTicket(pos.pack(), new Ticket(NativeStructureTickets.ACTIVE.get(), NativeStructureTickets.TICKET_LEVEL));
                level.getChunk(x, z);
            }
            nextCase();
        }); }

        private void nextCase() {
            workload = workloads.get(caseIndex);
            measured.clear(); caseCounters.clear(); measuredNetwork.clear(); profileNetwork.clear(); runningPeak = 0;
            row = new LinkedHashMap<>();
            row.put("players", workload.players); row.put("automatic_graphs_per_player", workload.graphs);
            row.put("wait_ticks", workload.waitTicks); row.put("boundary_only", workload.boundary);
            installed = ProgramBook.empty(AbilityProgramManager.SLOT_COUNT);
            var definition = AbilityProgramDefinitions.require(CATEGORY);
            for (int slot = 0; slot < workload.graphs; slot++) {
                var program = program(slot, workload.waitTicks);
                var compiled = definition.compile(program, Set.of());
                check(compiled.valid(), "ordinary workload graph invalid: " + compiled.diagnostics());
                installed = installed.replaceSlot(slot, program);
            }
            byte[] encoded = ProgramBookCodec.encode(installed);
            check(ProgramBookCodec.decode(encoded).valid(), "book codec round trip rejected");
            row.put("book_sha256", hex(encoded)); row.put("book_bytes", encoded.length);
            row.put("graph_nodes", installed.slot(0).program().graph().nodes().size());
            row.put("graph_edges", installed.slot(0).program().graph().edges().size());
            for (int i = 0; i < workload.players; i++) {
                var peer = CapturedProgramPlayer.create(level, uuid("case-" + caseIndex + "-player-" + i), "auto-peer-" + i,
                        center.add(i % 8 * .6 - 2, 0, i / 8 * .6 - 1));
                peers.add(peer);
                var player = peer.player(); player.noPhysics = true; player.setNoGravity(true);
                var ability = AbilitySystemServer.getSystem(player);
                ability.setPlayerAbilityCategory(player.getUUID(), AbilityCategories.ACCELERATOR.get());
                ability.setPlayerLevel(player.getUUID(), 5);
                ability.setPlayerBaseMaxCP(player.getUUID(), 1_000_000);
                ability.setPlayerAvailableCP(player.getUUID(), 1_000_000);
                ability.setPlayerStatus(player.getUUID(), org.academy.api.common.data.AbilityData.Status.NORMAL);
                check(ability.getPlayerLevel(player.getUUID()) == 5, "registered player must really be unlocked");
                peer.drain();
            }
            phase = "READY"; phaseTicks = 0;
        }

        void beforeTick() {
            if (!metricsStarted) return;
            serverCpu = ProgramAutomaticMetrics.cpuNow(); serverAllocation = ProgramAutomaticMetrics.allocatedNow();
            serverWall = System.nanoTime();
        }

        @SuppressWarnings("unchecked") private void afterTick() {
            if (finished) return;
            long elapsed = System.nanoTime() - serverWall;
            long cpu = ProgramAutomaticMetrics.cpuNow(), allocation = ProgramAutomaticMetrics.allocatedNow();
            phaseTicks++;
            if (phase.equals("READY")) {
                check(phaseTicks <= 240, "registered-player arena did not become queryable");
                var entities = ((ServerLevelEntityManagerAccessor) level).academy$getEntityManager();
                if (tickets.stream().anyMatch(pos -> !entities.areEntitiesLoaded(pos.pack()))) return;
                if (markers.isEmpty()) for (int i = 0; i < 16; i++) {
                    var marker = new Marker(EntityTypes.MARKER, level);
                    marker.setUUID(uuid("marker-" + caseIndex + "-" + i));
                    marker.setPos(center.x + i % 4 * .3, center.y + 1, center.z + i / 4 * .3);
                    check(level.addFreshEntity(marker), "fixture marker registration failed"); markers.add(marker);
                }
                for (var peer : peers) {
                    var player = peer.player();
                    check(level.getServer().getPlayerList().getPlayer(player.getUUID()) == player, "player not in actual PlayerList");
                    var data = AbilitySystemServer.getSystem(player).getPlayerData(player.getUUID());
                    data.setAbilityProgramBook(CATEGORY.toString(), AbilityProgramManager.encodeStoredBook(installed));
                    ProgramSharedVariables.clear(player, CATEGORY); peer.drain();
                }
                ProgramAutomaticMetrics.start(peers.stream().map(p -> p.player().getUUID()).collect(java.util.stream.Collectors.toSet()));
                metricsStarted = true;
                row.put("thread_cpu_available", ProgramAutomaticMetrics.cpuAvailable());
                row.put("thread_allocation_available", ProgramAutomaticMetrics.allocationAvailable());
                phase = workload.boundary ? "BOUNDARY" : "WARMUP"; phaseTicks = 0;
                return;
            }
            if (phase.equals("DRAIN")) {
                peers.forEach(CapturedProgramPlayer::drain);
                if (phaseTicks < 8) return;
                finishCase(); return;
            }
            var observations = ProgramAutomaticMetrics.snapshotAndReset();
            latestObservations = observations;
            var counters = (Map<String, Long>) observations.get("counters");
            counters.forEach((key, value) -> caseCounters.merge(key, value, Long::sum));
            check(counters.get("loop_dispatch") == workload.players && counters.get("health_dispatch") == workload.players,
                    "natural dispatch mismatch: expected=" + workload.players + ", phase=" + phase + ", phaseTick=" + phaseTicks
                            + ", counters=" + counters + ", peers=" + peerDiagnostics());
            int running = runningSessions(); runningPeak = Math.max(runningPeak, running);
            check(running <= 256, "production scheduler exceeded 256 sessions");
            if (phase.equals("MEASURE")) {
                observations.put("server_tick_event_interval", Map.of("wall_ns", elapsed,
                        "cpu_ns", serverCpu < 0 ? -1 : cpu - serverCpu,
                        "allocated_bytes", serverAllocation < 0 ? -1 : allocation - serverAllocation));
                measured.add(observations);
            }
            drainNetwork(phase.equals("MEASURE") ? measuredNetwork : phase.equals("PROFILE") ? profileNetwork : null);
            if (phase.equals("BOUNDARY")) {
                if (phaseTicks < 4) return;
                check(running == 256, "natural automatic load must fill exactly 256 live sessions");
                check(caseCounters.get("session_admitted") == 256 && caseCounters.get("session_rejected") > 0,
                        "the natural 257th scheduler admission must be rejected");
                row.put("actual_live_sessions", running); row.put("counters", new LinkedHashMap<>(caseCounters));
                row.put("passed", true); results.add(row);
                assertions.put("natural_256_257_session_boundary", true);
                closeCase(); finish(); return;
            }
            if (phase.equals("WARMUP") && phaseTicks == WARMUP) {
                ProgramNetworkMetrics.resetCapture(true);
                phase = "MEASURE"; phaseTicks = 0;
            }
            else if (phase.equals("MEASURE") && phaseTicks == SAMPLES) {
                row.put("production_network_metrics_measured", ProgramNetworkMetrics.snapshot());
                ProgramNetworkMetrics.resetCapture(true);
                phase = "PROFILE"; phaseTicks = 0; AcademyProfiler.startZoneCapture();
            } else if (phase.equals("PROFILE") && phaseTicks == PROFILE) {
                AcademyProfiler.stopZoneCapture();
                row.put("production_network_metrics_profiled", ProgramNetworkMetrics.snapshot());
                ProgramNetworkMetrics.resetCapture(false);
                var zones = new ArrayList<Object>();
                AcademyProfiler.snapshot().getZones().values().forEach(snapshot -> zones.addAll(snapshot.topSlices(Integer.MAX_VALUE, true)));
                row.put("separate_profile_zones", zones);
                row.put("profile_network", new LinkedHashMap<>(profileNetwork));
                row.put("all_observed_counters", new LinkedHashMap<>(caseCounters));
                row.put("measured_network", new LinkedHashMap<>(measuredNetwork));
                row.put("running_session_peak_at_post", runningPeak);
                row.put("timed_samples", summarize(measured));
                for (var peer : peers) AbilitySystemServer.getSystem(peer.player()).getPlayerData(peer.player().getUUID())
                        .setAbilityProgramBook(CATEGORY.toString(), AbilityProgramManager.encodeStoredBook(ProgramBook.empty(AbilityProgramManager.SLOT_COUNT)));
                ProgramAutomaticMetrics.stop(); metricsStarted = false; phase = "DRAIN"; phaseTicks = 0;
            }
        }

        private void drainNetwork(Map<String, Long> output) {
            for (var peer : peers) for (var packet : peer.drain()) if (output != null) {
                String key = packet.kind().name().toLowerCase(Locale.ROOT);
                output.merge(key + ".packets", 1L, Long::sum);
                output.merge(key + ".payload_bytes", (long) packet.payloadBytes(), Long::sum);
                output.merge(key + ".misaka_envelope_bytes", (long) packet.envelopeBytes(), Long::sum);
            }
        }

        private int runningSessions() {
            int count = 0;
            for (var peer : peers) for (int slot = 0; slot < workload.graphs; slot++) {
                if (ServerProgramScheduler.contains(level.getServer(), new ServerProgramScheduler.SessionKey(
                        peer.player().getUUID(), CATEGORY, installed.slot(slot).program().id(), slot))) count++;
            }
            return count;
        }

        private void finishCase() {
            check(runningSessions() == 0, "normal workload sessions must all finish after removing triggers");
            long completed = 0;
            var signature = new ArrayList<Object>();
            for (var peer : peers) {
                var player = peer.player();
                var resolver = new ServerProgramTargetResolver(player, 64, 256);
                var point = new ProgramWorldPosition(level.dimension().identifier(), player.getX(), player.getY(), player.getZ());
                int expected = resolver.entitiesAround(point, 16).size();
                check(expected >= 16, "actual query arena must contain all fixture markers");
                for (int slot = 0; slot < workload.graphs; slot++) {
                    int count = (Integer) ProgramSharedVariables.read(player, CATEGORY, "count_" + slot).orElseThrow().value();
                    int query = (Integer) ProgramSharedVariables.read(player, CATEGORY, "query_" + slot).orElseThrow().value();
                    check(count > 0 && query == expected, "actual transactional counter/query output differs");
                    completed += count; signature.add(List.of(player.getUUID().toString(), slot, count, query));
                }
            }
            long admitted = caseCounters.get("accepted_immediate") + caseCounters.get("accepted_deferred");
            check(completed == admitted, "all admitted ordinary runs must commit exactly once: " + completed + "/" + admitted);
            if (workload.waitTicks > 0) {
                check(caseCounters.get("already_running") > 0 && caseCounters.get("session_admitted") > 0
                        && caseCounters.get("session_completed") > 0, "WAIT workload must naturally retain and resume real sessions");
            } else check(admitted == (long) workload.players * workload.graphs * (WARMUP + SAMPLES + PROFILE),
                    "synchronous automatic graph throughput changed");
            row.put("committed_runs", completed); row.put("functional_result_signature", signature);
            row.put("all_admitted_runs_committed_exactly_once", true); row.put("query_result_and_loaded_players_verified", true);
            row.put("passed", true); results.add(row);
            closeCase(); caseIndex++; nextCase();
        }

        private void closeCase() {
            ProgramNetworkMetrics.resetCapture(false);
            if (metricsStarted) { ProgramAutomaticMetrics.stop(); metricsStarted = false; }
            for (var peer : peers) peer.close(); peers.clear();
            markers.forEach(Entity::discard); markers.clear();
        }

        private void finish() {
            assertions.put("natural_player_tick_dispatch", true);
            assertions.put("ordinary_graph_compile_and_codec", true);
            assertions.put("real_transactional_outputs_and_resume", true);
            write(true, null); cleanup(); finished = true; active = null; helper.succeed();
        }

        private void write(boolean passed, Throwable failure) {
            var report = new LinkedHashMap<String, Object>();
            report.put("passed", passed); report.put("baseline", "c701bd9a");
            report.put("stage", System.getProperty("academy.precisionAutomaticBenchmark.stage", "baseline"));
            report.put("source", "1/8/32 actual PlayerList-registered ServerPlayer instances; EmbeddedChannel simulated connections; natural player Post ticks dispatch LOOP and HEALTH");
            report.put("warmup_ticks", WARMUP); report.put("measured_ticks", SAMPLES); report.put("separate_profile_ticks", PROFILE);
            report.put("percentile_method", "nearest rank ceil(p*n)-1; warmup and profile ticks excluded");
            report.put("measurement_mode", "AcademyProfiler and sampling profiler disabled for measured ticks; opt-in stage wall/CPU/allocation and program-network counters enabled. This includes measurement overhead and is not production-default instrumentation-off cost.");
            report.put("timing_scope", "Per-tick sum of observed Manager dispatch and scheduler key-tick intervals. Nested stages overlap their parent; do not add all stages together. Optional server_tick_event_interval runs Pre HIGHEST to this Post LOWEST, before fixture drain/checks; not complete tickServer or TPS.");
            report.put("allocation_scope", "current server thread only; nested clock/recording overhead retained; native/other-thread allocations excluded");
            report.put("network_scope", "shared capture helper directly observes and decodes the actual sent S2CPacket body outside timed intervals; payload and Misaka envelope bytes; no observer re-encode and not TCP/framing/compression/bandwidth");
            report.put("limitations", List.of("Not 32 Minecraft clients, WAN or client rendering.", "Control/query/transaction workloads have no paid category action; they do not measure physics/CP spending/cooldowns.",
                    "LOOP precedes HEALTH and shares the per-player budget; this benchmark does not claim cross-trigger fairness.",
                    "Books are installed directly through real player data during setup; network save/revision correctness is a separate fixture.",
                    "Post-tick live-session counts can miss within-tick peaks; scheduler admission/completion counters separately verify real sessions.",
                    "Default max 256 sessions is unchanged; boundary uses real 32-player x9 automatic long-WAIT programs."));
            report.put("java", System.getProperty("java.runtime.version")); report.put("arena", List.of(center.x, center.y, center.z));
            report.put("assertions", assertions); report.put("cases", results);
            if (failure != null) {
                report.put("failure", failure.toString());
                report.put("current_case", row); report.put("current_phase", phase); report.put("current_phase_tick", phaseTicks);
                report.put("latest_observations", latestObservations); report.put("peer_diagnostics", peerDiagnostics());
            }
            try { Files.writeString(level.getServer().getWorldPath(LevelResource.ROOT).resolve("precision-automatic-benchmark.json"),
                    new GsonBuilder().setPrettyPrinting().create().toJson(report) + "\n"); }
            catch (java.io.IOException e) { throw new IllegalStateException(e); }
        }

        private void cleanup() {
            AcademyProfiler.stopZoneCapture(); closeCase();
            var storage = level.getDataStorage().computeIfAbsent(TicketStorage.TYPE);
            for (var pos : tickets) storage.removeTicket(pos.pack(), new Ticket(NativeStructureTickets.ACTIVE.get(), NativeStructureTickets.TICKET_LEVEL));
            tickets.clear();
        }

        void guarded(Runnable action) {
            if (finished) return;
            try { action.run(); }
            catch (Throwable failure) {
                finished = true; active = null;
                try { write(false, failure); } catch (Throwable writing) { failure.addSuppressed(writing); }
                cleanup();
                // Event-bus callbacks are outside GameTest's managed task runner. Report failure
                // through a GameTest task rather than crashing the whole dedicated server.
                helper.runAfterDelay(1, () -> helper.fail("Automatic benchmark: " + failure));
            }
        }

        private List<Map<String, Object>> peerDiagnostics() {
            return peers.stream().map(peer -> {
                var player = peer.player();
                var info = new LinkedHashMap<String, Object>();
                info.put("id", player.getUUID().toString()); info.put("tick_count", player.tickCount);
                info.put("registered", level.getServer().getPlayerList().getPlayer(player.getUUID()) == player);
                info.put("server_connection_registered", level.getServer().getConnection().getConnections().contains(player.connection.getConnection()));
                info.put("alive", player.isAlive()); info.put("removed", player.isRemoved());
                info.put("disconnected", player.hasDisconnected()); info.put("channel_active", peer.channel().isActive());
                return (Map<String, Object>) info;
            }).toList();
        }
    }

    @SuppressWarnings("unchecked") private static Map<String, Object> summarize(List<Map<String, Object>> samples) {
        var result = new LinkedHashMap<String, Object>();
        var stages = new LinkedHashMap<String, Object>();
        for (var stage : ProgramAutomaticMetrics.Stage.values()) {
            String name = stage.name().toLowerCase(Locale.ROOT);
            var metrics = new LinkedHashMap<String, Object>();
            for (String metric : List.of("calls", "wall_ns", "cpu_ns", "allocated_bytes")) {
                long[] values = samples.stream().mapToLong(sample -> ((Number) ((Map<String, Object>)
                        ((Map<String, Object>) sample.get("stages")).get(name)).get(metric)).longValue()).toArray();
                metrics.put(metric, distribution(values));
            }
            stages.put(name, metrics);
        }
        result.put("automatic_stages_per_server_tick", stages);
        var server = new LinkedHashMap<String, Object>();
        for (String metric : List.of("wall_ns", "cpu_ns", "allocated_bytes")) server.put(metric, distribution(samples.stream()
                .mapToLong(sample -> ((Number) ((Map<String, Object>) sample.get("server_tick_event_interval")).get(metric)).longValue()).toArray()));
        result.put("server_tick_event_interval", server);
        return result;
    }

    static Map<String, Object> distribution(long[] values) {
        long[] sorted = values.clone(); Arrays.sort(sorted);
        if (sorted.length == 0 || sorted[0] < 0) return Map.of("available", false, "samples", values.length);
        return Map.of("available", true, "samples", sorted.length, "min", sorted[0], "p50", sorted[(int) Math.ceil(sorted.length * .50) - 1],
                "p95", sorted[(int) Math.ceil(sorted.length * .95) - 1], "p99", sorted[(int) Math.ceil(sorted.length * .99) - 1],
                "max", sorted[sorted.length - 1], "total", Arrays.stream(sorted).sum());
    }

    private static String hex(byte[] bytes) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes)); }
        catch (java.security.NoSuchAlgorithmException e) { throw new AssertionError(e); }
    }
    private static void check(boolean value, String reason) { if (!value) throw new AssertionError(reason); }

    private static final class Instance extends GameTestInstance {
        private static final MapCodec<Instance> CODEC = TestData.CODEC.xmap(Instance::new, Instance::info);
        Instance(TestData<Holder<TestEnvironmentDefinition<?>>> info) { super(info); }
        @Override public void run(GameTestHelper helper) { new Scenario(helper).start(); }
        @Override public MapCodec<? extends GameTestInstance> codec() { return CODEC; }
        @Override protected MutableComponent typeDescription() { return Component.literal("Natural automatic player workload"); }
    }
}
