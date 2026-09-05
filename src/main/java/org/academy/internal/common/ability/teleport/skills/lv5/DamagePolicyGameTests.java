package org.academy.internal.common.ability.teleport.skills.lv5;

import com.mojang.authlib.GameProfile;
import com.mojang.serialization.MapCodec;
import io.netty.channel.embedded.EmbeddedChannel;
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
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Rotation;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterGameTestsEvent;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.registries.RegisterEvent;
import org.academy.AcademyCraft;
import org.academy.api.common.attribute.PlayerAttributes;
import org.academy.api.common.damage.DamageComposition;
import org.academy.api.common.damage.SkillDamageSource;
import org.academy.internal.common.ability.Skills;
import org.academy.internal.common.world.damagesource.DamageTypes;
import org.academy.internal.common.world.damagesource.SkillDamageUtil;

import java.util.List;
import java.util.UUID;

/** Runs with the live NeoForge event bus and applied damage mixins. */
@EventBusSubscriber(modid = AcademyCraft.MOD_ID)
public final class DamagePolicyGameTests {
    private static final Identifier TYPE = AcademyCraft.academy("damage_policy_function");
    private static UUID targetId;
    private static boolean weaken;

    @SubscribeEvent
    private static void registerType(RegisterEvent event) {
        event.register(Registries.TEST_INSTANCE_TYPE, TYPE, () -> DamageTest.CODEC);
    }

    @SubscribeEvent
    private static void registerTests(RegisterGameTestsEvent event) {
        var environment = event.registerEnvironment(TYPE, new TestEnvironmentDefinition.AllOf(List.of()));
        event.registerTest(AcademyCraft.academy("damage_policy"), new DamageTest(new TestData<>(
                environment, Identifier.withDefaultNamespace("empty"), 100, 0, true,
                Rotation.NONE, false, 1, 1, false, 16)));
    }

    @SubscribeEvent
    public static void incoming(LivingIncomingDamageEvent event) {
        if (!event.getEntity().getUUID().equals(targetId)) return;
        event.setAmount(event.getAmount() * 2.0f);
        if (weaken) {
            event.setAmount(0.0f);
            event.setCanceled(true);
        }
        event.setAmount(event.getAmount() + 3.0f);
    }

    @SubscribeEvent
    public static void pre(LivingDamageEvent.Pre event) {
        if (event.getEntity().getUUID().equals(targetId)) event.setNewDamage(weaken ? 0.0f : event.getNewDamage() * 2.0f);
    }

    private static void verify(GameTestHelper helper) {
        var level = helper.getLevel();
        var profile = new GameProfile(UUID.randomUUID(), "amplification-test");
        var cookie = CommonListenerCookie.createInitial(profile, false);
        var player = new ServerPlayer(level.getServer(), level, profile, cookie.clientInformation());
        var connection = new Connection(PacketFlow.SERVERBOUND);
        new EmbeddedChannel(connection);
        level.getServer().getPlayerList().placeNewPlayer(connection, player, cookie);
        player.connection.markClientLoaded();
        player.setGameMode(GameType.SURVIVAL);
        try {
            for (var type : List.of(DamageTypes.DM_DAMAGE, DamageTypes.AERO_DAMAGE, DamageTypes.ELECTRO_DAMAGE, DamageTypes.MELT_DAMAGE,
                    DamageTypes.SPACE_DAMAGE, DamageTypes.MENTAL_DAMAGE, DamageTypes.VEC, DamageTypes.CTA)) {
                for (var purePercentage : List.of(false, true)) {
                    var target = helper.spawn(EntityTypes.COW, 3, 2, 5);
                    target.setNoAi(true);
                    target.getAttribute(Attributes.MAX_HEALTH).setBaseValue(1000.0);
                    target.setHealth(1000.0f);
                    targetId = target.getUUID();
                    var source = SkillDamageSource.of(player, Skills.ELECTRICAL_CONTACT.get(), type);
                    weaken = purePercentage;
                    var total = purePercentage ? 50.0f : 60.0f;
                    DamageComposition.withMaximumHealthPart(target, source, 50.0f,
                            () -> DamageTypes.usesVerifiedTrueHealth(type)
                                    ? SkillDamageUtil.applyVerifiedTrueHealth(target, source, total)
                                    : target.hurtServer(level, source, total));
                    var expected = purePercentage ? 950.0f : 940.0f;
                    helper.assertTrue(Math.abs(target.getHealth() - expected) < 0.01f,
                            type.toString() + " percentageOnly=" + purePercentage
                                    + " expected health " + expected + " actual " + target.getHealth());
                    helper.assertTrue(target.getLastDamageSource() != null && target.getLastDamageSource().is(type),
                            "Amplification must retain the original special damage type");
                    target.discard();
                }
            }

            targetId = null;
            verifyChestItem(helper, player);
            verifyCategoryEffects(helper, player);
            var resistance = player.getAttribute(PlayerAttributes.TRUE_RESISTANCE);
            resistance.setBaseValue(1.0);
            Flashing.Server.beginDashInvulnerability(player);
            helper.assertTrue(resistance.getValue() == 8.0, "Dash must immediately provide 8 true resistance");
            var now = level.getGameTime();
            Flashing.Server.completeDashInvulnerability(player.getUUID(), now);
            helper.assertTrue(resistance.getValue() == 8.0, "Grace ticks must retain dash resistance");
            Flashing.Server.clearDashInvulnerability(player.getUUID());
            helper.assertTrue(resistance.getValue() == 1.0, "Ending dash must retain pre-existing resistance");
            Flashing.Server.beginDashInvulnerability(player);
            Flashing.Server.cancelDashInvulnerability(player.getUUID(), now);
            helper.assertTrue(resistance.getValue() == 1.0, "Failed dash must remove its resistance");
        } finally {
            targetId = null;
            Flashing.Server.clearDashInvulnerability(player.getUUID());
            level.getServer().getPlayerList().remove(player);
        }
        helper.succeed();
    }

    private static void verifyChestItem(GameTestHelper helper, ServerPlayer player) {
        if (!net.neoforged.fml.ModList.get().isLoaded("chest_item")) return;
        try {
            var handler = Class.forName("com.ytgld.chest_item.Handler");
            var inventory = (net.minecraft.world.Container) handler
                    .getMethod("getItem", net.minecraft.world.entity.player.Player.class).invoke(null, player);
            helper.assertTrue(inventory != null, "Chest Item must attach its actual player inventory");
            inventory.setItem(0, net.minecraft.core.registries.BuiltInRegistries.ITEM
                    .getValue(Identifier.parse("chest_item:stronger_stone")).getDefaultInstance());
            inventory.setItem(1, net.minecraft.core.registries.BuiltInRegistries.ITEM
                    .getValue(Identifier.parse("chest_item:fission_emblem")).getDefaultInstance());
            var config = (java.util.function.Supplier<?>) Class
                    .forName("com.ytgld.chest_item.items.gold.StrongerStone$ConfigItem")
                    .getField("intValue").get(null);
            var multiplier = ((Number) config.get()).floatValue() * 1.1f;
            for (var type : List.of(DamageTypes.DM_DAMAGE, DamageTypes.AERO_DAMAGE, DamageTypes.ELECTRO_DAMAGE, DamageTypes.MELT_DAMAGE,
                    DamageTypes.SPACE_DAMAGE, DamageTypes.MENTAL_DAMAGE, DamageTypes.VEC, DamageTypes.CTA)) {
                var target = helper.spawn(EntityTypes.COW, 3, 2, 5);
                target.setNoAi(true);
                target.getAttribute(Attributes.MAX_HEALTH).setBaseValue(1000.0);
                target.setHealth(1000.0f);
                player.snapTo(target.getX() - 2, target.getY(), target.getZ(), 0, 0);
                var source = SkillDamageSource.of(player, Skills.ELECTRICAL_CONTACT.get(), type);
                DamageComposition.withMaximumHealthPart(target, source, 50.0f,
                        () -> DamageTypes.usesVerifiedTrueHealth(type)
                                ? SkillDamageUtil.applyVerifiedTrueHealth(target, source, 60.0f)
                                : target.hurtServer(helper.getLevel(), source, 60.0f));
                var expected = 940.0f;
                helper.assertTrue(Math.abs(target.getHealth() - expected) < 0.01f,
                        "Chest Item event modifiers must not change Academy damage: " + type
                                + " expected " + expected + " got " + target.getHealth());
                target.discard();
            }
            inventory.clearContent();
        } catch (ReflectiveOperationException error) {
            throw new IllegalStateException("Chest Item compatibility fixture failed", error);
        }
    }


    private static void verifyCategoryEffects(GameTestHelper helper, ServerPlayer player) {
        var level = helper.getLevel();
        var electric = helper.spawn(EntityTypes.COW, 3, 2, 5);
        electric.setNoAi(true);
        electric.getAttribute(Attributes.MAX_HEALTH).setBaseValue(1000);
        electric.setHealth(1000);
        var arc = SkillDamageSource.of(player, Skills.ARC_GENERATE.get());
        for (var hit = 0; hit < 4; hit++) {
            electric.invulnerableTime = 0;
            electric.hurtServer(level, arc, 1);
        }
        helper.assertTrue(org.academy.api.common.damage.AbilityHitEffects.electricalCharge(electric) == 4,
                "Four ordinary arc hits must accumulate four points");
        var originalSpeed = electric.getAttributeValue(Attributes.MOVEMENT_SPEED);
        electric.invulnerableTime = 0;
        electric.hurtServer(level, arc, 1);
        helper.assertTrue(org.academy.api.common.damage.AbilityHitEffects.isParalyzed(electric),
                "Fifth charge point must trigger paralysis");
        helper.assertTrue(org.academy.api.common.damage.AbilityHitEffects.electricalCharge(electric) == 0,
                "Discharge must consume five points");
        helper.assertTrue(Math.abs(electric.getAttributeValue(Attributes.MOVEMENT_SPEED) - originalSpeed * 0.2) < 1e-6,
                "Paralysis must reduce movement speed by exactly 80%");
        var receiver = helper.spawn(EntityTypes.COW, 4, 2, 5);
        receiver.setNoAi(true);
        receiver.hurtServer(level, electric.damageSources().mobAttack(electric), 10);
        helper.assertTrue(Math.abs(receiver.getHealth() - (receiver.getMaxHealth() - 8)) < 0.01,
                "Paralyzed ordinary attacks must lose exactly 20% damage");
        receiver.discard();
        electric.discard();

        var blocked = helper.spawn(EntityTypes.COW, 3, 2, 5);
        blocked.getAttribute(Attributes.MAX_ABSORPTION).setBaseValue(100);
        blocked.setAbsorptionAmount(100);
        blocked.hurtServer(level, arc, 5);
        helper.assertTrue(org.academy.api.common.damage.AbilityHitEffects.electricalCharge(blocked) == 0,
                "An absorption-only hit must not award electrical charge");
        blocked.discard();

        var irradiated = helper.spawn(EntityTypes.COW, 3, 2, 5);
        irradiated.getAttribute(Attributes.MAX_HEALTH).setBaseValue(1000);
        irradiated.setHealth(1000);
        irradiated.addEffect(new net.minecraft.world.effect.MobEffectInstance(
                net.minecraft.world.effect.MobEffects.RESISTANCE, 400, 4));
        irradiated.addEffect(new net.minecraft.world.effect.MobEffectInstance(
                net.minecraft.world.effect.MobEffects.NAUSEA, 400, 2));
        SkillDamageUtil.apply(player, irradiated, Skills.SINGLE_HIGH_SPEED_ELECTRON_BEAM.get(),
                DamageTypes.MELT_DAMAGE, 10);
        helper.assertTrue(Math.abs(irradiated.getHealth() - 990) < 0.01,
                "Meltdowner must ignore resistance V");
        for (var effect : List.of(net.minecraft.world.effect.MobEffects.NAUSEA,
                net.minecraft.world.effect.MobEffects.SLOWNESS,
                net.minecraft.world.effect.MobEffects.MINING_FATIGUE,
                net.minecraft.world.effect.MobEffects.WEAKNESS)) {
            helper.assertTrue(irradiated.hasEffect(effect), "Successful melt hit must apply all radiation effects");
        }
        helper.assertTrue(irradiated.getEffect(net.minecraft.world.effect.MobEffects.NAUSEA).getAmplifier() == 2,
                "Radiation must preserve a stronger existing debuff");
        irradiated.discard();

        var lawTarget = helper.spawn(EntityTypes.COW, 3, 2, 5);
        lawTarget.getAttribute(Attributes.MAX_HEALTH).setBaseValue(1000);
        lawTarget.setHealth(1000);
        lawTarget.getAttribute(Attributes.ARMOR).setBaseValue(1000);
        lawTarget.getAttribute(Attributes.MAX_ABSORPTION).setBaseValue(1000);
        lawTarget.setAbsorptionAmount(1000);
        lawTarget.addEffect(new net.minecraft.world.effect.MobEffectInstance(
                net.minecraft.world.effect.MobEffects.RESISTANCE, 400, 4));
        var law = new org.academy.api.common.damage.LawDetonationDamageSource(
                SkillDamageSource.of(player, Skills.DARKMATTER_CUT.get()));
        SkillDamageUtil.applyDirect(level, lawTarget, law, 10);
        helper.assertTrue(Math.abs(lawTarget.getHealth() - 990) < 0.01,
                "Law detonation must subtract health through armor, resistance and absorption");
        helper.assertTrue(lawTarget.getLastDamageSource().is(DamageTypes.DM_DAMAGE),
                "Law detonation must retain darkmatter category identity");
        lawTarget.discard();

        var armored = helper.spawn(EntityTypes.COW, 3, 2, 5);
        armored.getAttribute(Attributes.MAX_HEALTH).setBaseValue(1000);
        armored.setHealth(1000);
        var chest = new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.DIAMOND_CHESTPLATE);
        armored.setItemSlot(net.minecraft.world.entity.EquipmentSlot.CHEST, chest);
        var aero = SkillDamageSource.of(player, Skills.LAMINAR_CUTTER.get(), DamageTypes.LAMINAR_CUT);
        armored.hurtServer(level, aero, 10);
        helper.assertTrue(chest.getDamageValue() >= 9, "Air cutting must add durability wear to actual hits");
        var beforeWear = chest.getDamageValue();
        org.academy.api.common.damage.AbilityHitEffects.damageEquipment(armored, 80, true);
        var heavyWear = chest.getDamageValue();
        org.academy.api.common.damage.AbilityHitEffects.damageEquipment(armored, 80, true);
        helper.assertTrue(heavyWear > beforeWear && chest.getDamageValue() == heavyWear,
                "Repeated heavy cuts must share the same short durability budget");
        armored.discard();

        var profile = new GameProfile(UUID.randomUUID(), "category-defender");
        var cookie = CommonListenerCookie.createInitial(profile, false);
        var defender = new ServerPlayer(level.getServer(), level, profile, cookie.clientInformation());
        var connection = new Connection(PacketFlow.SERVERBOUND);
        new EmbeddedChannel(connection);
        level.getServer().getPlayerList().placeNewPlayer(connection, defender, cookie);
        defender.connection.markClientLoaded();
        defender.setGameMode(GameType.SURVIVAL);
        try {
            org.academy.internal.common.world.damagesource.PvpSetting.trySetPvpEnabled(player, true);
            org.academy.internal.common.world.damagesource.PvpSetting.trySetPvpEnabled(defender, true);
            defender.getAttribute(Attributes.MAX_HEALTH).setBaseValue(1000);
            defender.setHealth(1000);
            defender.getAttribute(PlayerAttributes.TRUE_RESISTANCE).setBaseValue(8);
            defender.addEffect(new net.minecraft.world.effect.MobEffectInstance(
                    net.minecraft.world.effect.MobEffects.RESISTANCE, 400, 4));
            SkillDamageUtil.apply(player, defender, Skills.SINGLE_HIGH_SPEED_ELECTRON_BEAM.get(),
                    DamageTypes.MELT_DAMAGE, 10);
            helper.assertTrue(Math.abs(defender.getHealth() - 990) < 0.01,
                    "Meltdowner must bypass player true resistance and resistance potion together; health="
                            + defender.getHealth() + ", max=" + defender.getMaxHealth());
            defender.setGameMode(GameType.CREATIVE);
            var before = defender.getHealth();
            SkillDamageUtil.apply(player, defender, Skills.SINGLE_HIGH_SPEED_ELECTRON_BEAM.get(),
                    DamageTypes.MELT_DAMAGE, 10);
            helper.assertTrue(defender.getHealth() == before, "Damage isolation must retain creative immunity");
        } finally {
            level.getServer().getPlayerList().remove(defender);
        }

        var zombie = helper.spawn(EntityTypes.ZOMBIE, 3, 2, 5);
        zombie.setNoAi(true);
        var drops = new java.util.ArrayList<net.minecraft.world.entity.item.ItemEntity>();
        var space = SkillDamageSource.of(player, Skills.THREATENING_TELEPORT.get());
        var dropEvent = new net.neoforged.neoforge.event.entity.living.LivingDropsEvent(zombie, space, drops, true);
        long seed = 0;
        for (; seed < 100_000; seed++) {
            zombie.getRandom().setSeed(seed);
            if (zombie.getRandom().nextFloat() < 0.25f) break;
        }
        zombie.getRandom().setSeed(seed);
        org.academy.internal.common.world.damagesource.CategoryDamageRuntime.onDrops(dropEvent);
        helper.assertTrue(drops.size() == 1 && drops.getFirst().getItem().is(net.minecraft.world.item.Items.ZOMBIE_HEAD),
                "Successful teleport lethal roll must add the matching head");
        zombie.getRandom().setSeed(seed);
        org.academy.internal.common.world.damagesource.CategoryDamageRuntime.onDrops(dropEvent);
        helper.assertTrue(drops.size() == 1, "Teleport roll must not duplicate an existing head");
        zombie.discard();
    }

    private static final class DamageTest extends GameTestInstance {
        private static final MapCodec<DamageTest> CODEC = TestData.CODEC.xmap(DamageTest::new, DamageTest::info);

        private DamageTest(TestData<Holder<TestEnvironmentDefinition<?>>> info) {
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
            return Component.literal("Isolated category damage and flash resistance");
        }
    }
}
