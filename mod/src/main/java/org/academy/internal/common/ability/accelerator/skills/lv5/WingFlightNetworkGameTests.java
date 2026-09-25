package org.academy.internal.common.ability.accelerator.skills.lv5;

import com.mojang.authlib.GameProfile;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.netty.channel.embedded.EmbeddedChannel;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.gametest.framework.GameTestInstance;
import net.minecraft.gametest.framework.TestData;
import net.minecraft.gametest.framework.TestEnvironmentDefinition;
import net.minecraft.network.Connection;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterGameTestsEvent;
import net.neoforged.neoforge.network.registration.NetworkRegistry;
import net.neoforged.neoforge.registries.RegisterEvent;
import org.academy.AcademyCraft;
import org.academy.api.common.ability.WingControlIntent;
import org.academy.api.common.ability.WingFlightMotion;
import org.academy.internal.common.ability.AbilityCategories;
import org.academy.internal.common.ability.Skills;
import org.academy.internal.common.ability.accelerator.skills.WingFlightRuntime;
import org.academy.internal.common.gametest.GameTestData;
import org.academy.internal.server.ability.AbilitySystemServer;

import java.util.List;
import java.util.UUID;

/**
 * Live server motion regression: packet aggregation must preserve every former directional push.
 */
@EventBusSubscriber(modid = AcademyCraft.MOD_ID)
public final class WingFlightNetworkGameTests {
    private static final Identifier TYPE = AcademyCraft.academy("wing_network_function");

    @SubscribeEvent
    private static void registerType(RegisterEvent event) {
        event.register(Registries.TEST_INSTANCE_TYPE, TYPE, () -> Instance.CODEC);
    }

    @SubscribeEvent
    private static void registerTests(RegisterGameTestsEvent event) {
        var environment = event.registerEnvironment(AcademyCraft.academy("wing_network"), new TestEnvironmentDefinition.AllOf(List.of()));
        event.registerTest(AcademyCraft.academy("wing_network_motion"), new Instance(GameTestData.of(environment,
                Identifier.withDefaultNamespace("empty"), 100, 0, true, Rotation.NONE, false, 1, 1, false, 16), "motion"));
    }

    private static void runMotion(GameTestHelper helper) {
        var profile = new GameProfile(UUID.randomUUID(), "wing-network");
        var cookie = CommonListenerCookie.createInitial(profile, false);
        var player = new ServerPlayer(helper.getLevel().getServer(), helper.getLevel(), profile, cookie.clientInformation());
        var connection = new Connection(PacketFlow.SERVERBOUND);
        new EmbeddedChannel(connection);
        NetworkRegistry.configureMockConnection(connection);
        var players = helper.getLevel().getServer().getPlayerList();
        players.placeNewPlayer(connection, player, cookie);
        player.connection.markClientLoaded();
        try {
            var system = AbilitySystemServer.getSystem(player);
            system.setPlayerAbilityCategory(player.getUUID(), AbilityCategories.ACCELERATOR.get());
            system.setPlayerLevel(player.getUUID(), 5);
            var skill = Skills.BLACK_WING.get();
            system.addPlayerSkill(player, skill.getKeyString());
            var data = system.getPlayerData(player.getUUID());
            data.setAcademyMaxCp(1_000_000);
            data.getCpData().setMaxCP(1_000_000);
            data.getCpData().setAvailableCP(1_000_000);
            if (!skill.isEnabled(player)) skill.toggle(player);
            for (int buttons : new int[]{0, 1, 2, 4, 8, 5, 9, 6, 10, 16}) {
                var input = new WingControlIntent(buttons, 123, 0);
                var initial = new Vec3(.3, .1, -.7);
                player.setDeltaMovement(initial);
                WingFlightRuntime.clear(player, skill);
                for (int packet = 0; packet < 100; packet++) {
                    WingFlightRuntime.accept(player, skill, input);
                }
                helper.assertTrue(player.getDeltaMovement().equals(initial), "Packets must not apply movement");
                player.tickCount++;
                WingFlightRuntime.tick(player, skill);
                var once = player.getDeltaMovement();
                WingFlightRuntime.tick(player, skill);
                helper.assertTrue(once.equals(player.getDeltaMovement()), "Duplicate tick must not apply another thrust");
                var expected = WingFlightMotion.step(
                        initial.multiply(.91, .98, .91), input, 0, 1, 1);
                helper.assertTrue(expected.distanceToSqr(player.getDeltaMovement()) < 1e-16,
                        "A stalled server must consume the newest input only once");
                WingFlightRuntime.accept(
                        player, skill, new WingControlIntent(0, 0, 0, 0));
                player.tickCount++;
                WingFlightRuntime.tick(player, skill);
                helper.assertTrue(player.getDeltaMovement().equals(Vec3.ZERO), "Zero momentum must stop immediately");
            }
            helper.succeed();
        } finally {
            players.remove(player);
        }
    }

    private static final class Instance extends GameTestInstance {
        static final MapCodec<Instance> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
                TestData.CODEC.forGetter(Instance::info), Codec.STRING.fieldOf("scenario").forGetter(value -> value.scenario)
        ).apply(instance, Instance::new));
        final String scenario;

        Instance(TestData<Holder<TestEnvironmentDefinition<?>>> info, String scenario) {
            super(info);
            this.scenario = scenario;
        }

        @Override
        public void run(GameTestHelper helper) {
            runMotion(helper);
        }

        @Override
        public MapCodec<? extends GameTestInstance> codec() {
            return CODEC;
        }

        @Override
        protected MutableComponent typeDescription() {
            return Component.literal("Wing input aggregation regression");
        }
    }
}
