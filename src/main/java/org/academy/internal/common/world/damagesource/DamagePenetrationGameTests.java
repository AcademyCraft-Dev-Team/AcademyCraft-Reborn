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
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.animal.cow.Cow;
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
import org.academy.api.server.vanilla.MinecraftServerContext;
import org.academy.api.common.ability.Skill;
import org.academy.api.common.attribute.PlayerAttributes;
import org.academy.api.common.damage.AbilityHitEffects;
import org.academy.api.common.damage.DamageComposition;
import org.academy.api.common.damage.SkillDamageSource;
import org.academy.internal.common.ability.Skills;
import org.academy.internal.common.attribute.PlayerAttributeRuntime;
import org.academy.internal.common.ability.aeromanip.AeromanipTargeting;
import org.academy.mixin.common.LivingEntityDamageInvoker;

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
        org.academy.internal.common.entitycontrol.TrueHealthOffsetRuntime.clear(target);
        org.academy.internal.common.entitycontrol.EntityControlApi.clearTemporaryTrueHealthCap(target);
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
            closeTo(helper, target.getHealth(), 985, "Five one-damage hits add one percent discharge damage");
            helper.assertTrue(CategoryDamageRuntime.electricalCharge(target) == 0,
                    "Discharge must not award itself another charge");
            helper.assertTrue(CategoryDamageRuntime.isParalyzed(target), "Discharge retains paralysis");
            helper.assertTrue(target.getLastDamageSource().is(DamageTypes.ELECTRO_DAMAGE)
                            && target.getLastDamageSource().getEntity() == attacker,
                    "Discharge retains electrical type and attacker");

            helper.assertTrue(target.getLastDamageSource() instanceof SkillDamageSource skillSource
                            && skillSource.getSkill() == Skills.ARC_GENERATE.get()
                            && skillSource.electricalChargePoints() == 0
                            && DamageTypes.usesVerifiedTrueHealth(skillSource),
                    "Discharge must retain skill attribution, zero charge and VEC settlement");
            helper.assertTrue(org.academy.internal.common.ability.mentalout.control.MentalControlRuntime.isFrozen(target),
                    "Discharge must enter the same action gate as mental stupor");

            var heavy = arc.withElectricalChargePoints(3);
            for (var index = 0; index < 2; index++) {
                target.invulnerableTime = 0;
                target.hurtServer(helper.getLevel(), heavy, 1);
            }
            closeTo(helper, target.getHealth(), 973, "Six more points trigger one percent discharge damage");
            helper.assertTrue(CategoryDamageRuntime.electricalCharge(target) == 1,
                    "Discharge preserves excess charge");
            org.academy.api.common.damage.AbilityHitEffects.addElectricalCharge(target, 9, arc);
            closeTo(helper, target.getHealth(), 953, "Each threshold in a batched charge adds one percent damage");
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
            closeTo(helper, target.getHealth(), 990, "VEC-grade discharge bypasses absorption and Protection");
            closeTo(helper, target.getAbsorptionAmount(), 50.0f,
                    "VEC-grade discharge leaves absorption untouched");

            var config = ((MinecraftServerContext) helper.getLevel().getServer())
                    .getAcademyCraftServer().getAbilityConfig();
            var settings = config.electromaster;
            var previousMultiplier = config.damageMultiplier;
            var previousEnabled = settings.paralysisDamageEnabled;
            var previousMinimum = settings.paralysisMinimumDamage;
            var previousFraction = settings.paralysisMaxHealthFraction;
            try {
                settings.paralysisDamageEnabled = false;
                reset(target, false, 0);
                org.academy.api.common.damage.AbilityHitEffects.addElectricalCharge(target, 5, arc);
                closeTo(helper, target.getHealth(), 1000, "Disabled paralysis damage must not hurt");
                helper.assertTrue(CategoryDamageRuntime.electricalInterruptionTicks(target) > 0,
                        "Disabling damage must preserve action interruption");

                settings.paralysisDamageEnabled = true;
                settings.paralysisMinimumDamage = 3;
                settings.paralysisMaxHealthFraction = 0.02f;
                config.damageMultiplier = 3;
                org.academy.api.common.damage.AbilityHitEffects.addElectricalCharge(target, 5, arc);
                closeTo(helper, target.getHealth(), 980, "Server fraction must control damage independently of ordinary multipliers");

                reset(target, false, 0);
                config.damageMultiplier = previousMultiplier;
                target.getAttribute(Attributes.MAX_HEALTH).setBaseValue(100);
                target.setHealth(100);
                org.academy.api.common.damage.AbilityHitEffects.addElectricalCharge(target, 5, arc);
                closeTo(helper, target.getHealth(), 97, "Configured minimum must win on small targets");
            } finally {
                config.damageMultiplier = previousMultiplier;
                settings.paralysisDamageEnabled = previousEnabled;
                settings.paralysisMinimumDamage = previousMinimum;
                settings.paralysisMaxHealthFraction = previousFraction;
            }
            reset(target, false, 0);
            target.invulnerableTime = 20;
            org.academy.api.common.damage.AbilityHitEffects.addElectricalCharge(target, 5);
            closeTo(helper, target.getHealth(), 990,
                    "Unattributed public charge API also uses VEC-grade settlement");
            helper.assertTrue(target.getLastDamageSource().is(DamageTypes.ELECTRO_DAMAGE),
                    "Unattributed discharge still uses electrodamage");

            reset(target, false, 0);
            target.setHealth(1);
            AbilityHitEffects.addElectricalCharge(target, 5, arc);
            helper.assertTrue(target.isDeadOrDying() && target.getLastDamageSource().getEntity() == attacker,
                    "A lethal discharge retains normal death completion and kill attribution");
        } finally {
            target.discard();
        }
    }

    private static void verifyParalysisImmunity(GameTestHelper helper, ServerPlayer attacker) {
        var target = helper.spawn(EntityTypes.COW, 6, 2, 5);
        target.setNoAi(true);
        var source = SkillDamageSource.of(attacker, Skills.ARC_GENERATE.get());
        var settings = ((MinecraftServerContext) helper.getLevel().getServer())
                .getAcademyCraftServer().getAbilityConfig().electromaster;
        var previousEnabled = settings.paralysisDamageEnabled;
        var registry = net.minecraft.core.registries.BuiltInRegistries.ENTITY_TYPE;
        var originalTags = new java.util.HashMap<net.minecraft.tags.TagKey<net.minecraft.world.entity.EntityType<?>>,
                List<Holder<net.minecraft.world.entity.EntityType<?>>>>();
        registry.listTags().forEach(tag -> originalTags.put(tag.key(), tag.stream().toList()));
        try {
            target.setInvulnerable(true);
            for (var enabled : new boolean[]{true, false}) {
                settings.paralysisDamageEnabled = enabled;
                helper.assertTrue(CategoryDamageRuntime.addCharge(target, 5, source) == 0,
                        "Invulnerable targets must reject paralysis even when extra damage is disabled");
                helper.assertTrue(!CategoryDamageRuntime.isParalyzed(target)
                                && CategoryDamageRuntime.electricalInterruptionTicks(target) == 0,
                        "Rejected damage must not install either paralysis timer");
                closeTo(helper, target.getHealth(), 10, "Invulnerability must preserve health");
            }
            target.setInvulnerable(false);
            try (var defense = org.academy.api.server.entity.SurvivalDefense.acquire(target,
                    org.academy.api.server.entity.SurvivalDefenseProfile.absolute(target.getHealth()))) {
                for (var enabled : new boolean[]{true, false}) {
                    settings.paralysisDamageEnabled = enabled;
                    helper.assertTrue(CategoryDamageRuntime.addCharge(target, 5, source) == 0,
                            "Absolute damage protection must also block the interruption");
                    helper.assertTrue(!CategoryDamageRuntime.isParalyzed(target),
                            "Absolute protection must not receive paralysis");
                }
            }
            settings.paralysisDamageEnabled = true;
            var config = ((MinecraftServerContext) helper.getLevel().getServer())
                    .getAcademyCraftServer().getAbilityConfig();
            var previousMultiplier = config.damageMultiplier;
            try {
                config.damageMultiplier = 0.0f;
                helper.assertTrue(CategoryDamageRuntime.addCharge(target, 5, source) == 0,
                        "A zero-damage settlement must not trigger paralysis");
                helper.assertTrue(CategoryDamageRuntime.electricalInterruptionTicks(target) == 0,
                        "Rejected settlement must not install an action lock");
            } finally {
                config.damageMultiplier = previousMultiplier;
            }
            var testTags = new java.util.HashMap<>(originalTags);
            var immuneTypes = new java.util.ArrayList<>(originalTags.getOrDefault(
                    org.academy.api.common.entitycontrol.MentalControlTags.IMMUNE, List.of()));
            immuneTypes.add(EntityTypes.COW.builtInRegistryHolder());
            testTags.put(org.academy.api.common.entitycontrol.MentalControlTags.IMMUNE, immuneTypes);
            registry.prepareTagReload(new net.minecraft.tags.TagLoader.LoadResult<>(registry.key(), testTags)).apply();
            helper.assertTrue(org.academy.internal.common.ability.mentalout.control.MentalControlRuntime
                            .isProtectedTarget(target),
                    "Fixture must be immune to mental control");
            helper.assertTrue(CategoryDamageRuntime.addCharge(target, 5, source) == 1,
                    "Mental protection must not prevent electrical paralysis");
            closeTo(helper, target.getHealth(), 8, "Mental protection must not prevent electrical damage");
            helper.assertTrue(org.academy.internal.common.ability.mentalout.control.MentalControlRuntime
                            .isFrozen(target),
                    "Mentally protected targets still use the stupor action gate after electrical damage");
        } finally {
            settings.paralysisDamageEnabled = previousEnabled;
            registry.prepareTagReload(new net.minecraft.tags.TagLoader.LoadResult<>(registry.key(), originalTags)).apply();
            target.discard();
        }
    }

    private static void verifyDirectEntryValidation(GameTestHelper helper, ServerPlayer attacker) {
        var level = helper.getLevel();
        var target = new CountingCow(level);
        target.getAttribute(Attributes.MAX_HEALTH).setBaseValue(20);
        target.setNoAi(true);
        target.setPos(helper.absoluteVec(new net.minecraft.world.phys.Vec3(4, 2, 5)));
        level.addFreshEntity(target);
        var skill = Skills.SINGLE_HIGH_SPEED_ELECTRON_BEAM.get();
        var source = SkillDamageSource.of(attacker, skill, DamageTypes.MELT_DAMAGE);
        var invoker = (LivingEntityDamageInvoker) (Object) target;
        try {
            target.setHealth(1);
            helper.assertTrue(SkillDamageUtil.applyDirect(level, target, source, 20),
                    "The first lethal direct hit must complete normally");
            helper.assertTrue(invoker.academy$isDead() && target.damageCalls == 1,
                    "The first hit must enter actuallyHurt once and finish vanilla death");
            // Multipart forwarding and lingering displacement marks can both reach the same
            // corpse again before the death animation removes it from the level.
            for (var index = 0; index < 3; index++) {
                helper.assertTrue(!target.hurtServer(level, source, 20),
                        "Forwarded damage to a dead parent must be rejected");
                helper.assertTrue(!SkillDamageUtil.applyDirect(level, target, source, 20),
                        "A lingering direct-damage mark must not hit a corpse");
            }
            helper.assertTrue(target.damageCalls == 1,
                    "Rejected corpse hits must never enter actuallyHurt, even if its exception is caught");
            helper.assertTrue(!AeromanipTargeting.canAffectNegatively(attacker, target),
                    "Airflow must stop selecting a dead target before it is removed");
            helper.assertTrue(invoker.academy$getDamageContainers().isEmpty(),
                    "Rejected hits must leave the damage-container stack empty");

            invoker.academy$setDead(false);
            target.setHealth(20);
            for (var amount : new float[]{0, -1, Float.NaN, Float.POSITIVE_INFINITY}) {
                helper.assertTrue(!SkillDamageUtil.applyDirect(level, target, source, amount),
                        "Invalid direct damage must be rejected: " + amount);
                helper.assertTrue(!target.hurtServer(level, source, amount),
                        "Invalid forwarded damage must be rejected: " + amount);
            }
            helper.assertTrue(target.damageCalls == 1 && target.getHealth() == 20,
                    "Invalid amounts must not enter the mutable damage pipeline");
            helper.assertTrue(SkillDamageUtil.applyDirect(level, target, source, 2),
                    "A living target must still accept a valid hit after rejected calls");
            closeTo(helper, target.getHealth(), 18, "Valid damage after rejected calls");
        } finally {
            target.discard();
        }
    }

    private static final class CountingCow extends Cow {
        private int damageCalls;

        private CountingCow(ServerLevel level) {
            super(EntityTypes.COW, level);
        }

        @Override
        protected void actuallyHurt(ServerLevel level, DamageSource source, float amount) {
            damageCalls++;
            super.actuallyHurt(level, source, amount);
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
            verifyParalysisImmunity(helper, attacker);
            verifyDirectEntryValidation(helper, attacker);
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
