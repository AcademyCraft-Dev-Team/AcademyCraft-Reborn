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
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.portal.TeleportTransition;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.storage.LevelResource;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterGameTestsEvent;
import net.neoforged.neoforge.registries.RegisterEvent;
import org.academy.AcademyCraft;
import org.academy.api.common.ability.program.*;
import org.academy.api.server.ability.AbilitySystemServer;
import org.academy.api.server.ability.program.AbilityProgramService;
import org.academy.internal.common.ability.AbilityCategories;
import org.academy.internal.common.ability.program.registry.AbilityProgramDefinitions;
import org.academy.internal.common.ability.program.registry.CommonProgramNodeIds;

import java.nio.file.Files;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;

/** Actual saved manager execution, natural resume, settlement and outbound status packets. */
@EventBusSubscriber(modid = AcademyCraft.MOD_ID)
public final class ProgramRunStatusGameTests {
    private static final Identifier TYPE = AcademyCraft.academy("precision_run_status_function");
    private ProgramRunStatusGameTests() {}
    @SubscribeEvent private static void registerType(RegisterEvent event) {
        event.register(Registries.TEST_INSTANCE_TYPE, TYPE, () -> Instance.CODEC);
    }
    @SubscribeEvent private static void registerTests(RegisterGameTestsEvent event) {
        if (!Boolean.getBoolean("academy.precisionRunStatusSmoke")) return;
        var environment = event.registerEnvironment(TYPE, new TestEnvironmentDefinition.AllOf(List.of()));
        event.registerTest(AcademyCraft.academy("precision_run_status"), new Instance(new TestData<>(environment,
                Identifier.withDefaultNamespace("empty"), 900, 0, true, Rotation.NONE, false, 1, 1, false, 16)));
    }

    private static final class Scenario {
        final GameTestHelper helper;
        final List<ProgramRunStatus> received = new ArrayList<>();
        final Map<String, Object> checks = new LinkedHashMap<>();
        CapturedProgramPlayer peer;
        ServerPlayer player;
        AbilityProgram current;
        ProgramRunStatus.Target target;
        UUID token;
        long revision;
        String phase = "prepare";
        boolean closed, finished;
        Scenario(GameTestHelper helper) { this.helper = helper; }
        void start() { guarded(() -> {
            peer = CapturedProgramPlayer.create(helper.getLevel(), UUID.randomUUID(), "status-peer",
                    helper.absoluteVec(new Vec3(4.5, 5, 4.5)));
            player = peer.player();
            var ability = AbilitySystemServer.getSystem(player);
            ability.setPlayerAbilityCategory(player.getUUID(), AbilityCategories.ACCELERATOR.get());
            ability.setPlayerLevel(player.getUUID(), 5);
            ability.setPlayerBaseMaxCP(player.getUUID(), 1_000_000);
            ability.setPlayerAvailableCP(player.getUUID(), 1_000_000);
            peer.drain();
            install(program(30, true));
            phase = "unsubscribed_run";
            check(AbilityProgramService.execute(player, 0, current).status() == AbilityProgramService.Status.DEFERRED, "real saved WAIT must defer");
            after(3, this::observeMidRun);
        }); }
        void observeMidRun() {
            check(drain().isEmpty(), "unsubscribed manager execution sent a detailed run status");
            checks.put("unsubscribed_no_status_packet", true);
            token = UUID.randomUUID();
            var wrong = new ProgramRunStatus.Target(Identifier.withDefaultNamespace("the_nether"), target.category(), 0, current.id(), target.bookRevision());
            check(!ProgramRunObservations.subscribe(player, token, wrong), "wrong dimension must not subscribe");
            check(ProgramRunObservations.subscribe(player, token, target), "mid-run subscription rejected");
            phase = "midrun_wait"; after(8, this::cancel);
        }
        void cancel() {
            var statuses = drain();
            check(statuses.stream().anyMatch(s -> s.phase() == ProgramRunStatus.Phase.WAITING && s.waitKind() == ProgramRunStatus.WaitKind.WAIT
                    && s.sourceNodeId() == 2 && s.nodeId() == 4 && s.wakeAtLogicalTick() > s.logicalTick()), "mid-run attach lost actual WAIT/source node");
            check(ProgramSharedVariables.read(player, current.category(), "status_committed").isEmpty(), "WAIT committed downstream work early");
            checks.put("midrun_attach_wait_source_and_no_early_commit", true);
            AbilityProgramService.cancel(player, 0, current.id());
            phase = "cancelled"; after(8, this::restart);
        }
        void restart() {
            var statuses = drain();
            check(statuses.stream().anyMatch(s -> s.phase() == ProgramRunStatus.Phase.CANCELLED), "cancel terminal missing");
            check(statuses.stream().noneMatch(s -> s.phase() == ProgramRunStatus.Phase.COMPLETED), "cancel was reported completed");
            check(ProgramSharedVariables.read(player, current.category(), "status_committed").isEmpty(), "cancel applied downstream transaction");
            checks.put("cancel_after_abort_without_downstream_write", true);
            check(AbilityProgramService.execute(player, 0, current).accepted(), "second run rejected");
            phase = "completed"; after(40, this::commitFailure);
        }
        void commitFailure() {
            var statuses = drain();
            check(statuses.stream().anyMatch(s -> s.phase() == ProgramRunStatus.Phase.COMPLETED), "committed run terminal missing");
            check((Integer) ProgramSharedVariables.read(player, current.category(), "status_committed").orElseThrow().value() == 1, "real transaction output missing");
            var oldRuns = received.stream().filter(s -> s.phase() == ProgramRunStatus.Phase.CANCELLED).mapToLong(ProgramRunStatus::runSequence).max().orElseThrow();
            var newRun = statuses.stream().filter(s -> s.phase() == ProgramRunStatus.Phase.COMPLETED).mapToLong(ProgramRunStatus::runSequence).max().orElseThrow();
            check(newRun > oldRuns, "run identity did not advance after cancellation");
            checks.put("new_run_commits_and_replaces_old_terminal", true);
            install(program(0, false)); token = UUID.randomUUID();
            check(ProgramRunObservations.subscribe(player, token, target), "commit failure subscription rejected");
            var definition = AbilityProgramDefinitions.require(current.category());
            var compiled = definition.compile(current, Set.of()); check(compiled.valid(), "commit failure ordinary graph invalid");
            var invocation = new ProgramInvocationContext(current.id(), 0, null, null, 0, null, null, null);
            ProgramRunObservations.begin(player, current.category(), 0, current.id(), target.bookRevision(), invocation);
            var transaction = new ProgramActionTransaction(); var applied = new AtomicInteger();
            transaction.stage(4, () -> { applied.incrementAndGet(); throw new IllegalStateException("fixture commit rejection"); });
            var result = ServerProgramExecution.execute(compiled.program(), player, current.category(), 1000, definition.executors(),
                    new ProgramExecutionFrame(transaction, new ServerProgramTargetResolver(player, 64, 128), invocation, player.level()::getGameTime),
                    transaction, invocation);
            check(result.vmResult().status() == ProgramVmResult.Status.COMPLETED && !result.transactionResult().orElseThrow().successful(),
                    "fixture must reach VM completion then reject the actual commit");
            check(applied.get() == 1, "observation repeated transaction action");
            phase = "commit_failed"; after(8, this::expired);
        }
        void expired() {
            var statuses = drain();
            check(statuses.stream().anyMatch(s -> s.phase() == ProgramRunStatus.Phase.FAILED), "failed commit terminal missing");
            check(statuses.stream().noneMatch(s -> s.phase() == ProgramRunStatus.Phase.COMPLETED), "VM completion leaked as committed result");
            checks.put("actual_commit_failure_never_announces_completion", true);
            install(program(30, true)); token = UUID.randomUUID();
            check(ProgramRunObservations.subscribe(player, token, target), "lease subscription rejected");
            phase = "lease_expiry"; after(410, this::afterExpiry);
        }
        void afterExpiry() {
            drain();
            check(AbilityProgramService.execute(player, 0, current).accepted(), "expired observation changed execution admission");
            phase = "expired_run_no_status"; after(8, this::mentalout);
        }
        void mentalout() {
            check(drain().isEmpty(), "expired lease emitted new run status");
            AbilityProgramService.cancel(player, 0, current.id());
            checks.put("physical_server_tick_lease_expiry_does_not_cancel_execution", true);
            var ability = AbilitySystemServer.getSystem(player);
            ability.setPlayerAbilityCategory(player.getUUID(), AbilityCategories.MENTALOUT.get());
            ability.setPlayerLevel(player.getUUID(), 5);
            install(program(10, false)); token = UUID.randomUUID();
            check(ProgramRunObservations.subscribe(player, token, target), "mentalout subscription rejected");
            check(AbilityProgramService.execute(player, 0, current).accepted(), "mentalout no-action WAIT rejected");
            phase = "mentalout_settlement"; after(18, this::logout);
        }
        void logout() {
            check(drain().stream().anyMatch(s -> s.phase() == ProgramRunStatus.Phase.COMPLETED), "mentalout completion missing after real no-op settlement");
            checks.put("mentalout_deferred_real_no_action_settlement", true);
            install(program(30, false)); token = UUID.randomUUID();
            check(ProgramRunObservations.subscribe(player, token, target), "dimension-change subscription rejected");
            check(AbilityProgramService.execute(player, 0, current).accepted(), "dimension-change WAIT rejected");
            phase = "actual_dimension_change";
            after(8, this::changeDimension);
        }
        void changeDimension() {
            check(drain().stream().anyMatch(s -> s.phase() == ProgramRunStatus.Phase.WAITING), "dimension fixture did not observe original wait");
            var destination = helper.getLevel().getServer().getLevel(Level.NETHER);
            check(destination != null, "actual dimension-change regression needs the server's Nether");
            var home = player.level(); var position = player.position();
            player.noPhysics = true;
            check(player.teleport(new TeleportTransition(destination, new Vec3(16.5, destination.getMaxY() - 8, 16.5), Vec3.ZERO,
                    0, 0, TeleportTransition.DO_NOTHING)) == player, "actual player dimension transfer failed");
            player.connection.markClientLoaded();
            check(!player.level().dimension().identifier().equals(target.dimension()), "player did not leave the observed dimension");
            var key = new ServerProgramScheduler.SessionKey(player.getUUID(), current.category(), current.id(), 0);
            boolean runningBeforeFlush = ServerProgramScheduler.contains(home.getServer(), key);
            var sent = new ArrayList<ProgramRunStatus>();
            ProgramRunObservations.flush(home.getServer(), (_, status) -> sent.add(status));
            check(sent.isEmpty(), "old-dimension subscription emitted after actual transfer");
            check(ServerProgramScheduler.contains(home.getServer(), key) == runningBeforeFlush,
                    "observation invalidation changed the execution session");
            check(trackerTarget() == null, "actual dimension transfer retained the old subscription");
            after(8, () -> {
                drain(); // Already queued old-dimension packets are client-filtered by target/token.
                check(trackerTarget() == null, "natural tick recreated an invalidated observation");
                check(player.teleport(new TeleportTransition(home, position, Vec3.ZERO, 0, 0, TeleportTransition.DO_NOTHING)) == player,
                        "fixture player could not return to original dimension");
                player.connection.markClientLoaded();
                AbilityProgramService.cancel(player, 0, current.id());
                checks.put("actual_dimension_transfer_stops_observation_without_cancelling_execution", true);
                prepareLogout();
            });
        }
        void prepareLogout() {
            install(program(30, false)); token = UUID.randomUUID();
            check(ProgramRunObservations.subscribe(player, token, target), "logout subscription rejected");
            check(AbilityProgramService.execute(player, 0, current).accepted(), "logout WAIT rejected");
            phase = "logout_cleanup"; after(8, this::closeAndVerify);
        }
        void closeAndVerify() {
            check(drain().stream().anyMatch(s -> s.phase() == ProgramRunStatus.Phase.WAITING), "logout fixture never observed live wait");
            var owner = player.getUUID(); var key = new ServerProgramScheduler.SessionKey(owner, current.category(), current.id(), 0);
            peer.close(); closed = true;
            check(!ServerProgramScheduler.contains(helper.getLevel().getServer(), key), "logout retained actual VM session");
            check(trackerTarget() == null, "logout retained status subscription");
            checks.put("actual_logout_clears_subscription_and_session", true);
            write(true, null); finished = true; helper.succeed();
        }
        private ProgramRunStatus.Target trackerTarget() {
            try {
                var field = ProgramRunObservations.class.getDeclaredField("TRACKERS"); field.setAccessible(true);
                var trackers = (Map<?, ?>) field.get(null);
                var tracker = (ProgramRunStatusTracker) trackers.get(helper.getLevel().getServer());
                return tracker == null ? null : tracker.target(player.getUUID(), Integer.toUnsignedLong(helper.getLevel().getServer().getTickCount()));
            } catch (ReflectiveOperationException failure) { throw new IllegalStateException(failure); }
        }

        private AbilityProgram program(int wait, boolean sharedWrite) {
            var category = AbilitySystemServer.getSystem(player).getPlayerAbilityCategory(player.getUUID()).getKey();
            var nodes = new ArrayList<ProgramGraph.Node>(); var edges = new ArrayList<ProgramGraph.Edge>();
            nodes.add(node(1, CommonProgramNodeIds.TRIGGER_MOVEMENT, "{\"condition\":\"jump\"}"));
            if (wait > 0) {
                nodes.add(node(2, CommonProgramNodeIds.WAIT, "{}"));
                nodes.add(node(3, CommonProgramNodeIds.SCALAR_CONSTANT, "{\"type\":\"integer\",\"value\":\"" + wait + "\"}"));
                edges.add(edge(1, "flow", 2, "flow")); edges.add(edge(3, "value", 2, "ticks"));
            }
            if (sharedWrite) {
                nodes.add(node(4, CommonProgramNodeIds.SHARED_VARIABLE_SET, "{\"name\":\"status_committed\",\"type\":\"" + ProgramValueTypes.INTEGER.id() + "\"}"));
                nodes.add(node(5, CommonProgramNodeIds.SCALAR_CONSTANT, "{\"type\":\"integer\",\"value\":\"1\"}"));
                edges.add(edge(5, "value", 4, "value"));
            } else nodes.add(node(4, CommonProgramNodeIds.STOP, "{}"));
            edges.add(edge(wait > 0 ? 2 : 1, "flow", 4, "flow"));
            return new AbilityProgram(1, UUID.randomUUID(), "Run status fixture", category, new ProgramGraph(nodes, edges), ProgramEditorLayout.EMPTY);
        }
        private void install(AbilityProgram program) {
            ProgramRunObservations.invalidate(player);
            current = program; revision++;
            var base = ProgramBook.empty(10).replaceSlot(0, program);
            var book = new ProgramBook(1, revision, 0, base.slots());
            AbilitySystemServer.getSystem(player).getPlayerData(player.getUUID()).setAbilityProgramBook(program.category().toString(), AbilityProgramManager.encodeStoredBook(book));
            target = new ProgramRunStatus.Target(player.level().dimension().identifier(), program.category(), 0, program.id(), revision);
            drain();
        }
        private List<ProgramRunStatus> drain() {
            var statuses = new ArrayList<ProgramRunStatus>();
            for (var captured : peer.drain()) if (captured.packet() instanceof AbilityProgramManager.RunStatusPacket packet) {
                check(captured.payloadBytes() <= ProgramRunStatus.MAX_PACKET_BYTES, "status packet exceeded 1 KiB");
                statuses.add(packet.status()); received.add(packet.status());
            }
            return statuses;
        }
        private void after(int ticks, Runnable action) { helper.runAfterDelay(ticks, () -> guarded(action)); }
        private void guarded(Runnable action) {
            if (finished) return;
            try { action.run(); }
            catch (Throwable failure) {
                finished = true;
                try { write(false, failure); } finally { if (peer != null && !closed) peer.close(); }
                helper.fail("Run status smoke: " + failure);
            }
        }
        private void write(boolean passed, Throwable failure) {
            var report = new LinkedHashMap<String, Object>();
            report.put("passed", passed); report.put("phase", phase); report.put("checks", checks); report.put("status_packets", received);
            report.put("scope", "One actual registered ServerPlayer, natural scheduler resume and real outbound status CODEC. Saved books installed during setup; subscriptions use server helper. Commit rejection is an injected action through production ServerProgramExecution/transaction. Not a real remote client or physical paid-action acceptance.");
            report.put("failure", failure == null ? "" : failure.toString());
            try { Files.writeString(helper.getLevel().getServer().getWorldPath(LevelResource.ROOT).resolve("precision-run-status-smoke.json"),
                    new GsonBuilder().setPrettyPrinting().create().toJson(report) + "\n"); }
            catch (java.io.IOException e) { throw new IllegalStateException(e); }
        }
    }
    private static ProgramGraph.Node node(int id, Identifier type, String json) { return new ProgramGraph.Node(id, type, 1, JsonParser.parseString(json)); }
    private static ProgramGraph.Edge edge(int a, String out, int b, String in) { return new ProgramGraph.Edge(new ProgramGraph.Endpoint(a, out), new ProgramGraph.Endpoint(b, in)); }
    private static void check(boolean value, String reason) { if (!value) throw new AssertionError(reason); }
    private static final class Instance extends GameTestInstance {
        static final MapCodec<Instance> CODEC = TestData.CODEC.xmap(Instance::new, Instance::info);
        Instance(TestData<Holder<TestEnvironmentDefinition<?>>> info) { super(info); }
        @Override public void run(GameTestHelper helper) { new Scenario(helper).start(); }
        @Override public MapCodec<? extends GameTestInstance> codec() { return CODEC; }
        @Override protected MutableComponent typeDescription() { return Component.literal("Saved program run status lifecycle"); }
    }
}
