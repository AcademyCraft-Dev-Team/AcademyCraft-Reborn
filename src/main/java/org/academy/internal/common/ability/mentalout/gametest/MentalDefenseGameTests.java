package org.academy.internal.common.ability.mentalout.gametest;

import com.mojang.authlib.GameProfile;
import com.mojang.serialization.MapCodec;
import io.netty.channel.embedded.EmbeddedChannel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.*;
import net.minecraft.network.Connection;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.tags.TagKey;
import net.minecraft.tags.TagLoader;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.block.Rotation;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterGameTestsEvent;
import net.neoforged.neoforge.network.registration.NetworkRegistry;
import net.neoforged.neoforge.registries.RegisterEvent;
import org.academy.AcademyCraft;
import org.academy.api.common.entitycontrol.*;
import org.academy.api.server.ability.AbilitySystemServer;
import org.academy.internal.common.ability.AbilityCategories;
import org.academy.internal.common.ability.Skills;
import org.academy.internal.common.ability.mentalout.MentalResistanceManager;
import org.academy.internal.common.ability.mentalout.MentaloutControlContext;
import org.academy.internal.common.ability.mentalout.control.MentalControlRuntime;
import org.academy.internal.common.ability.mentalout.control.MentalPerceptionRuntime;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.UUID;

/** Runs in an isolated environment, restoring the registry tags and clock in finally. */
@EventBusSubscriber(modid = AcademyCraft.MOD_ID)
public final class MentalDefenseGameTests {
    private static final Identifier TYPE = AcademyCraft.academy("mental_defense_function");
    private static final Identifier SOURCE = AcademyCraft.academy("test_mental_defense");
    private static boolean feedbackRegistered;

    @SubscribeEvent
    private static void registerType(RegisterEvent event) {
        event.register(Registries.TEST_INSTANCE_TYPE, TYPE, () -> Instance.CODEC);
    }

    @SubscribeEvent
    private static void registerTests(RegisterGameTestsEvent event) {
        var environment = event.registerEnvironment(TYPE, new TestEnvironmentDefinition.AllOf(List.of()));
        event.registerTest(AcademyCraft.academy("mental_defense"), new Instance(new TestData<>(
                environment, Identifier.withDefaultNamespace("empty"), 40, 0, true,
                Rotation.NONE, false, 1, 1, false, 16)));
    }

    private static ServerPlayer player(GameTestHelper helper, List<Component> feedback) {
        var profile = new GameProfile(UUID.randomUUID(), "mental-test");
        var cookie = CommonListenerCookie.createInitial(profile, false);
        var player = new ServerPlayer(helper.getLevel().getServer(), helper.getLevel(), profile, cookie.clientInformation()) {
            @Override
            public void sendOverlayMessage(Component message) {
                feedback.add(message);
            }
        };
        var connection = new Connection(PacketFlow.SERVERBOUND);
        new EmbeddedChannel(connection);
        NetworkRegistry.configureMockConnection(connection);
        helper.getLevel().getServer().getPlayerList().placeNewPlayer(connection, player, cookie);
        return player;
    }

    private static ControlHandle freeze(ServerPlayer controller, LivingEntity subject) {
        return MentalControlApi.apply(ControlRequest.permanent(
                controller, subject, SOURCE, 100, new ControlDirective.FreezeAi()));
    }

    private static void verify(GameTestHelper helper) {
        var level = helper.getLevel();
        var server = level.getServer();
        var originalTime = level.getGameTime();
        var registry = BuiltInRegistries.ENTITY_TYPE;
        var originalTags = new HashMap<TagKey<EntityType<?>>, List<Holder<EntityType<?>>>>();
        registry.listTags().forEach(tag -> originalTags.put(tag.key(), tag.stream().toList()));
        var testTags = new HashMap<>(originalTags);
        testTags.put(MentalControlTags.IMMUNE, List.of(
                EntityTypes.COW.builtInRegistryHolder(), EntityTypes.PIG.builtInRegistryHolder(),
                EntityTypes.CHICKEN.builtInRegistryHolder()));
        testTags.put(MentalControlTags.RESISTANCE, List.of(
                EntityTypes.SHEEP.builtInRegistryHolder(), EntityTypes.PLAYER.builtInRegistryHolder()));
        var feedback = new ArrayList<Component>();
        var players = new ArrayList<ServerPlayer>();
        var entities = new ArrayList<LivingEntity>();
        try {
            registry.prepareTagReload(new TagLoader.LoadResult<>(registry.key(), testTags)).apply();
            var controller = player(helper, feedback);
            players.add(controller);
            var playerSubject = player(helper, new ArrayList<>());
            players.add(playerSubject);
            var system = AbilitySystemServer.getSystem(controller);
            system.setPlayerAbilityCategory(controller.getUUID(), AbilityCategories.MENTALOUT.get());
            system.setPlayerLevel(controller.getUUID(), 5);
            system.addPlayerSkill(controller, Skills.MENTAL_INTERVENTION.get().getKeyString());
            system.setPlayerMaxCP(controller.getUUID(), 10000);
            system.setPlayerAvailableCP(controller.getUUID(), 10000);

            var cow = helper.spawn(EntityTypes.COW, new BlockPos(2, 2, 2));
            var pig = helper.spawn(EntityTypes.PIG, new BlockPos(3, 2, 2));
            var chicken = helper.spawn(EntityTypes.CHICKEN, new BlockPos(4, 2, 2));
            var sheep = helper.spawn(EntityTypes.SHEEP, new BlockPos(5, 2, 2));
            var rosterOnly = helper.spawn(EntityTypes.SHEEP, new BlockPos(6, 2, 2));
            var plain = helper.spawn(EntityTypes.RABBIT, new BlockPos(7, 2, 2));
            entities.addAll(List.of(cow, pig, chicken, sheep, rosterOnly, plain));
            if (!feedbackRegistered) {
                MentalControlFeedbackApi.registerImmuneFeedback(Identifier.withDefaultNamespace("cow"),
                        (_, subject) -> subject.getCustomName() == null ? null
                                : Component.literal("immune:" + subject.getCustomName().getString()));
                MentalControlFeedbackApi.registerImmuneFeedback(Identifier.withDefaultNamespace("pig"),
                        (_, _) -> { throw new IllegalStateException("Expected feedback fallback test"); });
                feedbackRegistered = true;
            }
            cow.setCustomName(Component.literal("first"));
            helper.assertTrue(MentaloutControlContext.toggleTarget(controller, cow)
                    == MentaloutControlContext.ToggleResult.UNSUPPORTED, "Immune cow must reject intervention");
            helper.assertTrue(MentalControlRuntime.notifyInterventionBlocked(controller, cow), "Must report immunity");
            helper.assertTrue(feedback.getLast().getString().equals("immune:first"), "Custom text must reach controller");
            cow.setCustomName(Component.literal("second"));
            helper.assertTrue(MentalControlFeedbackApi.immuneFeedback(controller, cow).getString().equals("immune:second"),
                    "Provider must inspect the actual target instance");
            var fallback = Component.translatable("message.academy.mentalout.protected_target");
            cow.setCustomName(null);
            helper.assertTrue(MentalControlFeedbackApi.immuneFeedback(controller, cow).equals(fallback), "Null fallback");
            helper.assertTrue(MentalControlFeedbackApi.immuneFeedback(controller, pig).equals(fallback), "Exception fallback");
            helper.assertTrue(MentalControlFeedbackApi.immuneFeedback(controller, chicken).equals(fallback), "Missing fallback");
            try {
                MentalControlFeedbackApi.registerImmuneFeedback(Identifier.withDefaultNamespace("cow"), (_, _) -> fallback);
                throw new AssertionError("Duplicate registration must fail");
            } catch (IllegalArgumentException expected) {
                // The original provider must not be replaced.
            }

            for (var subject : List.of(sheep, rosterOnly)) {
                helper.assertTrue(MentaloutControlContext.addTarget(controller, subject)
                        == MentaloutControlContext.ToggleResult.ADDED, "Tagged target must allow intervention");
            }
            var mobHandle = freeze(controller, sheep);
            var playerHandle = freeze(controller, playerSubject);
            var plainHandle = freeze(controller, plain);
            var perception = MentalPerceptionRuntime.apply(controller, sheep, plain, SOURCE, 100, Long.MAX_VALUE);
            var start = level.getGameTime();
            // Advance the authoritative clock and invoke real runtimes without simulating physical input.
            // Repeated calls in one tick must neither speed up nor postpone automatic resistance.
            for (int elapsed = 0; elapsed <= 501; elapsed++) {
                ((net.minecraft.world.level.storage.ServerLevelData) level.getLevelData()).setGameTime(start + elapsed);
                MentalControlRuntime.tick(server);
                MentalPerceptionRuntime.tick(server);
                MentalResistanceManager.tick(server);
                if (elapsed == 200) {
                    mobHandle.close();
                    mobHandle = freeze(controller, sheep);
                }
                if (elapsed == 400) {
                    helper.assertTrue(!mobHandle.isClosed() && !playerHandle.isClosed(), "Exactly 20 seconds must not break");
                }
                if (elapsed == 401) {
                    helper.assertTrue(mobHandle.isClosed() && playerHandle.isClosed(), "Mobs and players must break");
                    helper.assertTrue(perception.isClosed(), "Automatic resistance must close perception");
                    helper.assertTrue(MentalControlApi.resistanceRemainingTicks(sheep) == 100, "Block must last 100 ticks");
                    helper.assertTrue(!MentalControlApi.supports(sheep, ControlCapability.FREEZE_AI), "Block must deny controls");
                    helper.assertTrue(MentaloutControlContext.subjects(controller).contains(sheep), "Roster must remain");
                    helper.assertTrue(MentalControlRuntime.evaluateIntervention(sheep, ControlCapability.FREEZE_AI).supported(),
                            "Intervention must be exempt from automatic block");
                    try {
                        freeze(controller, sheep);
                        throw new AssertionError("Active resistance must reject a new lease");
                    } catch (ControlApplyException expected) {
                        // Admission must match the read-only query.
                    }
                }
                if (elapsed == 500) {
                    helper.assertTrue(MentalControlApi.resistanceRemainingTicks(sheep) == 1, "Last block tick");
                }
            }
            helper.assertTrue(MentalControlApi.resistanceRemainingTicks(sheep) == 0, "Block must expire at 5 seconds");
            freeze(controller, sheep).close();
            helper.assertTrue(!plainHandle.isClosed(), "Untagged entity must retain control");
            helper.assertTrue(MentalControlApi.resistanceRemainingTicks(rosterOnly) == 0, "Intervention alone must not count");
            helper.assertTrue(MentaloutControlContext.subjects(controller).contains(rosterOnly), "Intervention must remain");
            helper.succeed();
        } finally {
            MentalResistanceManager.clear();
            for (var entity : entities) {
                MentalControlRuntime.releaseBySubject(server, entity.getUUID());
                MentalPerceptionRuntime.releaseEntity(entity.getUUID());
                entity.discard();
            }
            for (var player : players) server.getPlayerList().remove(player);
            ((net.minecraft.world.level.storage.ServerLevelData) level.getLevelData()).setGameTime(originalTime);
            registry.prepareTagReload(new TagLoader.LoadResult<>(registry.key(), originalTags)).apply();
        }
    }

    private static final class Instance extends GameTestInstance {
        private static final MapCodec<Instance> CODEC = TestData.CODEC.xmap(Instance::new, Instance::info);

        private Instance(TestData<Holder<TestEnvironmentDefinition<?>>> info) {
            super(info);
        }

        @Override
        public void run(GameTestHelper helper) {
            verify(helper);
        }

        @Override
        public MapCodec<? extends GameTestInstance> codec() {
            return CODEC;
        }

        @Override
        protected MutableComponent typeDescription() {
            return Component.literal("Mental defense regression");
        }
    }
}
