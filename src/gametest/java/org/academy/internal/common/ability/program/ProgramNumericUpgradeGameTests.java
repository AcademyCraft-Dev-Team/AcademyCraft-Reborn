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
import net.minecraft.server.level.Ticket;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.TicketStorage;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.storage.LevelResource;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterGameTestsEvent;
import net.neoforged.neoforge.registries.RegisterEvent;
import org.academy.AcademyCraft;
import org.academy.api.common.ability.program.*;
import org.academy.api.common.data.AbilityData;
import org.academy.api.server.ability.AbilitySystemServer;
import org.academy.internal.common.ability.AbilityCategories;
import org.academy.internal.common.ability.Skills;
import org.academy.internal.common.ability.darkmatter.program.DarkmatterProgramExecutionBridge;
import org.academy.internal.common.ability.darkmatter.program.DarkmatterProgramNodeIds;
import org.academy.internal.common.ability.program.compile.ProgramCompiler;
import org.academy.internal.common.ability.program.migrate.ProgramNumericUpgrades;
import org.academy.internal.common.ability.program.registry.AbilityProgramDefinitions;
import org.academy.internal.common.ability.program.registry.BaseAbilityProgramDefinition;
import org.academy.internal.common.ability.program.registry.CommonProgramNodeIds;
import org.academy.internal.common.structure.space.NativeStructureTickets;
import org.academy.mixin.common.ServerLevelEntityManagerAccessor;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.*;

/** Old and upgraded numeric outputs drive the same actual, paid block action and its resource rejection. */
@EventBusSubscriber(modid = AcademyCraft.MOD_ID)
public final class ProgramNumericUpgradeGameTests {
    private ProgramNumericUpgradeGameTests() {}
    private static final Identifier TYPE = AcademyCraft.academy("precision_numeric_upgrade_equivalence_function");
    private static final Identifier CATEGORY = AcademyCraft.academy("darkmatter");
    @SubscribeEvent private static void registerType(RegisterEvent event) { event.register(Registries.TEST_INSTANCE_TYPE, TYPE, () -> Instance.CODEC); }
    @SubscribeEvent private static void registerTests(RegisterGameTestsEvent event) {
        if (!Boolean.getBoolean("academy.precisionNumericUpgradeEquivalence")) return;
        var environment = event.registerEnvironment(TYPE, new TestEnvironmentDefinition.AllOf(List.of()));
        event.registerTest(AcademyCraft.academy("precision_numeric_upgrade_equivalence"), new Instance(new TestData<>(
                environment, Identifier.withDefaultNamespace("empty"), 400, 0, true, Rotation.NONE, false, 1, 1, false, 16)));
    }
    private static void start(GameTestHelper helper) {
        AcademyCraft.LOGGER.info("PRECISION_NUMERIC_UPGRADE_PAID_START");
        var level = helper.getLevel(); var center = new Vec3(128.5, level.getMaxY() - 48.5, 128.5);
        var tickets = new ArrayList<ChunkPos>(); var results = new ArrayList<Map<String, Object>>();
        var target = BlockPos.containing(center.add(0, 1, 4)); var previous = level.getBlockState(target);
        var storage = level.getDataStorage().computeIfAbsent(TicketStorage.TYPE);
        for (int x = 7; x <= 9; x++) for (int z = 7; z <= 9; z++) {
            var chunk = new ChunkPos(x, z); tickets.add(chunk);
            storage.addTicket(chunk.pack(), new Ticket(NativeStructureTickets.ACTIVE.get(), NativeStructureTickets.TICKET_LEVEL)); level.getChunk(x, z);
        }
        var ready = new int[]{0}; var finished = new boolean[]{false};
        Runnable[] await = new Runnable[1];
        await[0] = () -> {
            try {
                check(level.getServer().getPlayerList().getPlayers().isEmpty(), "paid-action fixture requires its isolated server");
                var entities = ((ServerLevelEntityManagerAccessor) level).academy$getEntityManager();
                if (tickets.stream().anyMatch(chunk -> !entities.areEntitiesLoaded(chunk.pack()))) {
                    check(++ready[0] < 100, "paid-action chunks did not become queryable"); nextTick(helper, await[0]); return;
                }
                AcademyCraft.LOGGER.info("PRECISION_NUMERIC_UPGRADE_PAID_READY polls={}", ready[0]);
                var definition = AbilityProgramDefinitions.require(CATEGORY);
                var capabilities = Set.of(Skills.DARKMATTER_DISASSEMBLE.get().getKey());
                var source = source(target, level.dimension().identifier());
                var plan = ProgramNumericUpgrades.preview(source, definition, capabilities);
                check(plan.canApply() && plan.changes().size() == 1, "paid-action upgrade was not reviewable");
                Map<String, Object> previousSuccess = null;
                for (boolean enough : List.of(true, false)) for (boolean upgraded : List.of(false, true)) {
                    String label = (upgraded ? "upgraded" : "legacy") + (enough ? "_paid" : "_insufficient_cp");
                    try (var peer = CapturedProgramPlayer.create(level, UUID.nameUUIDFromBytes(("numeric-paid-" + label).getBytes(StandardCharsets.UTF_8)),
                            "numeric-peer", center)) {
                        var player = peer.player(); player.setGameMode(GameType.SURVIVAL); player.noPhysics = true; player.setNoGravity(true);
                        var system = AbilitySystemServer.getSystem(player);
                        system.setPlayerAbilityCategory(player.getUUID(), AbilityCategories.DARKMATTER.get()); system.setPlayerLevel(player.getUUID(), 5);
                        system.setPlayerBaseMaxCP(player.getUUID(), 100_000); system.setPlayerAvailableCP(player.getUUID(), enough ? 100_000 : 0);
                        system.setPlayerStatus(player.getUUID(), AbilityData.Status.NORMAL);
                        var skill = Skills.DARKMATTER_DISASSEMBLE.get(); system.addPlayerSkill(player, skill.getKeyString());
                        if (!skill.isEnabled(player)) system.toggleSkill(player.getUUID(), skill.getKeyString());
                        check(skill.isEnabled(player), "paid action did not really learn its original skill");
                        level.setBlock(target, Blocks.STONE.defaultBlockState(), 3);
                        var input = upgraded ? plan.candidate() : source;
                        // Compile the old graph directly so an automatic normalization could not conceal the comparison.
                        var compiled = ProgramCompiler.compile(input.graph(), new ProgramCompileContext(CATEGORY, capabilities, ProgramLimits.DEFAULT), definition.nodeLookup());
                        check(compiled.valid(), "actual paid-action graph did not compile");
                        var invocation = new ProgramInvocationContext(input.id(), 0, null, null, 0, null, null, null);
                        float before = system.getPlayerAvailableCP(player.getUUID());
                        var execution = DarkmatterProgramExecutionBridge.executeServer(compiled.program(), player, 1, invocation);
                        float after = system.getPlayerAvailableCP(player.getUUID());
                        boolean air = level.getBlockState(target).isAir();
                        var row = new LinkedHashMap<String, Object>();
                        row.put("name", label); row.put("vm", execution.vmResult()); row.put("accepted", execution.successful());
                        row.put("block_became_air", air); row.put("available_cp_before", before); row.put("available_cp_after", after);
                        row.put("cp_delta", before - after); row.put("trace", invocation.runTrace().steps());
                        execution.transactionResult().ifPresent(transaction -> row.put("transaction", Map.of(
                                "successful", transaction.successful(), "state", transaction.state(), "phase", transaction.phase(),
                                "node_id", transaction.nodeId(), "rollback_failures", transaction.rollbackFailures(),
                                "cause", transaction.cause() == null ? "" : transaction.cause().toString())));
                        results.add(row);
                        if (enough) {
                            check(execution.successful() && air && before > after, "real paid disassembly failed: " + row);
                            check(invocation.runTrace().steps().stream().anyMatch(step -> step.nodeId() == 5 && step.detail().contains("power=1.0")),
                                    "paid action did not receive the numeric output 1.0");
                            if (previousSuccess != null) check(previousSuccess.get("cp_delta").equals(row.get("cp_delta")), "old/new CP charge differed");
                            previousSuccess = row;
                        } else check(!execution.successful() && execution.vmResult().nodeId() == 5 && !air && before == 0 && after == 0,
                                "zero CP did not reject the same actual action at its action node");
                    }
                }
                finished[0] = true; write(helper, results, null); helper.succeed();
            } catch (Throwable failure) { finished[0] = true; write(helper, results, failure); helper.fail("Numeric paid-action equivalence: " + failure); }
            finally {
                if (finished[0]) {
                    level.setBlock(target, previous, 3);
                    for (var chunk : tickets) storage.removeTicket(chunk.pack(), new Ticket(NativeStructureTickets.ACTIVE.get(), NativeStructureTickets.TICKET_LEVEL));
                }
            }
        };
        nextTick(helper, await[0]);
    }
    private static void nextTick(GameTestHelper helper, Runnable action) {
        // GameTestInfo removes the running Runnable key after run() returns. Scheduling that
        // same object inside itself would overwrite its key and then remove the next poll.
        helper.runAfterDelay(1, new Runnable() {
            @Override public void run() { action.run(); }
        });
    }
    private static AbilityProgram source(BlockPos block, Identifier dimension) {
        var first = new JsonObject(); first.addProperty("type", "float"); first.addProperty("value", "0.5");
        var second = new JsonObject(); second.addProperty("type", "float"); second.addProperty("value", "2");
        var position = new JsonObject(); position.addProperty("dimension", dimension.toString());
        position.addProperty("x", block.getX()); position.addProperty("y", block.getY()); position.addProperty("z", block.getZ());
        var power = new JsonObject(); power.addProperty("power", 1.0);
        return new AbilityProgram(1, UUID.nameUUIDFromBytes("numeric-paid-graph".getBytes(StandardCharsets.UTF_8)), "Numeric paid equivalence", CATEGORY,
                new ProgramGraph(List.of(node(0, BaseAbilityProgramDefinition.entryId(CATEGORY), new JsonObject()), node(1, CommonProgramNodeIds.SCALAR_CONSTANT, first),
                        node(2, CommonProgramNodeIds.SCALAR_CONSTANT, second), node(3, CommonProgramNodeIds.FLOAT_MULTIPLY, new JsonObject()),
                        node(4, CommonProgramNodeIds.BLOCK_POSITION_CONSTANT, position), node(5, DarkmatterProgramNodeIds.DISASSEMBLE_BLOCK, power),
                        node(6, CommonProgramNodeIds.STOP, new JsonObject())), List.of(edge(0, "flow", 5, "flow"), edge(5, "flow", 6, "flow"),
                        edge(1, "value", 3, "left"), edge(2, "value", 3, "right"), edge(3, "result", 5, "power"), edge(4, "position", 5, "block"))), ProgramEditorLayout.EMPTY);
    }
    private static ProgramGraph.Node node(int id, Identifier type, JsonObject config) { return new ProgramGraph.Node(id, type, 1, config); }
    private static ProgramGraph.Edge edge(int from, String output, int to, String input) { return new ProgramGraph.Edge(new ProgramGraph.Endpoint(from, output), new ProgramGraph.Endpoint(to, input)); }
    private static void write(GameTestHelper helper, List<Map<String, Object>> rows, Throwable failure) {
        var report = new LinkedHashMap<String, Object>(); report.put("passed", failure == null); report.put("cases", rows);
        report.put("scope", "Isolated registered fixture players, real Darkmatter skill, transaction, block destruction and CP payment/rejection; this does not claim all 29 operations are paid world actions");
        if (failure != null) report.put("failure", failure.toString());
        try { Files.writeString(helper.getLevel().getServer().getWorldPath(LevelResource.ROOT).resolve("precision-numeric-upgrade-equivalence.json"), new GsonBuilder().setPrettyPrinting().create().toJson(report) + "\n"); }
        catch (java.io.IOException failureWriting) { throw new IllegalStateException(failureWriting); }
    }
    private static void check(boolean condition, String reason) { if (!condition) throw new AssertionError(reason); }
    private static final class Instance extends GameTestInstance {
        static final MapCodec<Instance> CODEC = TestData.CODEC.xmap(Instance::new, Instance::info);
        Instance(TestData<Holder<TestEnvironmentDefinition<?>>> info) { super(info); }
        @Override public void run(GameTestHelper helper) { ProgramNumericUpgradeGameTests.start(helper); }
        @Override public MapCodec<? extends GameTestInstance> codec() { return CODEC; }
        @Override protected MutableComponent typeDescription() { return Component.literal("Numeric upgrade actual paid-action equivalence"); }
    }
}
