package example.academy;

import com.google.gson.JsonObject;
import com.mojang.authlib.GameProfile;
import com.mojang.serialization.MapCodec;
import io.netty.channel.embedded.EmbeddedChannel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.*;
import net.minecraft.network.Connection;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.level.block.Rotation;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterGameTestsEvent;
import net.neoforged.neoforge.network.registration.NetworkRegistry;
import net.neoforged.neoforge.registries.RegisterEvent;
import org.academy.api.common.ability.program.*;
import org.academy.api.common.damage.SkillDamageSource;
import org.academy.api.server.ability.AbilitySystemServer;
import org.academy.api.server.ability.program.AbilityProgramService;
import java.util.List;
import java.util.UUID;

@EventBusSubscriber(modid = ExampleAddon.MOD_ID)
public final class ExampleGameTests {
    private static final Identifier TYPE = ExampleAddon.id("api_function");

    @SubscribeEvent
    private static void registerType(RegisterEvent event) {
        event.register(Registries.TEST_INSTANCE_TYPE, TYPE, () -> Instance.CODEC);
    }

    @SubscribeEvent
    private static void registerTests(RegisterGameTestsEvent event) {
        var environment = event.registerEnvironment(TYPE, new TestEnvironmentDefinition.AllOf(List.of()));
        event.registerTest(ExampleAddon.id("registration_execution"), new Instance(new TestData<>(
                environment, Identifier.withDefaultNamespace("empty"), 60, 0, true,
                Rotation.NONE, false, 1, 1, false, 16)));
    }

    private static AbilityProgram program(int actions) {
        var config = new JsonObject();
        config.addProperty("ticks", 20);
        var nodes = new java.util.ArrayList<ProgramGraph.Node>();
        var edges = new java.util.ArrayList<ProgramGraph.Edge>();
        nodes.add(new ProgramGraph.Node(0, ExampleAddon.ENTRY_ID, 1, new JsonObject()));
        for (int i = 1; i <= actions; i++) {
            nodes.add(new ProgramGraph.Node(i, ExampleAddon.ACTION_ID, 1, config));
            edges.add(new ProgramGraph.Edge(new ProgramGraph.Endpoint(i - 1, "flow"), new ProgramGraph.Endpoint(i, "flow")));
        }
        return new AbilityProgram(1, UUID.randomUUID(), "Addon API", ExampleAddon.CATEGORY_KEY.identifier(),
                new ProgramGraph(nodes, edges), ProgramEditorLayout.EMPTY);
    }

    private static void command(ServerPlayer player, String command) {
        var server = player.level().getServer();
        try {
            var source = server.createCommandSourceStack().withEntity(player)
                    .withPermission(net.minecraft.server.permissions.PermissionSet.ALL_PERMISSIONS);
            if (server.getCommands().getDispatcher().execute("academy " + command, source) <= 0) {
                throw new AssertionError("Fixture setup command failed: " + command);
            }
        } catch (com.mojang.brigadier.exceptions.CommandSyntaxException exception) {
            throw new AssertionError("Fixture setup command failed: " + command, exception);
        }
    }

    private static void verify(GameTestHelper helper) {
        var level = helper.getLevel();
        var server = level.getServer();
        var profile = new GameProfile(UUID.randomUUID(), "api-test");
        var cookie = CommonListenerCookie.createInitial(profile, false);
        // NeoForge recognizes test-player subclasses as connections without a real config handshake.
        var player = new ServerPlayer(server, level, profile, cookie.clientInformation()) { };
        var connection = new Connection(PacketFlow.SERVERBOUND);
        new EmbeddedChannel(connection);
        NetworkRegistry.configureMockConnection(connection);
        server.getPlayerList().placeNewPlayer(connection, player, cookie);
        player.connection.markClientLoaded();
        var position = helper.absolutePos(new BlockPos(2, 2, 2));
        player.snapTo(position.getX() + 0.5, position.getY(), position.getZ() + 0.5, 0, 0);
        player.setNoGravity(true);
        var pendingExpiry = new boolean[]{false};
        try {
            var category = ExampleAddon.CATEGORY.get();
            var primary = ExampleAddon.PRIMARY.get();
            var secondary = ExampleAddon.SECONDARY.get();
            helper.assertTrue(category.getSkills().size() == 2, "Same-class skills must coexist");
            helper.assertTrue(secondary.getDependencies().contains(primary), "Deferred key dependencies must resolve");
            helper.assertTrue(primary.getCategory() == category, "Category key must resolve");
            helper.assertTrue(primary.getClass() == secondary.getClass(), "Fixture must actually use the same Java class");
            try {
                category.getSkills().clear();
                throw new AssertionError("Frozen category view must be immutable");
            } catch (UnsupportedOperationException expected) { }

            var system = AbilitySystemServer.getSystem(player);
            var id = player.getUUID();
            command(player, "set_category " + category.getKey());
            command(player, "level 5");
            command(player, "debug cp set_max @s 1000");
            command(player, "debug cp set @s 1000");
            system.addPlayerSkill(player, primary.getKeyString());
            var graph = program(1);
            var denied = AbilityProgramService.execute(player, 0, graph);
            helper.assertTrue(!denied.accepted(), "Unlearned required skill must deny the graph");
            helper.assertTrue(!player.isCurrentlyGlowing(), "Rejected graph must have no side effects");
            system.addPlayerSkill(player, secondary.getKeyString());
            helper.assertTrue(secondary.state(player, ExampleAddon.CAST_COUNT).orElseThrow() == 0, "Default codec state");
            var saved = AbilityProgramService.save(player, 0, graph);
            helper.assertTrue(saved.accepted(), "Addon graph must save: " + saved);
            helper.assertTrue(AbilityProgramService.book(player).slot(0).program().id().equals(graph.id()), "Saved graph must round-trip");
            var beforeCp = system.getPlayerAvailableCP(id);
            var run = AbilityProgramService.execute(player, 0, graph);
            helper.assertTrue(run.accepted(), "Addon action must execute: " + run.reason());
            helper.assertTrue(player.isCurrentlyGlowing(), "Action effect must be applied");
            helper.assertTrue(secondary.state(player, ExampleAddon.CAST_COUNT).orElseThrow() == 1, "Addon state must update");
            helper.assertTrue(primary.state(player, ExampleAddon.CAST_COUNT).orElseThrow() == 0, "State must be isolated per skill");
            helper.assertTrue(system.getPlayerAvailableCP(id) < beforeCp, "Program action must charge CP");
            AbilityProgramService.cancel(player, 0, graph.id());
            helper.assertTrue(!player.isCurrentlyGlowing(), "Cancel must release completed lasting effects");

            var singleCost = system.quoteTimedOccupation(player, 10, secondary);
            command(player, "debug cp set @s " + singleCost * 1.5f);
            var failed = AbilityProgramService.execute(player, 0, program(2));
            helper.assertTrue(!failed.accepted(), "Insufficient combined CP must reject actions");
            helper.assertTrue(system.getPlayerAvailableCP(id) == singleCost * 1.5f, "Budget rejection must not charge CP");
            helper.assertTrue(secondary.state(player, ExampleAddon.CAST_COUNT).orElseThrow() == 1, "Failed budget must not apply actions");
            helper.assertTrue(!player.isCurrentlyGlowing(), "Failed budget must not leave an effect");
            command(player, "level 4");
            helper.assertTrue(!AbilityProgramService.execute(player, 0, graph).accepted(), "Level 5 unlock must be enforced");
            command(player, "level 5");
            command(player, "debug cp set @s 1000");
            var target = helper.spawn(EntityTypes.PIG, new BlockPos(3, 2, 3));
            helper.assertTrue(SkillDamageSource.of(player, primary).is(ExampleAddon.DAMAGE_TYPE),
                    "Category profile must supply the damage type");
            helper.assertTrue(SkillDamageSource.of(player, secondary).is(net.minecraft.world.damagesource.DamageTypes.MAGIC),
                    "Skill declaration must override the category default");
            var oldHealth = target.getHealth();
            helper.assertTrue(primary.hit(player, target), "Registered skill must cast");
            helper.assertTrue(target.getHealth() < oldHealth, "Registered damage type must reach real settlement");
            target.discard();
            for (var damageProfile : List.of(ExampleAddon.DIRECT.getKey(), ExampleAddon.TRUE.getKey())) {
                var victim = helper.spawn(EntityTypes.PIG, new BlockPos(3, 2, 3));
                try {
                    victim.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.MAX_ABSORPTION).setBaseValue(6);
                    victim.setAbsorptionAmount(6);
                    helper.assertTrue(victim.getAbsorptionAmount() == 6, "Fixture must have absorption before damage");
                    var health = victim.getHealth();
                    var result = org.academy.api.server.damage.AbilityDamageService.apply(player, victim, secondary,
                            org.academy.api.server.damage.AbilityDamageService.Request.of(4).withProfile(damageProfile));
                    helper.assertTrue(result.applied() && victim.getHealth() < health,
                            "Explicit addon profile must override skill and reach its settlement: " + damageProfile);
                    helper.assertTrue(victim.getAbsorptionAmount() == 6,
                            "Addon bypassAbsorption rule must preserve absorption: " + damageProfile);
                } finally {
                    victim.discard();
                }
            }
            helper.assertTrue(AbilityProgramService.execute(player, 0, graph).accepted(), "Expiry fixture must execute");
            helper.assertTrue(player.isCurrentlyGlowing(), "Lasting effect must be active before expiry");
            helper.runAfterDelay(25, () -> {
                try {
                    helper.assertTrue(!player.isCurrentlyGlowing(), "Server ticks must release expired effects");
                    helper.succeed();
                } finally {
                    server.getPlayerList().remove(player);
                }
            });
            pendingExpiry[0] = true;
        } finally {
            if (!pendingExpiry[0]) server.getPlayerList().remove(player);
        }
    }

    private static final class Instance extends GameTestInstance {
        private static final MapCodec<Instance> CODEC = TestData.CODEC.xmap(Instance::new, Instance::info);
        private Instance(TestData<Holder<TestEnvironmentDefinition<?>>> info) { super(info); }
        @Override public void run(GameTestHelper helper) { verify(helper); }
        @Override public MapCodec<? extends GameTestInstance> codec() { return CODEC; }
        @Override protected MutableComponent typeDescription() { return Component.literal("Public addon API integration"); }
    }
}
