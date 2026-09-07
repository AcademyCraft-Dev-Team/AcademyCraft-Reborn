package org.academy.internal.server.ability.gametest;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.*;
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
import org.academy.api.common.attribute.PlayerAttributes;
import org.academy.api.common.damage.SkillDamageSource;
import org.academy.api.server.ability.AbilitySystemServer;
import org.academy.internal.common.ability.AbilityCategories;
import org.academy.internal.common.ability.Skills;
import org.academy.internal.common.ability.mentalout.skills.lv4.PainSuppression;
import org.academy.internal.common.world.damagesource.DamageTypes;
import org.academy.internal.common.world.damagesource.SkillDamageUtil;

import java.util.List;

import static org.academy.internal.server.ability.gametest.HostileTargetGameTests.check;
import static org.academy.internal.server.ability.gametest.HostileTargetGameTests.player;

@EventBusSubscriber(modid = AcademyCraft.MOD_ID)
public final class PainSuppressionGameTests {
    private static final Identifier TYPE = AcademyCraft.academy("pain_suppression_test_function");

    @SubscribeEvent
    private static void registerType(RegisterEvent event) {
        event.register(Registries.TEST_INSTANCE_TYPE, TYPE, () -> Instance.CODEC);
    }

    @SubscribeEvent
    private static void registerTests(RegisterGameTestsEvent event) {
        for (var scenario : List.of("toggle", "damage", "direct", "insufficient_cp")) {
            var environment = event.registerEnvironment(AcademyCraft.academy("pain_suppression/" + scenario),
                    new TestEnvironmentDefinition.AllOf(List.of()));
            event.registerTest(AcademyCraft.academy("pain_suppression_" + scenario), new Instance(new TestData<>(
                    environment, Identifier.withDefaultNamespace("empty"), 80, 0, true,
                    Rotation.NONE, false, 1, 1, false, 16), scenario));
        }
    }

    private static void prepare(ServerPlayer player) {
        var system = AbilitySystemServer.getSystem(player);
        var uuid = player.getUUID();
        system.setPlayerAbilityCategory(uuid, AbilityCategories.MENTALOUT.get());
        system.setPlayerLevel(uuid, 4);
        system.addPlayerSkill(player, Skills.PAIN_SUPPRESSION.get().getKeyString());
        var data = system.getPlayerData(uuid);
        data.setAcademyMaxCp(1000);
        data.getCpData().setMaxCP(1000);
        data.getCpData().setAvailableCP(1000);
        player.getAttribute(Attributes.MAX_HEALTH).setBaseValue(1000);
        player.setHealth(1000);
        PainSuppression.Server.setActive(player, true);
    }

    private static float intensity(ServerPlayer player) {
        return AbilitySystemServer.getSystem(player).getPlayerCalculationIntensity(player.getUUID());
    }

    private static float cp(ServerPlayer player) {
        return AbilitySystemServer.getSystem(player).getPlayerAvailableCP(player.getUUID());
    }

    private static void near(GameTestHelper helper, double expected, double actual, String message) {
        check(helper, Math.abs(expected - actual) < 0.001, message + ": expected=" + expected + ", actual=" + actual);
    }

    private static void toggle(GameTestHelper helper, ServerPlayer target) {
        near(helper, 1000 - 40 * intensity(target), cp(target), "Enabling reserves exactly 40 CP");
        near(helper, 8, target.getAttributeValue(PlayerAttributes.TRUE_RESISTANCE), "Enabling grants 8 resistance immediately");
        PainSuppression.Server.sync(target);
        near(helper, 1000 - 40 * intensity(target), cp(target), "Maintenance must not accumulate");
        target.getAttribute(PlayerAttributes.TRUE_RESISTANCE).setBaseValue(2);
        PainSuppression.Server.setActive(target, false);
        near(helper, 2, target.getAttributeValue(PlayerAttributes.TRUE_RESISTANCE), "Disabling removes only this skill's modifier");
        near(helper, 1000, cp(target), "Disabling releases maintenance CP");
        PainSuppression.Server.setActive(target, true);
        AbilitySystemServer.getSystem(target).setPlayerAbilityCategory(target.getUUID(), AbilityCategories.ELECTROMASTER.get());
        PainSuppression.Server.sync(target);
        near(helper, 2, target.getAttributeValue(PlayerAttributes.TRUE_RESISTANCE), "Changing category removes the skill modifier");
    }

    private static void hit(GameTestHelper helper, ServerPlayer target, Runnable action, float expectedDamage) {
        target.invulnerableTime = 0;
        var health = target.getHealth();
        var beforeCp = cp(target);
        action.run();
        var damage = health - target.getHealth();
        near(helper, expectedDamage, damage, "Final health loss must follow real resistance");
        near(helper, PainSuppression.damageCpCost(damage) * intensity(target), beforeCp - cp(target),
                "Each hit must charge once, using final damage");
    }

    private static void damage(GameTestHelper helper, ServerPlayer target) {
        var source = target.damageSources().generic();
        hit(helper, target, () -> target.hurtServer(helper.getLevel(), source, 10), 2);
        hit(helper, target, () -> target.hurtServer(helper.getLevel(), source, 15), 3);
        hit(helper, target, () -> target.hurtServer(helper.getLevel(), source, 200), 40);
        target.getAttribute(Attributes.MAX_ABSORPTION).setBaseValue(20);
        target.setAbsorptionAmount(20);
        hit(helper, target, () -> target.hurtServer(helper.getLevel(), source, 5), 0);
    }

    private static void direct(GameTestHelper helper, ServerPlayer target, ServerPlayer attacker) {
        var arc = SkillDamageSource.of(attacker, Skills.ARC_GENERATE.get());
        hit(helper, target, () -> target.hurtServer(helper.getLevel(), arc, 15), 3);
        var melt = SkillDamageSource.of(attacker, Skills.SINGLE_HIGH_SPEED_ELECTRON_BEAM.get());
        hit(helper, target, () -> target.hurtServer(helper.getLevel(), melt, 10), 10);
        var cta = SkillDamageSource.of(attacker, Skills.MIND_DESTRUCTION.get(), DamageTypes.CTA);
        hit(helper, target, () -> SkillDamageUtil.applyVerifiedTrueHealth(target, cta, 20), 7.2f);
    }

    private static void insufficientCp(GameTestHelper helper, ServerPlayer target) {
        var system = AbilitySystemServer.getSystem(target);
        system.getPlayerData(target.getUUID()).getCpData().setAvailableCP(1);
        target.hurtServer(helper.getLevel(), target.damageSources().generic(), 15);
        check(helper, !Skills.PAIN_SUPPRESSION.get().isEnabled(target), "Unaffordable damage charge must disable the skill");
        near(helper, 0, target.getAttributeValue(PlayerAttributes.TRUE_RESISTANCE), "Failed payment must remove resistance immediately");
        near(helper, 1 + 40 * intensity(target), cp(target), "Failed payment must release maintenance without creating negative CP");
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
            var target = player(helper, 2);
            var attacker = player(helper, 8);
            try {
                prepare(target);
                switch (scenario) {
                    case "toggle" -> toggle(helper, target);
                    case "damage" -> damage(helper, target);
                    case "direct" -> direct(helper, target, attacker);
                    case "insufficient_cp" -> insufficientCp(helper, target);
                    default -> throw new IllegalArgumentException(scenario);
                }
                helper.succeed();
            } finally {
                var players = helper.getLevel().getServer().getPlayerList();
                players.remove(target);
                players.remove(attacker);
            }
        }

        @Override
        public MapCodec<? extends GameTestInstance> codec() {
            return CODEC;
        }

        @Override
        protected MutableComponent typeDescription() {
            return Component.literal("Pain suppression final damage regression");
        }
    }
}
