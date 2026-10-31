package org.academy.internal.common.ability.program;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.*;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.Ticket;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.TicketStorage;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.LevelResource;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterGameTestsEvent;
import net.neoforged.neoforge.registries.RegisterEvent;
import org.academy.AcademyCraft;
import org.academy.api.common.ability.AbilityCategory;
import org.academy.api.common.ability.program.*;
import org.academy.api.common.entitycontrol.ControlCapability;
import org.academy.api.server.ability.AbilitySystemServer;
import org.academy.internal.client.ability.program.ProgramTaskTemplates;
import org.academy.internal.common.ability.AbilityCategories;
import org.academy.internal.common.ability.accelerator.program.AcceleratorProgramExecutionBridge;
import org.academy.internal.common.ability.aeromanip.program.AeromanipProgramExecutionBridge;
import org.academy.internal.common.ability.darkmatter.program.DarkmatterProgramExecutionBridge;
import org.academy.internal.common.ability.electromaster.program.ElectromasterProgramExecutionBridge;
import org.academy.internal.common.ability.meltdowner.program.MeltdownerProgramExecutionBridge;
import org.academy.internal.common.ability.mentalout.precision.PrecisionOperationRuntime;
import org.academy.internal.common.ability.mentalout.control.MentalControlRuntime;
import org.academy.internal.common.ability.mentalout.control.MentaloutControlContext;
import org.academy.internal.common.ability.program.registry.AbilityProgramDefinitions;
import org.academy.internal.common.ability.program.registry.CommonProgramNodeIds;
import org.academy.internal.common.ability.teleport.program.TeleportProgramExecutionBridge;
import org.academy.internal.common.structure.space.NativeStructureTickets;
import org.academy.mixin.common.ServerLevelEntityManagerAccessor;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.*;

/** Isolated, opt-in real server execution of the seven new editable advanced recipes. */
@EventBusSubscriber(modid = AcademyCraft.MOD_ID)
public final class ProgramAdvancedTaskGameTests {
    private ProgramAdvancedTaskGameTests() {}
    private static final Identifier TYPE = AcademyCraft.academy("precision_advanced_tasks_function");

    @SubscribeEvent private static void registerType(RegisterEvent event) {
        event.register(Registries.TEST_INSTANCE_TYPE, TYPE, () -> Instance.CODEC);
    }
    @SubscribeEvent private static void registerTests(RegisterGameTestsEvent event) {
        if (!Boolean.getBoolean("academy.precisionAdvancedTasks")) return;
        var environment = event.registerEnvironment(TYPE, new TestEnvironmentDefinition.AllOf(List.of()));
        event.registerTest(AcademyCraft.academy("precision_advanced_tasks"), new Instance(new TestData<>(
                environment, Identifier.withDefaultNamespace("empty"), 600, 0, true,
                Rotation.NONE, false, 1, 1, false, 16)));
    }

    private static final class Scenario {
        final GameTestHelper helper;
        final ServerLevel level;
        final Vec3 center;
        final List<ChunkPos> tickets = new ArrayList<>();
        final Map<BlockPos, BlockState> restored = new LinkedHashMap<>();
        final List<String> checks = new ArrayList<>();
        final List<Map<String, Object>> cases = new ArrayList<>();
        CapturedProgramPlayer peer;
        Mob mentalSubject;
        Object lastExecution;
        ProgramInvocationContext lastInvocation;
        boolean finished;
        int readyTicks;

        Scenario(GameTestHelper helper) {
            this.helper = helper; level = helper.getLevel();
            center = new Vec3(128.5, level.getMaxY() - 48.5, 128.5);
        }
        void start() { guarded(() -> {
            check(level.getServer().getPlayerList().getPlayers().isEmpty(), "fixture requires its isolated server");
            var storage = level.getDataStorage().computeIfAbsent(TicketStorage.TYPE);
            for (int x = 7; x <= 9; x++) for (int z = 7; z <= 9; z++) {
                var chunk = new ChunkPos(x, z); tickets.add(chunk);
                storage.addTicket(chunk.pack(), new Ticket(NativeStructureTickets.ACTIVE.get(), NativeStructureTickets.TICKET_LEVEL));
                level.getChunk(x, z);
            }
            awaitReady();
        }); }

        void awaitReady() { helper.runAfterDelay(1, () -> guarded(() -> {
            var entities = ((ServerLevelEntityManagerAccessor) level).academy$getEntityManager();
            if (tickets.stream().anyMatch(chunk -> !entities.areEntitiesLoaded(chunk.pack()))) {
                check(++readyTicks < 120, "fixture chunks did not become entity-ticking"); awaitReady(); return;
            }
            negativeCases(); positiveCases(); mentalNoOpPreservesControl(); sampleAcrossWait();
        })); }

        void negativeCases() {
            for (var kind : ProgramTaskTemplates.Kind.values()) if (kind.advanced()) {
                open(kind, "negative-" + kind.name());
                var player = peer.player();
                BlockPos target = sightBlock(4);
                if (kind == ProgramTaskTemplates.Kind.ELECTROMASTER_RECHARGE_LOW_BATTERY) set(target, Blocks.STONE.defaultBlockState());
                if (kind == ProgramTaskTemplates.Kind.DARKMATTER_FILTERED_BLOCK) set(target, Blocks.DIRT.defaultBlockState());
                if (kind == ProgramTaskTemplates.Kind.MELTDOWNER_LOCKED_MINING) set(sightBlock(20), Blocks.STONE.defaultBlockState());
                if (kind == ProgramTaskTemplates.Kind.TELEPORT_CHECKED_FORWARD) {
                    set(BlockPos.containing(center.add(0, 0, 3)), Blocks.STONE.defaultBlockState());
                    set(BlockPos.containing(center.add(0, 1, 3)), Blocks.STONE.defaultBlockState());
                }
                var before = player.position(); float cp = cp();
                var program = recipe(kind);
                var invocation = invocation(program);
                boolean successful = execute(program, invocation);
                check(successful, kind + " negative path must complete normally");
                assertBranch(invocation, "false");
                check(player.position().distanceToSqr(before) < 1E-10, "negative path moved the player: " + kind);
                check(cp() == cp, "negative path charged CP: " + kind);
                record(kind.name().toLowerCase(Locale.ROOT) + "_negative", invocation, true);
                checks.add(kind + ": actual queries took the false branch without an action or CP cost");
                closePeer(); clearBlocks();
            }
        }

        void positiveCases() {
            var teleport = ProgramTaskTemplates.Kind.TELEPORT_CHECKED_FORWARD;
            open(teleport, "teleport-positive");
            var program = recipe(teleport); var invocation = invocation(program); float before = cp();
            check(execute(program, invocation), "checked forward teleport must be accepted in clear space");
            assertBranch(invocation, "true");
            check(peer.player().position().distanceToSqr(center.add(0, 0, 3)) < 1E-8, "clear checked teleport did not reach the sampled destination");
            check(cp() < before, "successful teleport must retain actual CP charging");
            record("teleport_positive", invocation, true); checks.add("Clear forward destination teleported through the real transaction and consumed CP");
            closePeer();

            var disassemble = ProgramTaskTemplates.Kind.DARKMATTER_FILTERED_BLOCK;
            open(disassemble, "disassemble-positive");
            var target = sightBlock(4); set(target, Blocks.STONE.defaultBlockState());
            program = recipe(disassemble); invocation = invocation(program); before = cp();
            check(execute(program, invocation), "matching stone must be disassembled"); assertBranch(invocation, "true");
            check(level.getBlockState(target).isAir(), "real disassembly did not destroy its selected stone");
            check(cp() < before, "real disassembly did not charge CP");
            record("darkmatter_positive", invocation, true); checks.add("Matching stone was destroyed by the real Darkmatter action and consumed CP");
            closePeer(); clearBlocks();

            open(disassemble, "disassemble-insufficient-cp"); set(target, Blocks.STONE.defaultBlockState());
            AbilitySystemServer.getSystem(peer.player()).setPlayerAvailableCP(peer.player().getUUID(), 0);
            program = recipe(disassemble); invocation = invocation(program);
            check(!execute(program, invocation), "zero CP must reject a matching target"); assertBranch(invocation, "true");
            check(level.getBlockState(target).is(Blocks.STONE), "insufficient CP destroyed a block");
            check(cp() == 0, "insufficient CP changed the balance");
            record("darkmatter_insufficient_cp", invocation, true); checks.add("Matching target passed the graph condition but the actual CP check rejected destruction");
            closePeer(); clearBlocks();
        }

        void sampleAcrossWait() {
            var kind = ProgramTaskTemplates.Kind.DARKMATTER_FILTERED_BLOCK;
            open(kind, "sample-wait");
            var original = sightBlock(4); var next = BlockPos.containing(center.add(-4, peer.player().getEyeHeight(), 0));
            set(original, Blocks.STONE.defaultBlockState()); set(next, Blocks.STONE.defaultBlockState());
            var program = addWaitAfterSample(recipe(kind)); var invocation = invocation(program);
            check(execute(program, invocation), "sampled WAIT graph must schedule");
            var key = new ServerProgramScheduler.SessionKey(peer.player().getUUID(), kind.category(), program.id(), 0);
            check(ServerProgramScheduler.contains(level.getServer(), key), "actual server scheduler did not retain the WAIT graph");
            peer.player().setYRot(90); peer.player().setXRot(0); peer.player().setYHeadRot(90);
            check(new ServerProgramTargetResolver(peer.player(), 32, 128).lookBlockTarget().orElseThrow().x() == next.getX(),
                    "fixture must actually change the live crosshair target");
            helper.runAfterDelay(4, () -> guarded(() -> {
                check(!ServerProgramScheduler.contains(level.getServer(), key), "WAIT graph did not complete on natural server ticks");
                check(level.getBlockState(original).isAir(), "latched target was not used after WAIT");
                check(level.getBlockState(next).is(Blocks.STONE), "WAIT re-queried and destroyed the new crosshair target");
                assertBranch(invocation, "true"); record("sample_wait_crosshair_changed", invocation, true);
                checks.add("Natural WAIT resumed with the original sampled block set after the live crosshair moved to another stone");
                closePeer(); clearBlocks(); finish();
            }));
        }

        void mentalNoOpPreservesControl() {
            var seedKind = ProgramTaskTemplates.Kind.MENTALOUT_MENTAL_STUPOR;
            open(seedKind, "mental-preserve-no-op");
            var player = peer.player(); var system = AbilitySystemServer.getSystem(player);
            mentalSubject = Objects.requireNonNull(EntityTypes.ZOMBIE.create(level, EntitySpawnReason.COMMAND));
            mentalSubject.setUUID(UUID.nameUUIDFromBytes("advanced-task-mental-subject".getBytes(StandardCharsets.UTF_8)));
            mentalSubject.snapTo(center.add(0, 0, 4)); mentalSubject.setNoGravity(true); mentalSubject.setPersistenceRequired();
            check(level.addFreshEntity(mentalSubject), "fixture mental subject did not enter the real world");
            check(new ServerProgramTargetResolver(player, 64, 128).lookTarget().orElseThrow() == mentalSubject,
                    "fixture did not actually aim at the living mental subject");
            var seed = recipe(seedKind); var definition = AbilityProgramDefinitions.require(seedKind.category());
            var compiled = definition.compile(seed, ProgramTaskTemplates.requiredCapabilities(definition, seedKind));
            check(compiled.valid(), "real prior mental action did not compile");
            var seedInvocation = invocation(seed);
            var started = PrecisionOperationRuntime.execute(player, 0, compiled.program(), false, 1, seedInvocation);
            lastExecution = started; lastInvocation = seedInvocation;
            check(started.state() == PrecisionOperationRuntime.ExecutionState.STARTED,
                    "prior real mental control did not start: " + started);
            var beforeLease = MentalControlRuntime.inspect(mentalSubject, ControlCapability.FREEZE_AI).orElseThrow();
            check(beforeLease.controllerId().equals(player.getUUID()), "real prior lease belongs to another controller");
            check(MentaloutControlContext.subjects(player).isEmpty(), "independent stupor unexpectedly populated the intervention roster");
            float occupied = system.getPlayerOccupiedCP(player.getUUID()), available = cp();
            check(occupied > 0, "prior real mental action must have a measurable CP occupation");

            var noOp = recipe(ProgramTaskTemplates.Kind.MENTALOUT_ROSTER_GUARD); var noOpInvocation = invocation(noOp);
            check(execute(noOp, noOpInvocation), "same-slot explicit false/STOP must complete without replacing the prior control");
            assertBranch(noOpInvocation, "false");
            var afterLease = MentalControlRuntime.inspect(mentalSubject, ControlCapability.FREEZE_AI).orElseThrow();
            check(beforeLease.equals(afterLease), "same-slot no-op replaced, shortened or closed the actual prior lease");
            check(system.getPlayerOccupiedCP(player.getUUID()) == occupied && cp() == available,
                    "same-slot no-op changed existing CP occupation or available CP");
            cases.add(Map.of("name", "mental_no_op_preserves_same_slot_control", "passed", true,
                    "seed_execution", started, "execution", lastExecution, "prior_lease", beforeLease,
                    "retained_lease", afterLease, "occupied_cp", occupied, "available_cp", available,
                    "flow_trace", noOpInvocation.runTrace().steps()));
            checks.add("Real same-slot stupor lease and CP occupation survived the empty-roster false/STOP graph unchanged");
            closePeer();
            check(MentalControlRuntime.inspect(mentalSubject, ControlCapability.FREEZE_AI).isEmpty(),
                    "fixture cleanup failed to release the original real mental control");
            mentalSubject.discard(); mentalSubject = null;
        }

        void open(ProgramTaskTemplates.Kind kind, String name) {
            check(peer == null, "fixture peer still owned");
            peer = CapturedProgramPlayer.create(level, UUID.nameUUIDFromBytes(("advanced-task-" + name).getBytes(StandardCharsets.UTF_8)),
                    "task-peer", center);
            var player = peer.player(); player.setGameMode(GameType.SURVIVAL);
            player.noPhysics = true; player.setNoGravity(true); player.setYRot(0); player.setXRot(0); player.setYHeadRot(0);
            var system = AbilitySystemServer.getSystem(player);
            system.setPlayerAbilityCategory(player.getUUID(), category(kind)); system.setPlayerLevel(player.getUUID(), 5);
            system.setPlayerBaseMaxCP(player.getUUID(), 100_000); system.setPlayerAvailableCP(player.getUUID(), 100_000);
            system.setPlayerStatus(player.getUUID(), org.academy.api.common.data.AbilityData.Status.NORMAL);
            for (var capability : ProgramTaskTemplates.requiredCapabilities(AbilityProgramDefinitions.require(kind.category()), kind)) {
                var skill = org.academy.api.common.registries.Registries.SKILLS.get(capability).orElseThrow().value();
                system.addPlayerSkill(player, skill.getKeyString());
                if (!skill.isEnabled(player)) system.toggleSkill(player.getUUID(), skill.getKeyString());
                check(skill.isEnabled(player), "fixture did not really learn/enable " + capability);
            }
            peer.drain();
        }

        AbilityProgram recipe(ProgramTaskTemplates.Kind kind) {
            var definition = AbilityProgramDefinitions.require(kind.category());
            return ProgramTaskTemplates.create(new AbilityProgram(1, UUID.randomUUID(), kind.name(), kind.category(),
                    ProgramGraph.EMPTY, ProgramEditorLayout.EMPTY), definition, ProgramTaskTemplates.requiredCapabilities(definition, kind), kind);
        }
        boolean execute(AbilityProgram program, ProgramInvocationContext invocation) {
            lastInvocation = invocation; lastExecution = null;
            var definition = AbilityProgramDefinitions.require(program.category());
            var capabilities = new HashSet<Identifier>();
            program.graph().nodes().forEach(node -> capabilities.addAll(definition.nodeLookup().find(node.type()).scope().requiredCapabilities()));
            var compiled = definition.compile(program, capabilities);
            check(compiled.valid(), "real execution recipe did not compile: " + compiled.diagnostics());
            var player = peer.player();
            return switch (program.category().getPath()) {
                case "accelerator" -> {
                    var result = AcceleratorProgramExecutionBridge.executeServer(compiled.program(), player, 1, invocation);
                    lastExecution = executionDetails(result.vmResult(), result.transactionResult()); yield result.successful();
                }
                case "electromaster" -> {
                    var result = ElectromasterProgramExecutionBridge.executeServer(compiled.program(), player, 1, invocation);
                    lastExecution = executionDetails(result.vmResult(), result.transactionResult()); yield result.successful();
                }
                case "aeromanip" -> {
                    var result = AeromanipProgramExecutionBridge.executeServer(compiled.program(), player, 1, invocation);
                    lastExecution = executionDetails(result.vmResult(), result.transactionResult()); yield result.successful();
                }
                case "teleport" -> {
                    var result = TeleportProgramExecutionBridge.executeServer(compiled.program(), player, 1, invocation);
                    lastExecution = executionDetails(result.vmResult(), result.transactionResult()); yield result.successful();
                }
                case "meltdowner" -> {
                    var result = MeltdownerProgramExecutionBridge.executeServer(compiled.program(), player, 1, invocation);
                    lastExecution = executionDetails(result.vmResult(), result.transactionResult()); yield result.successful();
                }
                case "darkmatter" -> {
                    var result = DarkmatterProgramExecutionBridge.executeServer(compiled.program(), player, 1, invocation);
                    lastExecution = executionDetails(result.vmResult(), result.transactionResult()); yield result.successful();
                }
                case "mentalout" -> {
                    var result = PrecisionOperationRuntime.execute(player, 0, compiled.program(), false, 1, invocation);
                    lastExecution = result;
                    yield result.state() == PrecisionOperationRuntime.ExecutionState.COMPLETED;
                }
                default -> throw new IllegalArgumentException("unsupported fixture category");
            };
        }
        void assertBranch(ProgramInvocationContext invocation, String branch) {
            check(invocation.runTrace().steps().stream().anyMatch(step -> step.flowOutput().equals(branch)),
                    "expected actual " + branch + " branch, trace=" + invocation.runTrace().steps());
        }
        void record(String name, ProgramInvocationContext invocation, boolean passed) {
            cases.add(Map.of("name", name, "passed", passed, "initial_execution_result", lastExecution,
                    "flow_trace", invocation.runTrace().steps()));
        }
        Map<String, Object> executionDetails(ProgramVmResult vm, Optional<ProgramActionTransaction.Result> transaction) {
            var details = new LinkedHashMap<String, Object>(); details.put("vm", vm);
            details.put("transaction_present", transaction.isPresent());
            transaction.ifPresent(result -> {
                var settled = new LinkedHashMap<String, Object>(); settled.put("successful", result.successful());
                settled.put("state", result.state()); settled.put("phase", result.phase()); settled.put("node_id", result.nodeId());
                settled.put("rollback_failures", result.rollbackFailures());
                var chain = new ArrayList<String>();
                for (var cause = result.cause(); cause != null && chain.size() < 4; cause = cause.getCause()) chain.add(cause.toString());
                settled.put("causes", chain); details.put("transaction", settled);
            });
            return details;
        }
        float cp() { return AbilitySystemServer.getSystem(peer.player()).getPlayerAvailableCP(peer.player().getUUID()); }
        BlockPos sightBlock(int distance) { return BlockPos.containing(center.add(0, peer.player().getEyeHeight(), distance)); }
        void set(BlockPos pos, BlockState state) {
            restored.putIfAbsent(pos.immutable(), level.getBlockState(pos)); level.setBlock(pos, state, 3);
        }
        void clearBlocks() { restored.forEach((pos, state) -> level.setBlock(pos, state, 3)); restored.clear(); }
        void closePeer() {
            if (peer == null) return;
            ServerProgramScheduler.cancelOwner(level.getServer(), peer.player().getUUID());
            PrecisionOperationRuntime.releaseController(peer.player()); peer.close(); peer = null;
        }
        void cleanup() {
            closePeer(); clearBlocks();
            if (mentalSubject != null) { mentalSubject.discard(); mentalSubject = null; }
            var storage = level.getDataStorage().computeIfAbsent(TicketStorage.TYPE);
            for (var chunk : tickets) storage.removeTicket(chunk.pack(), new Ticket(NativeStructureTickets.ACTIVE.get(), NativeStructureTickets.TICKET_LEVEL));
            tickets.clear();
        }
        void finish() {
            finished = true; write(true, null); cleanup(); helper.succeed();
        }
        void guarded(Runnable action) {
            if (finished) return;
            try { action.run(); }
            catch (Throwable failure) {
                finished = true; try { write(false, failure); } finally { cleanup(); }
                helper.fail("Advanced editable tasks: " + failure);
            }
        }
        void write(boolean passed, Throwable failure) {
            var report = new LinkedHashMap<String, Object>();
            report.put("passed", passed); report.put("checks", checks); report.put("cases", cases);
            report.put("scope", List.of("Actual registered fixture ServerPlayer, production category execution bridges, world queries, transactions and natural WAIT scheduler",
                    "Seven negative branches; real teleport and block disassembly success; CP rejection; edited graph WAIT target snapshot; same-slot real mental lease and CP preservation",
                    "Three structure-control templates exercise empty-selection exits, not positive native/Sable physical group control",
                    "Fixture players/chunks/blocks are isolated; no real user save is touched"));
            if (failure != null) {
                report.put("failure", failure.toString());
                if (lastExecution != null) report.put("last_execution_result", lastExecution);
                if (lastInvocation != null) report.put("last_flow_trace", lastInvocation.runTrace().steps());
            }
            try { Files.writeString(level.getServer().getWorldPath(LevelResource.ROOT).resolve("precision-advanced-tasks.json"),
                    new GsonBuilder().setPrettyPrinting().create().toJson(report) + "\n"); }
            catch (java.io.IOException io) { throw new IllegalStateException(io); }
        }
    }

    private static ProgramInvocationContext invocation(AbilityProgram program) {
        return new ProgramInvocationContext(program.id(), 0, null, null, 0, null, null, null);
    }
    private static AbilityProgram addWaitAfterSample(AbilityProgram program) {
        var sample = program.graph().nodes().stream().filter(node -> node.type().equals(CommonProgramNodeIds.SAMPLE_VALUE)).findFirst().orElseThrow();
        var edge = program.graph().edges().stream().filter(value -> value.from().equals(new ProgramGraph.Endpoint(sample.id(), "flow"))).findFirst().orElseThrow();
        int waitId = program.graph().nodes().stream().mapToInt(ProgramGraph.Node::id).max().orElseThrow() + 1;
        var nodes = new ArrayList<>(program.graph().nodes()); var edges = new ArrayList<>(program.graph().edges());
        var config = new JsonObject(); config.addProperty("type", "integer"); config.addProperty("value", "2");
        nodes.add(new ProgramGraph.Node(waitId, CommonProgramNodeIds.WAIT, 1, new JsonObject()));
        nodes.add(new ProgramGraph.Node(waitId + 1, CommonProgramNodeIds.SCALAR_CONSTANT, 1, config));
        edges.remove(edge); edges.add(new ProgramGraph.Edge(edge.from(), new ProgramGraph.Endpoint(waitId, "flow")));
        edges.add(new ProgramGraph.Edge(new ProgramGraph.Endpoint(waitId, "flow"), edge.to()));
        edges.add(new ProgramGraph.Edge(new ProgramGraph.Endpoint(waitId + 1, "value"), new ProgramGraph.Endpoint(waitId, "ticks")));
        return new AbilityProgram(program.schemaVersion(), program.id(), program.name(), program.category(), new ProgramGraph(nodes, edges), program.editorLayout());
    }
    private static AbilityCategory category(ProgramTaskTemplates.Kind kind) {
        return switch (kind.category().getPath()) {
            case "accelerator" -> AbilityCategories.ACCELERATOR.get(); case "electromaster" -> AbilityCategories.ELECTROMASTER.get();
            case "aeromanip" -> AbilityCategories.AEROMANIP.get(); case "teleport" -> AbilityCategories.TELEPORT.get();
            case "meltdowner" -> AbilityCategories.MELTDOWNER.get(); case "darkmatter" -> AbilityCategories.DARKMATTER.get();
            case "mentalout" -> AbilityCategories.MENTALOUT.get(); default -> throw new IllegalArgumentException();
        };
    }
    private static void check(boolean value, String detail) { if (!value) throw new AssertionError(detail); }
    private static final class Instance extends GameTestInstance {
        static final MapCodec<Instance> CODEC = TestData.CODEC.xmap(Instance::new, Instance::info);
        Instance(TestData<Holder<TestEnvironmentDefinition<?>>> info) { super(info); }
        @Override public void run(GameTestHelper helper) { new Scenario(helper).start(); }
        @Override public MapCodec<? extends GameTestInstance> codec() { return CODEC; }
        @Override protected MutableComponent typeDescription() { return Component.literal("Seven editable advanced ability tasks and real target snapshots"); }
    }
}
