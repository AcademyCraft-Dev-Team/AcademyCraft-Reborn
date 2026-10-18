package org.academy.internal.server.ability.gametest;

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
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterGameTestsEvent;
import net.neoforged.neoforge.registries.RegisterEvent;
import net.neoforged.neoforge.network.registration.NetworkRegistry;
import org.academy.AcademyCraft;
import org.academy.api.common.ability.SyncTypes;
import org.academy.api.common.data.AbilityData;
import org.academy.api.server.ability.AbilitySystemServer;
import org.academy.internal.common.ability.AbilityCategories;
import org.academy.internal.common.ability.Skills;
import org.academy.internal.server.ability.PlayerCPManager;

import java.util.List;
import java.util.UUID;

/** Exercises actual category switching, CP ticking and optional installed-addon recovery vetoes. */
@EventBusSubscriber(modid = AcademyCraft.MOD_ID)
public final class NativeCpRecoveryGameTests {
    private static final Identifier TYPE = AcademyCraft.academy("native_cp_recovery_function");

    @SubscribeEvent
    private static void registerType(RegisterEvent event) {
        event.register(Registries.TEST_INSTANCE_TYPE, TYPE, () -> Instance.CODEC);
    }

    @SubscribeEvent
    private static void registerTests(RegisterGameTestsEvent event) {
        for (var scenario : List.of("category_return", "occupation_recovery", "overload_recovery")) {
            var environment = event.registerEnvironment(AcademyCraft.academy("native_cp/" + scenario),
                    new TestEnvironmentDefinition.AllOf(List.of()));
            event.registerTest(AcademyCraft.academy("native_cp_" + scenario), new Instance(new TestData<>(
                    environment, Identifier.withDefaultNamespace("empty"), 40, 0, true,
                    Rotation.NONE, false, 1, 1, false, 16), scenario));
        }
    }

    private static ServerPlayer createPlayer(GameTestHelper helper) {
        var profile = new GameProfile(UUID.randomUUID(), "cp-regression");
        var cookie = CommonListenerCookie.createInitial(profile, false);
        var player = new ServerPlayer(helper.getLevel().getServer(), helper.getLevel(), profile,
                cookie.clientInformation());
        var connection = new Connection(PacketFlow.SERVERBOUND);
        new EmbeddedChannel(connection);
        NetworkRegistry.configureMockConnection(connection);
        helper.getLevel().getServer().getPlayerList().placeNewPlayer(connection, player, cookie);
        return player;
    }

    private static PlayerCPManager manager() {
        return (PlayerCPManager) AbilitySystemServer.SubsystemRegistry.getHandler(SyncTypes.CP_DATA).orElseThrow();
    }

    private static void runScenario(GameTestHelper helper, String scenario) {
        var player = createPlayer(helper);
        Class<?> exhaustion = null;
        try {
            var system = AbilitySystemServer.getSystem(player);
            var uuid = player.getUUID();
            system.setPlayerAbilityCategory(uuid, AbilityCategories.ELECTROMASTER.get());
            system.setPlayerLevel(uuid, 5);
            var data = system.getPlayerData(uuid);
            data.setAcademyMaxCp(600);
            data.setAppliedCommonSkillMaxCpBonus(500);
            data.getCpData().setMaxCP(600);
            data.getCpData().setAvailableCP(600);
            if (scenario.equals("category_return")) {
                // When installed, use the actual addon's category and its CP manager injections.
                var shadow = org.academy.api.common.registries.Registries.ABILITY_CATEGORIES.stream()
                        .filter(category -> category.getKey().getNamespace().equals("shadowmaster"))
                        .findFirst().orElse(AbilityCategories.TELEPORT.get());
                system.setPlayerAbilityCategory(uuid, shadow);
                data.getCpData().setMaxCP(400);
                data.getCpData().setAvailableCP(350);
                data.getMutableCpOccupations().add(new AbilityData.CpOccupationData(
                        50, 999, "removed_addon:skill", true));
                system.setPlayerAbilityCategory(uuid, AbilityCategories.ELECTROMASTER.get());
                helper.assertTrue(system.getPlayerMaxCP(uuid) == 600,
                        "Returning to a built-in category must recover the persisted maximum");
                helper.assertTrue(system.getPlayerAvailableCP(uuid) == 600 && data.getCpOccupations().isEmpty(),
                        "Category switching must release old occupations and refill the repaired maximum");
            } else {
                // The same scenarios run with and without the user's addon JARs in the test instance.
                // Only this regression fixture knows the optional addon; production code has no dependency.
                try {
                    exhaustion = Class.forName("cn.academy.alice.ability.codeunknown.StarMapExhaustion");
                    exhaustion.getMethod("triggerFromManualClose", ServerPlayer.class).invoke(null, player);
                    helper.assertTrue((boolean) exhaustion.getMethod("isExhausted", UUID.class).invoke(null, uuid),
                            "The installed addon must actually veto the legacy recovery entry point");
                } catch (ClassNotFoundException ignored) {
                    // Standalone base-mod regression run.
                }
                var cp = data.getCpData();
                cp.setStatus(AbilityData.Status.NORMAL);
                cp.setAvailableCP(500);
                cp.setCurrSP(1000);
                var occupations = data.getMutableCpOccupations();
                occupations.clear();
                if (scenario.equals("occupation_recovery")) {
                    var valid = new AbilityData.CpOccupationData(20, 1, Skills.ARC_GENERATE.get().getKeyString(), false);
                    occupations.add(valid);
                    occupations.add(new AbilityData.CpOccupationData(30, 0, "removed_addon:skill", true));
                    occupations.add(new AbilityData.CpOccupationData(50, 100,
                            Skills.DARKMATTER_GENERATION.get().getKeyString(), false));
                    cp.setCurrMP(25); // Old-category MP must not exempt its foreign CP reservation.
                    manager().tick(player);
                    helper.assertTrue(occupations.size() == 1 && occupations.getFirst() == valid,
                            "Tick must remove foreign debt while retaining the current category's timed occupation");
                    helper.assertTrue(cp.getAvailableCP() == 580, "Only invalid occupations should be refunded immediately");
                    for (var tick = 1; tick < 20; tick++) manager().tick(player);
                    helper.assertTrue(occupations.isEmpty() && cp.getAvailableCP() == 600,
                            "Native timed recovery must complete even when an addon vetoes processOccupations");
                    helper.assertTrue(cp.getCurrSP() == 998, "Valid recovery must still pay the normal SP cost");
                } else {
                    cp.setAvailableCP(0);
                    cp.setStatus(AbilityData.Status.OVERLOAD);
                    cp.setStateTimer(1);
                    occupations.add(new AbilityData.CpOccupationData(600, 100,
                            Skills.ARC_GENERATE.get().getKeyString(), false));
                    manager().tick(player);
                    helper.assertTrue(cp.getStatus() == AbilityData.Status.NORMAL && cp.getAvailableCP() == 600,
                            "Native overload recovery must restore CP even when an addon vetoes tickOverload");
                    helper.assertTrue(occupations.isEmpty(), "Overload recovery must release its occupations");
                }
            }
            helper.succeed();
        } catch (ReflectiveOperationException exception) {
            throw new IllegalStateException("Cannot exercise installed addon recovery state", exception);
        } finally {
            try {
                if (exhaustion != null) exhaustion.getMethod("clear", ServerPlayer.class).invoke(null, player);
            } catch (ReflectiveOperationException exception) {
                throw new IllegalStateException("Cannot clear test addon recovery state", exception);
            } finally {
                helper.getLevel().getServer().getPlayerList().remove(player);
            }
        }
    }

    private static final class Instance extends GameTestInstance {
        private static final MapCodec<Instance> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
                TestData.CODEC.forGetter(Instance::info),
                Codec.STRING.fieldOf("scenario").forGetter(value -> value.scenario)
        ).apply(instance, Instance::new));
        private final String scenario;

        private Instance(TestData<Holder<TestEnvironmentDefinition<?>>> info, String scenario) {
            super(info);
            this.scenario = scenario;
        }

        @Override
        public void run(GameTestHelper helper) {
            runScenario(helper, scenario);
        }

        @Override
        public MapCodec<? extends GameTestInstance> codec() {
            return CODEC;
        }

        @Override
        protected MutableComponent typeDescription() {
            return Component.literal("Native CP recovery regression");
        }
    }
}
