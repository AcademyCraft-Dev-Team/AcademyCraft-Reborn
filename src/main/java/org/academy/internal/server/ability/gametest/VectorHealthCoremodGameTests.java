package org.academy.internal.server.ability.gametest;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.gametest.framework.GameTestInstance;
import net.minecraft.gametest.framework.TestData;
import net.minecraft.gametest.framework.TestEnvironmentDefinition;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.level.block.Rotation;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterGameTestsEvent;
import net.neoforged.neoforge.registries.RegisterEvent;
import org.academy.AcademyCraft;
import org.academy.api.server.ability.AbilitySystemServer;
import org.academy.internal.common.ability.AbilityCategories;
import org.academy.internal.common.ability.Skills;
import org.academy.internal.common.ability.accelerator.skills.lv4.VectorReflection;
import org.academy.mixin.common.LivingHealthDataAccessor;

import java.util.List;

import static org.academy.internal.server.ability.gametest.HostileTargetGameTests.check;
import static org.academy.internal.server.ability.gametest.HostileTargetGameTests.player;

/** Exercises the transformed parent getter together with the real synced health data item. */
@EventBusSubscriber(modid = AcademyCraft.MOD_ID)
public final class VectorHealthCoremodGameTests {
    private static final Identifier TYPE = AcademyCraft.academy("vector_health_coremod_test_function");

    private VectorHealthCoremodGameTests() {
    }

    @SubscribeEvent
    private static void registerType(RegisterEvent event) {
        event.register(Registries.TEST_INSTANCE_TYPE, TYPE, () -> Instance.CODEC);
    }

    @SubscribeEvent
    private static void registerTests(RegisterGameTestsEvent event) {
        var environment = event.registerEnvironment(AcademyCraft.academy("vector_health_coremod"),
                new TestEnvironmentDefinition.AllOf(List.of()));
        event.registerTest(AcademyCraft.academy("vector_health_coremod"), new Instance(new TestData<>(
                environment, Identifier.withDefaultNamespace("empty"), 80, 0, true,
                Rotation.NONE, false, 1, 1, false, 16)));
    }

    private static void near(GameTestHelper helper, float expected, float actual, String message) {
        check(helper, Math.abs(expected - actual) < 0.001f,
                message + ": expected=" + expected + ", actual=" + actual);
    }

    private static void run(GameTestHelper helper, ServerPlayer player) {
        var system = AbilitySystemServer.getSystem(player);
        var id = player.getUUID();
        system.setPlayerAbilityCategory(id, AbilityCategories.ACCELERATOR.get());
        system.setPlayerLevel(id, 4);
        system.addPlayerSkill(player, Skills.VECTOR_REFLECTION.get().getKeyString());
        var data = system.getPlayerData(id);
        data.setAcademyMaxCp(1000);
        data.getCpData().setMaxCP(1000);
        data.getCpData().setAvailableCP(1000);
        player.getAttribute(Attributes.MAX_HEALTH).setBaseValue(40);
        player.setHealth(20);

        var skill = Skills.VECTOR_REFLECTION.get();
        skill.toggle(player);
        check(helper, VectorReflection.Server.usesFullInstanceProtection(player),
                "Vector Reflection did not activate");
        VectorReflection.Server.maintainProtection(player);
        near(helper, 20, player.getHealth(), "initial guarded health");

        player.setHealth(5);
        near(helper, 20, player.getHealth(), "setHealth reduction");
        var healthId = LivingHealthDataAccessor.academy$healthAccessor();
        player.getEntityData().set(healthId, 3.0f);
        near(helper, 20, player.getHealth(), "synched data reduction");
        near(helper, 20, player.getEntityData().get(healthId), "stored health after rejected write");

        player.setHealth(30);
        near(helper, 30, player.getHealth(), "accepted healing");
        VectorReflection.Server.imaginebreaker(player, 4);
        near(helper, 26, player.getHealth(), "authorized skill damage");

        skill.toggle(player);
        VectorReflection.Server.clearProtection(player);
        player.setHealth(5);
        near(helper, 5, player.getHealth(), "ordinary health after deactivation");
    }

    private static final class Instance extends GameTestInstance {
        private static final MapCodec<Instance> CODEC = TestData.CODEC.xmap(Instance::new, Instance::info);

        private Instance(TestData<Holder<TestEnvironmentDefinition<?>>> info) {
            super(info);
        }

        @Override
        public void run(GameTestHelper helper) {
            var subject = player(helper, 4);
            try {
                VectorHealthCoremodGameTests.run(helper, subject);
                helper.succeed();
            } finally {
                VectorReflection.Server.clearProtection(subject);
                helper.getLevel().getServer().getPlayerList().remove(subject);
            }
        }

        @Override
        public MapCodec<? extends GameTestInstance> codec() {
            return CODEC;
        }

        @Override
        protected MutableComponent typeDescription() {
            return Component.literal("NeoForge coremod player health protection");
        }
    }
}
