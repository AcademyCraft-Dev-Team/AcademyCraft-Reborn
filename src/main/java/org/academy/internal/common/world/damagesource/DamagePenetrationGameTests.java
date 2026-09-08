package org.academy.internal.common.world.damagesource;

import com.mojang.authlib.GameProfile;
import com.mojang.serialization.MapCodec;
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
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Rotation;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterGameTestsEvent;
import net.neoforged.neoforge.registries.RegisterEvent;
import org.academy.AcademyCraft;
import org.academy.api.common.ability.Skill;
import org.academy.api.common.attribute.PlayerAttributes;
import org.academy.api.common.damage.DamageComposition;
import org.academy.api.common.damage.SkillDamageSource;
import org.academy.internal.common.ability.Skills;
import org.academy.internal.common.attribute.PlayerAttributeRuntime;

import java.util.List;
import java.util.UUID;

/** Exercises loaded damage tags and both the player and mob damage pipelines. */
@EventBusSubscriber(modid = AcademyCraft.MOD_ID)
public final class DamagePenetrationGameTests {
    private static final Identifier TYPE = AcademyCraft.academy("damage_penetration_function");
    private static final EquipmentSlot[] ARMOR = {
            EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET
    };

    private DamagePenetrationGameTests() {}

    @SubscribeEvent
    private static void registerType(RegisterEvent event) {
        event.register(Registries.TEST_INSTANCE_TYPE, TYPE, () -> Instance.CODEC);
    }

    @SubscribeEvent
    private static void registerTests(RegisterGameTestsEvent event) {
        var environment = event.registerEnvironment(TYPE, new TestEnvironmentDefinition.AllOf(List.of()));
        event.registerTest(AcademyCraft.academy("damage_penetration"), new Instance(new TestData<>(
                environment, Identifier.withDefaultNamespace("empty"), 100, 0, true,
                Rotation.NONE, false, 1, 1, false, 16)));
    }

    private static ServerPlayer player(GameTestHelper helper, String name) {
        var level = helper.getLevel();
        var profile = new GameProfile(UUID.randomUUID(), name);
        var cookie = CommonListenerCookie.createInitial(profile, false);
        var player = new ServerPlayer(level.getServer(), level, profile, cookie.clientInformation());
        var connection = new Connection(PacketFlow.SERVERBOUND);
        new EmbeddedChannel(connection);
        level.getServer().getPlayerList().placeNewPlayer(connection, player, cookie);
        player.connection.markClientLoaded();
        player.setGameMode(GameType.SURVIVAL);
        return player;
    }

    private static void reset(LivingEntity target, boolean protection, float absorption) {
        target.removeAllEffects();
        target.stopUsingItem();
        target.invulnerableTime = 0;
        target.getAttribute(Attributes.MAX_HEALTH).setBaseValue(1000);
        target.getAttribute(Attributes.MAX_ABSORPTION).setBaseValue(1000);
        target.getAttribute(Attributes.ARMOR).setBaseValue(30);
        target.getAttribute(Attributes.ARMOR_TOUGHNESS).setBaseValue(20);
        if (target instanceof ServerPlayer player) {
            player.getAttribute(PlayerAttributes.TRUE_RESISTANCE).setBaseValue(0);
        }
        PlayerAttributeRuntime.runWithoutResistance(() -> target.setHealth(1000));
        target.setAbsorptionAmount(absorption);
        var items = List.of(Items.DIAMOND_HELMET, Items.DIAMOND_CHESTPLATE,
                Items.DIAMOND_LEGGINGS, Items.DIAMOND_BOOTS);
        for (var index = 0; index < ARMOR.length; index++) {
            var stack = new ItemStack(items.get(index));
            if (protection) {
                stack.enchant(target.level().registryAccess().lookupOrThrow(Registries.ENCHANTMENT)
                        .getOrThrow(Enchantments.PROTECTION), 4);
            }
            target.setItemSlot(ARMOR[index], stack);
        }
    }

    private static void closeTo(GameTestHelper helper, float actual, float expected, String description) {
        helper.assertTrue(Math.abs(actual - expected) < 0.02f,
                description + ": expected " + expected + ", got " + actual);
    }

    private static void verifyPenetration(GameTestHelper helper, ServerPlayer attacker, LivingEntity target) {
        for (var hit : List.of(
                new Hit(DamageTypes.MELT_DAMAGE, Skills.SINGLE_HIGH_SPEED_ELECTRON_BEAM.get()),
                new Hit(DamageTypes.SPACE_DAMAGE, Skills.THREATENING_TELEPORT.get()),
                new Hit(DamageTypes.MENTAL_DAMAGE, Skills.MIND_DESTRUCTION.get()))) {
            var melt = hit.type == DamageTypes.MELT_DAMAGE;
            var label = target.getType() + " " + hit.type;
            reset(target, true, 50);
            var source = SkillDamageSource.of(attacker, hit.skill, hit.type);
            helper.assertTrue(target.hurtServer(helper.getLevel(), source, 20), label + " must hit");
            closeTo(helper, target.getHealth(), melt ? 1000 : 980, label + " armor/enchantment bypass");
            closeTo(helper, target.getAbsorptionAmount(), melt ? 30 : 50, label + " absorption policy");
            if (melt) {
                helper.assertTrue(!target.hasEffect(MobEffects.NAUSEA),
                        "Absorption-only melt damage must not apply radiation");
            }

            reset(target, true, 0);
            target.addEffect(new MobEffectInstance(MobEffects.RESISTANCE, 200, 0));
            helper.assertTrue(SkillDamageUtil.apply(attacker, target, hit.skill, hit.type, 20),
                    label + " public direct entry must hit");
            closeTo(helper, target.getHealth(), melt ? 980 : 984, label + " resistance potion policy");

            if (target instanceof ServerPlayer defender) {
                reset(target, true, 50);
                defender.getAttribute(PlayerAttributes.TRUE_RESISTANCE).setBaseValue(2);
                target.addEffect(new MobEffectInstance(MobEffects.RESISTANCE, 200, 0));
                if (melt) target.setAbsorptionAmount(0);
                helper.assertTrue(SkillDamageUtil.apply(attacker, target, hit.skill, hit.type, 20, 10),
                        label + " composed damage must hit");
                closeTo(helper, target.getHealth(), melt ? 980 : 987.2f,
                        label + " true resistance policy");
                closeTo(helper, target.getAbsorptionAmount(), melt ? 0 : 50,
                        label + " composed damage preserves absorption");
            }
        }

        reset(target, true, 50);
        var electric = SkillDamageSource.of(attacker, Skills.ELECTRICAL_CONTACT.get());
        target.hurtServer(helper.getLevel(), electric, 20);
        closeTo(helper, target.getHealth(), 1000, "Electric control retains absorption");
        helper.assertTrue(target.getAbsorptionAmount() > 30 && target.getAbsorptionAmount() < 50,
                "Electric control must still be reduced by Protection and consume absorption");

        reset(target, false, 0);
        var darkmatter = SkillDamageSource.of(attacker, Skills.DARKMATTER_CUT.get());
        target.hurtServer(helper.getLevel(), darkmatter, 20);
        helper.assertTrue(target.getHealth() > 980 && target.getHealth() < 1000,
                "Ordinary darkmatter control must retain armor reduction");
    }

    private static void verifyAirRoute(GameTestHelper helper, ServerPlayer attacker, LivingEntity target) {
        for (var hit : List.of(
                new Hit(DamageTypes.VACUUM_SUFFOCATION, Skills.VACUUM_DOMAIN.get()),
                new Hit(DamageTypes.ADIABATIC_COMPRESSION, Skills.ADIABATIC_COMPRESSION.get()))) {
            var label = target.getType() + " " + hit.type;
            reset(target, false, 0);
            target.invulnerableTime = 20;
            var source = SkillDamageSource.of(attacker, hit.skill, hit.type);
            for (var index = 0; index < 2; index++) {
                helper.assertTrue(DamageComposition.hurt(target, helper.getLevel(), source, 20, 20),
                        label + " must bypass hurt cooldown on consecutive hits");
            }
            closeTo(helper, target.getHealth(), 960, label + " retains armor bypass");
            helper.assertTrue(target.getLastDamageSource().is(hit.type), label + " retains type");
            for (var slot : ARMOR) {
                helper.assertTrue(target.getItemBySlot(slot).getDamageValue() == 0,
                        label + " percentage-only hit must not add heavy-cut equipment wear");
            }

            reset(target, true, 50);
            target.hurtServer(helper.getLevel(), source, 20);
            closeTo(helper, target.getHealth(), 1000, label + " retains absorption");
            helper.assertTrue(target.getAbsorptionAmount() > 30 && target.getAbsorptionAmount() < 50,
                    label + " retains Protection enchantment");

            reset(target, false, 0);
            target.addEffect(new MobEffectInstance(MobEffects.RESISTANCE, 200, 0));
            if (target instanceof ServerPlayer defender) {
                defender.getAttribute(PlayerAttributes.TRUE_RESISTANCE).setBaseValue(2);
            }
            target.hurtServer(helper.getLevel(), source, 20);
            closeTo(helper, target.getHealth(), target instanceof ServerPlayer ? 987.2f : 984,
                    label + " retains potion and true resistance");
        }
    }

    private static void verifyDischarge(GameTestHelper helper, ServerPlayer attacker) {
        var target = helper.spawn(EntityTypes.COW, 4, 2, 5);
        target.setNoAi(true);
        reset(target, false, 0);
        try {
            var arc = SkillDamageSource.of(attacker, Skills.ARC_GENERATE.get());
            for (var index = 0; index < 5; index++) {
                target.invulnerableTime = 0;
                target.hurtServer(helper.getLevel(), arc, 1);
            }
            closeTo(helper, target.getHealth(), 993, "Five one-damage hits add exactly two discharge damage");
            helper.assertTrue(CategoryDamageRuntime.electricalCharge(target) == 0,
                    "Discharge must not award itself another charge");
            helper.assertTrue(CategoryDamageRuntime.isParalyzed(target), "Discharge retains paralysis");
            helper.assertTrue(target.getLastDamageSource().is(DamageTypes.ELECTRO_DAMAGE)
                            && target.getLastDamageSource().getEntity() == attacker,
                    "Discharge retains electrical type and attacker");

            var heavy = arc.withElectricalChargePoints(3);
            for (var index = 0; index < 2; index++) {
                target.invulnerableTime = 0;
                target.hurtServer(helper.getLevel(), heavy, 1);
            }
            closeTo(helper, target.getHealth(), 989, "Six more points trigger one two-damage discharge");
            helper.assertTrue(CategoryDamageRuntime.electricalCharge(target) == 1,
                    "Discharge preserves excess charge");
            org.academy.api.common.damage.AbilityHitEffects.addElectricalCharge(target, 9, arc);
            closeTo(helper, target.getHealth(), 985, "Each threshold in a batched charge adds two damage");
            helper.assertTrue(CategoryDamageRuntime.electricalCharge(target) == 0,
                    "Batched discharge cannot recurse");

            var discharge = arc.withElectricalChargePoints(0);
            var reflected = ReflectedSkillDamageSource.from(discharge, attacker, arc.getSkill(), target);
            helper.assertTrue(reflected.electricalChargePoints() == 0,
                    "Reflected discharge must preserve its zero-charge marker");
            var reflectedHeavy = ReflectedSkillDamageSource.from(heavy, attacker, arc.getSkill(), target);
            helper.assertTrue(reflectedHeavy.electricalChargePoints() == 3,
                    "Reflection must also retain explicit primary-hit charge points");

            reset(target, true, 50);
            org.academy.api.common.damage.AbilityHitEffects.addElectricalCharge(target, 5, arc);
            closeTo(helper, target.getHealth(), 1000, "Discharge still respects absorption");
            closeTo(helper, target.getAbsorptionAmount(), 49.28f,
                    "Discharge still respects Protection enchantment");

            reset(target, false, 0);
            target.invulnerableTime = 20;
            org.academy.api.common.damage.AbilityHitEffects.addElectricalCharge(target, 5);
            closeTo(helper, target.getHealth(), 998,
                    "Unattributed public charge API also discharges through direct actuallyHurt");
            helper.assertTrue(target.getLastDamageSource().is(DamageTypes.ELECTRO_DAMAGE),
                    "Unattributed discharge still uses electrodamage");

            reset(target, false, 0);
            target.setHealth(1);
            org.academy.api.common.damage.AbilityHitEffects.addElectricalCharge(target, 5, arc);
            helper.assertTrue(target.isDeadOrDying() && target.getLastDamageSource().getEntity() == attacker,
                    "A lethal discharge retains normal death completion and kill attribution");
        } finally {
            target.discard();
        }
    }

    private static void verify(GameTestHelper helper) {
        var attacker = player(helper, "penetration-owner");
        var defender = player(helper, "penetration-target");
        var cow = helper.spawn(EntityTypes.COW, 3, 2, 5);
        cow.setNoAi(true);
        try {
            for (var target : List.<LivingEntity>of(cow, defender)) {
                verifyPenetration(helper, attacker, target);
                verifyAirRoute(helper, attacker, target);
            }
            verifyDischarge(helper, attacker);
        } finally {
            cow.discard();
            helper.getLevel().getServer().getPlayerList().remove(defender);
            helper.getLevel().getServer().getPlayerList().remove(attacker);
        }
        helper.succeed();
    }

    private record Hit(ResourceKey<DamageType> type, Skill skill) {}

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
            return Component.literal("Category penetration and direct air damage");
        }
    }
}
