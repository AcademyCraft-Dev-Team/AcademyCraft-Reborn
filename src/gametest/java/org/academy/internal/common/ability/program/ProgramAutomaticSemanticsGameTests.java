package org.academy.internal.common.ability.program;

import com.google.gson.JsonObject;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.Holder;
import net.minecraft.gametest.framework.*;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.Rotation;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterGameTestsEvent;
import net.neoforged.neoforge.registries.RegisterEvent;
import org.academy.AcademyCraft;
import org.academy.api.common.ability.program.*;
import org.academy.api.server.ability.AbilitySystemServer;
import org.academy.internal.common.ability.AbilityCategories;
import org.academy.internal.common.ability.program.registry.AbilityProgramDefinition;
import org.academy.internal.common.ability.program.registry.AbilityProgramDefinitions;
import org.academy.internal.common.ability.program.registry.CommonProgramNodeCatalog;
import org.academy.internal.common.ability.program.registry.CommonProgramNodeIds;

import java.util.*;

/** Real manager admission semantics with one registered server player and a simulated connection. */
@EventBusSubscriber(modid = AcademyCraft.MOD_ID)
public final class ProgramAutomaticSemanticsGameTests {
    private static final boolean ENABLED = Boolean.getBoolean("academy.precisionAutomaticSemantics");
    private static final Identifier TYPE = AcademyCraft.academy("precision_automatic_semantics_function");
    private ProgramAutomaticSemanticsGameTests() {}

    @SubscribeEvent
    private static void registerType(RegisterEvent event) {
        event.register(net.minecraft.core.registries.Registries.TEST_INSTANCE_TYPE, TYPE, () -> Instance.CODEC);
    }

    @SubscribeEvent
    private static void registerTests(RegisterGameTestsEvent event) {
        if (!ENABLED) return;
        var environment = event.registerEnvironment(AcademyCraft.academy("precision_automatic_semantics_environment"),
                new TestEnvironmentDefinition.AllOf(List.of()));
        event.registerTest(AcademyCraft.academy("precision_automatic_semantics"), new Instance(new TestData<>(
                environment, Identifier.withDefaultNamespace("empty"), 240, 0, true,
                Rotation.NONE, false, 1, 1, false, 16)));
    }

    private static final class Instance extends GameTestInstance {
        private static final MapCodec<Instance> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
                TestData.CODEC.forGetter(Instance::info)).apply(instance, Instance::new));
        private Instance(TestData<Holder<TestEnvironmentDefinition<?>>> info) { super(info); }
        @Override public void run(GameTestHelper helper) { new Scenario(helper).start(); }
        @Override public MapCodec<? extends GameTestInstance> codec() { return CODEC; }
        @Override protected MutableComponent typeDescription() { return Component.literal("Precision automatic semantics"); }
    }

    private static final class Peer implements AutoCloseable {
        private final ServerPlayer player;
        private final CapturedProgramPlayer capture;
        private Peer(GameTestHelper helper) {
            var level = helper.getLevel();
            var position = helper.absoluteVec(new net.minecraft.world.phys.Vec3(4, 4, 4));
            capture = CapturedProgramPlayer.create(level, UUID.randomUUID(), "precision-sema", position);
            player = capture.player();
            try {
                var system = AbilitySystemServer.getSystem(player);
                system.setPlayerAbilityCategory(player.getUUID(), AbilityCategories.ACCELERATOR.get());
                system.setPlayerLevel(player.getUUID(), 5);
                system.setPlayerStatus(player.getUUID(), org.academy.api.common.data.AbilityData.Status.NORMAL);
                if (system.getPlayerData(player.getUUID()) == null || system.getPlayerLevel(player.getUUID()) != 5) {
                    throw new IllegalStateException("Real ability data was not admitted");
                }
                drain();
            } catch (RuntimeException | Error failure) {
                try { capture.close(); }
                catch (RuntimeException cleanup) { failure.addSuppressed(cleanup); }
                throw failure;
            }
        }
        private List<AbilityProgramManager.ResultPacket> drain() {
            return capture.drain().stream().map(CapturedProgramPlayer.Captured::packet)
                    .filter(AbilityProgramManager.ResultPacket.class::isInstance)
                    .map(AbilityProgramManager.ResultPacket.class::cast).toList();
        }
        @Override public void close() { capture.close(); }
    }

    private static final class Scenario {
        private final GameTestHelper helper;
        private final AbilityProgramDefinition definition = AbilityProgramDefinitions.require(AcademyCraft.academy("accelerator"));
        private final int[] rotations = new int[10];
        private Peer peer;
        private List<AbilityProgram> rotating;
        private long previousTick = Long.MIN_VALUE, failureTick;
        private int rotationPulse;
        private AbilityProgram failed;
        private boolean closed;
        private Scenario(GameTestHelper helper) { this.helper = helper; }
        private void check(boolean condition, String message) { helper.assertTrue(condition, Component.literal(message)); }
        private void guarded(Runnable action) {
            try { action.run(); }
            catch (RuntimeException | Error failure) { close(); throw failure; }
        }
        private void after(long delay, Runnable action) { helper.runAfterDelay(delay, () -> guarded(action)); }
        private void start() { guarded(() -> {
            peer = new Peer(helper);
            check(helper.getLevel().getServer().getPlayerList().getPlayer(peer.player.getUUID()) == peer.player,
                    "Peer must be registered in PlayerList");
            rotating = new ArrayList<>();
            for (int slot = 0; slot < 10; slot++) rotating.add(counter("rotate_" + slot, CommonProgramNodeIds.TRIGGER_LOOP));
            rotation();
        }); }
        private void rotation() {
            long now = peer.player.level().getGameTime();
            check(previousTick == Long.MIN_VALUE || now > previousTick, "Each rotation pulse must use a new real world tick");
            previousTick = now;
            install(rotating);
            AbilityProgramManager.executeTriggered(peer.player, ProgramTriggers.Type.LOOP, null);
            install(List.of());
            for (int admission = 0; admission < 4; admission++) rotations[(rotationPulse * 4 + admission) % 10]++;
            for (int slot = 0; slot < 10; slot++) check(read("rotate_" + slot) == rotations[slot],
                    "Ten-slot rotation diverged at pulse " + rotationPulse + ", slot " + slot);
            rotationPulse++; peer.drain();
            if (rotationPulse < 5) after(1, this::rotation);
            else {
                for (var count : rotations) check(count == 2, "Five ticks must give every slot two admissions");
                after(1, this::exhaustedHealth);
            }
        }
        private void exhaustedHealth() {
            var programs = new ArrayList<AbilityProgram>();
            for (int slot = 0; slot < 3; slot++) programs.add(counter("budget_loop_" + slot, CommonProgramNodeIds.TRIGGER_LOOP));
            programs.add(counter("budget_movement", CommonProgramNodeIds.TRIGGER_MOVEMENT));
            programs.add(counter("budget_health", CommonProgramNodeIds.TRIGGER_HEALTH_THRESHOLD));
            peer.player.setHealth(4);
            install(programs);
            AbilityProgramManager.executeTriggered(peer.player, ProgramTriggers.Type.MOVEMENT,
                    CommonProgramNodeCatalog.MovementCondition.JUMP);
            AbilityProgramManager.executeTriggered(peer.player, ProgramTriggers.Type.LOOP, null);
            AbilityProgramManager.executeTriggered(peer.player, ProgramTriggers.Type.HEALTH, null);
            install(List.of());
            check(read("budget_movement") == 1, "Movement must share the same tick budget");
            for (int slot = 0; slot < 3; slot++) check(read("budget_loop_" + slot) == 1, "Expected three loop admissions");
            check(read("budget_health") == 0, "Exhausted budget must not run the health graph");
            peer.drain();
            var health = programs.get(4);
            after(1, () -> {
                var slots = new ArrayList<AbilityProgram>(Collections.nCopies(5, null)); slots.set(4, health);
                install(slots);
                AbilityProgramManager.executeTriggered(peer.player, ProgramTriggers.Type.HEALTH, null);
                check(read("budget_health") == 1, "Budget exhaustion must not consume the pending health edge");
                AbilityProgramManager.executeTriggered(peer.player, ProgramTriggers.Type.HEALTH, null);
                check(read("budget_health") == 1, "A true health condition must remain latched");
                peer.player.setHealth(10);
                AbilityProgramManager.executeTriggered(peer.player, ProgramTriggers.Type.HEALTH, null);
                peer.player.setHealth(4);
                AbilityProgramManager.executeTriggered(peer.player, ProgramTriggers.Type.HEALTH, null);
                check(read("budget_health") == 2, "Equality must clear the strict-below latch and permit a new crossing");
                install(List.of()); peer.drain(); after(1, this::failureStart);
            });
        }
        private void failureStart() {
            failed = failingLoop(); failureTick = peer.player.level().getGameTime(); peer.drain();
            pulseFailure(); check(retryAt() == failureTick + 100, "Initial failure did not establish the exact 100-tick cooldown");
            after(1, () -> {
                check(peer.player.level().getGameTime() == failureTick + 1, "Expected exact first cooldown tick");
                check(failedTraces() == 1, "The initial loop failure must be observable once after normal tick flushing");
                pulseFailure();
                check(retryAt() == failureTick + 100, "A blocked retry changed the cooldown deadline");
            });
            after(2, () -> {
                check(failedTraces() == 0, "Failed loop retried before its cooldown");
            });
            after(99, () -> {
                check(peer.player.level().getGameTime() == failureTick + 99, "Expected exact final blocked tick");
                pulseFailure();
                check(retryAt() == failureTick + 100, "The final blocked tick changed the cooldown deadline");
            });
            after(100, () -> {
                check(peer.player.level().getGameTime() == failureTick + 100, "Expected exact cooldown release tick");
                check(failedTraces() == 0, "Failed loop retried at tick 99");
                pulseFailure(); check(retryAt() == failureTick + 200, "Failed loop must retry exactly at tick 100");
            });
            after(101, () -> {
                check(failedTraces() == 1, "The second failed attempt must arrive once after normal tick flushing");
                check(read("failure_sink") == 0, "Rejected data evaluation must not commit the shared write");
                close();
                check(!managerRetainsPeer("AUTOMATIC_RUN_BUDGETS") && !managerRetainsPeer("NEXT_AUTOMATIC_SLOT")
                        && !managerRetainsPeer("FAILED_LOOP_RETRY_AT") && !managerRetainsPeer("LOOP_EXECUTION_COUNTS")
                        && !managerRetainsPeer("LAST_TRACE_TICK"), "Logout left automatic runtime state");
                AcademyCraft.LOGGER.info("PRECISION_AUTOMATIC_SEMANTICS_PASSED rotation_ticks=5 admissions=20 health_budget_and_latch=true failed_retry_ticks=100 actual_server_players=1 simulated_connection=true");
                helper.succeed();
            });
        }
        private void pulseFailure() {
            install(List.of(failed));
            AbilityProgramManager.executeTriggered(peer.player, ProgramTriggers.Type.LOOP, null);
            install(List.of());
        }
        private int failedTraces() {
            var packets = peer.drain();
            AcademyCraft.LOGGER.info("PRECISION_AUTOMATIC_FAILURE_CAPTURE world_tick={} retry_at={} sink={} packets={}",
                    peer.player.level().getGameTime(), retryAt(), read("failure_sink"), packets.stream().map(packet ->
                            packet.type() + "/" + packet.code() + "/" + packet.vmDiagnostic() + "/steps=" + packet.trace().size()).toList());
            return (int) packets.stream().filter(packet -> packet.type() == AbilityProgramManager.FeedbackType.TRACE
                    && packet.code() == AbilityProgramManager.ResultCode.EXECUTION_FAILED).count();
        }
        private long retryAt() {
            try {
                var field = AbilityProgramManager.class.getDeclaredField("FAILED_LOOP_RETRY_AT"); field.setAccessible(true);
                for (var entry : ((Map<?, ?>) field.get(null)).entrySet()) {
                    var owner = entry.getKey().getClass().getDeclaredMethod("ownerId"); owner.setAccessible(true);
                    var program = entry.getKey().getClass().getDeclaredMethod("programId"); program.setAccessible(true);
                    if (owner.invoke(entry.getKey()).equals(peer.player.getUUID()) && program.invoke(entry.getKey()).equals(failed.id())) {
                        return ((Number) entry.getValue()).longValue();
                    }
                }
                return Long.MIN_VALUE;
            } catch (ReflectiveOperationException failure) { throw new IllegalStateException(failure); }
        }
        private int read(String name) {
            return ProgramSharedVariables.read(peer.player, definition.category(), name)
                    .map(value -> ((Number) value.value()).intValue()).orElse(0);
        }
        private void install(List<AbilityProgram> programs) {
            var slots = new ArrayList<ProgramBook.Slot>();
            for (int slot = 0; slot < AbilityProgramManager.SLOT_COUNT; slot++) slots.add(new ProgramBook.Slot(
                    slot < programs.size() ? programs.get(slot) : null));
            var book = new ProgramBook(ProgramBook.CURRENT_SCHEMA_VERSION, 0, 0, slots);
            AbilitySystemServer.getSystem(peer.player).getPlayerData(peer.player.getUUID())
                    .setAbilityProgramBook(definition.category().toString(), AbilityProgramManager.encodeStoredBook(book));
        }
        private AbilityProgram counter(String name, Identifier trigger) {
            var config = new JsonObject(); config.addProperty("name", name); config.addProperty("type", ProgramValueTypes.INTEGER.id().toString());
            var one = new JsonObject(); one.addProperty("type", "integer"); one.addProperty("value", "1");
            var add = new JsonObject(); add.addProperty("type", "integer"); add.addProperty("operator", "add");
            var nodes = List.of(node(0, trigger, triggerConfig(trigger)), node(1, CommonProgramNodeIds.SHARED_VARIABLE_GET, config),
                    node(2, CommonProgramNodeIds.SCALAR_CONSTANT, one), node(3, CommonProgramNodeIds.NUMERIC_ARITHMETIC, add),
                    node(4, CommonProgramNodeIds.SHARED_VARIABLE_SET, config));
            return program(name, nodes, List.of(edge(0,"flow",4,"flow"), edge(1,"value",3,"left"),
                    edge(2,"value",3,"right"), edge(3,"result",4,"value")));
        }
        private AbilityProgram failingLoop() {
            var program = counter("failure_sink", CommonProgramNodeIds.TRIGGER_LOOP);
            var nodes = new ArrayList<>(program.graph().nodes());
            var operation = nodes.get(3).configuration().getAsJsonObject(); operation.addProperty("operator", "divide");
            nodes.set(3, node(3, CommonProgramNodeIds.NUMERIC_ARITHMETIC, operation));
            var local = new JsonObject(); local.addProperty("name", "before_failure");
            local.addProperty("type", ProgramValueTypes.INTEGER.id().toString());
            nodes.add(node(5, CommonProgramNodeIds.VARIABLE_SET, local));
            // The shared value starts at zero. Divide 1 by that value so every admitted attempt fails.
            // A VM-local write guarantees a preceding successful flow step, without world or shared effects.
            var edges = List.of(edge(0,"flow",5,"flow"), edge(2,"value",5,"value"), edge(5,"flow",4,"flow"), edge(2,"value",3,"left"),
                    edge(1,"value",3,"right"), edge(3,"result",4,"value"));
            return program("failure_sink", nodes, edges);
        }
        private JsonObject triggerConfig(Identifier trigger) {
            var config = new JsonObject();
            if (trigger.equals(CommonProgramNodeIds.TRIGGER_LOOP)) { config.addProperty("interval", 1); config.addProperty("enabled", true); }
            if (trigger.equals(CommonProgramNodeIds.TRIGGER_MOVEMENT)) config.addProperty("condition", "jump");
            if (trigger.equals(CommonProgramNodeIds.TRIGGER_HEALTH_THRESHOLD)) { config.addProperty("mode", "below"); config.addProperty("threshold", 10.0); }
            return config;
        }
        private AbilityProgram program(String name, List<ProgramGraph.Node> nodes, List<ProgramGraph.Edge> edges) {
            var result = new AbilityProgram(AbilityProgram.CURRENT_SCHEMA_VERSION, UUID.randomUUID(), name,
                    definition.category(), new ProgramGraph(nodes, edges), ProgramEditorLayout.EMPTY);
            var compiled = definition.compile(result, Set.of());
            check(compiled.valid(), "Semantic probe must be an ordinary valid graph: " + compiled.diagnostics());
            return result;
        }
        private ProgramGraph.Node node(int id, Identifier type, JsonObject override) {
            var entry = Objects.requireNonNull(definition.editorCatalog().entry(type));
            var config = entry.defaultConfiguration().getAsJsonObject();
            override.entrySet().forEach(field -> config.add(field.getKey(), field.getValue().deepCopy()));
            return new ProgramGraph.Node(id, type, entry.type().schemaVersion(), config);
        }
        private boolean managerRetainsPeer(String name) {
            try {
                var field = AbilityProgramManager.class.getDeclaredField(name); field.setAccessible(true);
                for (var key : ((Map<?, ?>) field.get(null)).keySet()) {
                    if (key.equals(peer.player.getUUID())) return true;
                    if (key.getClass().isRecord()) {
                        var owner = key.getClass().getDeclaredMethod("ownerId"); owner.setAccessible(true);
                        if (owner.invoke(key).equals(peer.player.getUUID())) return true;
                    }
                }
                return false;
            } catch (ReflectiveOperationException failure) { throw new IllegalStateException(failure); }
        }
        private void close() {
            if (closed) return; closed = true;
            if (peer != null) {
                try { install(List.of()); ProgramSharedVariables.clear(peer.player, definition.category()); }
                finally { peer.close(); }
            }
        }
    }
    private static ProgramGraph.Edge edge(int from, String output, int to, String input) {
        return new ProgramGraph.Edge(new ProgramGraph.Endpoint(from, output), new ProgramGraph.Endpoint(to, input));
    }
}
