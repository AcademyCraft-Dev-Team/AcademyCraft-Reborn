package org.academy.internal.server.ability.gametest;

import com.mojang.authlib.GameProfile;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
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
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.player.Input;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterGameTestsEvent;
import net.neoforged.neoforge.registries.RegisterEvent;
import org.academy.AcademyCraft;
import org.academy.api.common.attribute.PlayerAttributes;
import org.academy.api.common.damage.SkillDamageSource;
import org.academy.internal.common.ability.Skills;
import org.academy.internal.common.ability.accelerator.reflection.*;
import org.academy.internal.common.ability.electromaster.ElectromasterArcActions;
import org.academy.internal.common.ability.electromaster.skills.lv3.MagnetManipulation;
import org.academy.internal.common.ability.meltdowner.ContinuousBeamReflection;
import org.academy.internal.common.ability.meltdowner.ContinuousReflectionSession;
import org.academy.internal.common.attachment.AttachmentTypes;
import org.academy.internal.common.world.entity.skill.HighSpeedElectronBeam;

import org.academy.api.server.ability.*;
import org.academy.api.server.ability.electromaster.*;
import org.academy.api.server.damage.HealthLossGuards;
import org.academy.api.common.entitycontrol.MentalImmunity;
import org.academy.api.common.damage.AbilityHitEffects;
import org.academy.internal.common.entitycontrol.EntityControlApi;
import org.academy.internal.common.world.damagesource.CTAEntityActuallyHurt;
import org.academy.internal.common.ability.AbilityCategories;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.ItemStack;
import java.util.List;
import java.util.UUID;

/** Exercises the live damage mixins, beam resolver and server magnetic movement. */
@EventBusSubscriber(modid = AcademyCraft.MOD_ID)
public final class ElectromasterReworkGameTests {
    private static final Identifier TYPE = AcademyCraft.academy("electromaster_rework_regression_function");

    @SubscribeEvent
    private static void registerType(RegisterEvent event) {
        event.register(Registries.TEST_INSTANCE_TYPE, TYPE, () -> Instance.CODEC);
    }

    @SubscribeEvent
    private static void registerTests(RegisterGameTestsEvent event) {
        for (var scenario : List.of("health", "resources", "attacks", "projectiles", "support")) {
            var environment = event.registerEnvironment(AcademyCraft.academy("electromaster_rework/" + scenario),
                    new TestEnvironmentDefinition.AllOf(List.of()));
            event.registerTest(AcademyCraft.academy("electromaster_rework_" + scenario), new Instance(new TestData<>(
                    environment, Identifier.withDefaultNamespace("empty"), 100, 0, true,
                    Rotation.NONE, false, 1, 1, false, 16), scenario));
        }
    }

    private static ServerPlayer player(GameTestHelper helper, int x, int y, int z) {
        var profile = new GameProfile(UUID.randomUUID(), "shield-" + UUID.randomUUID().toString().substring(0, 8));
        var cookie = CommonListenerCookie.createInitial(profile, false);
        var player = new ServerPlayer(helper.getLevel().getServer(), helper.getLevel(),
                profile, cookie.clientInformation());
        var connection = new Connection(PacketFlow.SERVERBOUND);
        new EmbeddedChannel(connection);
        helper.getLevel().getServer().getPlayerList().placeNewPlayer(connection, player, cookie);
        player.connection.markClientLoaded();
        player.setGameMode(GameType.SURVIVAL);
        var position = helper.absoluteVec(new Vec3(x, y, z));
        player.snapTo(position.x, position.y, position.z, 0, 0);
        player.setNoGravity(true);
        return player;
    }

    private static void check(GameTestHelper helper, boolean condition, String message) {
        helper.assertTrue(condition, Component.literal(message));
    }

    private static void close(GameTestHelper helper, double expected, double actual, String message) {
        check(helper, Math.abs(expected - actual) < 0.015, message + ": expected=" + expected + ", actual=" + actual);
    }

    private static AbilitySystemServer learn(ServerPlayer player) {
        var system = AbilitySystemServer.getSystem(player);
        system.setPlayerAbilityCategory(player.getUUID(), AbilityCategories.ELECTROMASTER.get());
        system.setPlayerLevel(player.getUUID(), 5);
        system.setPlayerMaxCP(player.getUUID(), 10000);
        system.setPlayerAvailableCP(player.getUUID(), 10000);
        system.addPlayerSkill(player, Skills.MAGNET_MANIPULATION.get().getKeyString());
        system.addPlayerSkill(player, Skills.IRON_SAND_ARSENAL.get().getKeyString());
        system.getIronSandResourceService().reconcileCapacity(player);
        return system;
    }

    private static void health(GameTestHelper helper, ServerPlayer attacker, ServerPlayer defender) throws ReflectiveOperationException {
        var npc = helper.spawn(EntityTypes.COW, 6, 4, 6);
        npc.getAttribute(Attributes.MAX_HEALTH).setBaseValue(100);
        npc.setHealth(100);
        var account = new TestAccount(100);
        var key = AcademyCraft.academy("test_sand");
        HealthLossGuards.set(npc, key, account, 0.9, true);
        npc.setHealth(90);
        close(helper, 100, npc.getHealth(), "setHealth is protected");
        close(helper, 91, account.current(), "setHealth pays once");
        var accessor = org.academy.mixin.common.LivingHealthDataAccessor.academy$healthAccessor();
        npc.getEntityData().set(accessor, 90f);
        close(helper, 82, account.current(), "Direct synchronized health write pays once");
        var items = net.minecraft.network.syncher.SynchedEntityData.class.getDeclaredField("itemsById");
        items.setAccessible(true);
        var item = (net.minecraft.network.syncher.SynchedEntityData.DataItem<Float>)
                ((Object[]) items.get(npc.getEntityData()))[accessor.id()];
        item.setValue(90f);
        close(helper, 73, account.current(), "Direct DataItem value setter is protected");
        npc.hurtServer(helper.getLevel(), npc.damageSources().generic(), 10);
        close(helper, 100, npc.getHealth(), "Ordinary damage does not lose health with sufficient MP");
        close(helper, 64, account.current(), "Ordinary damage pays once");
        EntityControlApi.forceSetTrueHealth(npc, 90);
        close(helper, 55, account.current(), "True health writer pays once");
        var source = SkillDamageSource.of(attacker, Skills.IRON_SAND_ARSENAL.get());
        new CTAEntityActuallyHurt(npc).actuallyHurt(source, 10, true);
        close(helper, 46, account.current(), "CTA nested submission pays once");
        close(helper, 100, npc.getHealth(), "CTA fully absorbed does not install a lower projection");
        var before = account.current();
        check(helper, !HealthLossGuards.commit(npc, 100, 90, _ -> false), "Rejected writer reports failure");
        close(helper, before, account.current(), "Rejected writer refunds");
        HealthLossGuards.maintenance(npc, () -> { npc.setHealth(99); return null; });
        close(helper, before, account.current(), "Maintenance does not charge");
        npc.setHealth(100);
        account.mass = 4.5;
        npc.setHealth(90);
        close(helper, 95, npc.getHealth(), "Partial MP preserves only affordable health");
        close(helper, 0, account.current(), "Partial MP is exhausted");
        npc.setHealth(0);
        close(helper, 0, npc.getHealth(), "Zero MP permits lethal loss");
        HealthLossGuards.set(npc, key, account, 1, false);
        var custom = new NotifyingCow(helper.getLevel());
        custom.getAttribute(Attributes.MAX_HEALTH).setBaseValue(100);
        custom.setHealth(100);
        var customMass = new TestAccount(30);
        HealthLossGuards.set(custom, key, customMass, 1, true);
        new CTAEntityActuallyHurt(custom).actuallyHurt(source, 10, true);
        close(helper, 100, custom.getHealth(), "Custom notification is restored before CTA settlement");
        close(helper, 20, customMass.current(), "Custom hurt notification cannot charge a second time");
        check(helper, custom.notifications == 1, "Custom hurt callback still executes");
        HealthLossGuards.set(custom, key, customMass, 1, false);
        var projected = helper.spawn(EntityTypes.COW, 7, 4, 7);
        projected.getAttribute(Attributes.MAX_HEALTH).setBaseValue(100);
        projected.setHealth(100);
        check(helper, org.academy.internal.common.entitycontrol.TrueHealthOffsetRuntime.install(projected, 80), "Projection setup");
        var projectedMass = new TestAccount(100);
        HealthLossGuards.set(projected, key, projectedMass, 1, true);
        projected.hurtServer(helper.getLevel(), projected.damageSources().generic(), 20);
        close(helper, 80, projected.getHealth(), "Ordinary damage respects existing projection");
        close(helper, 80, projectedMass.current(), "Existing projected loss is not charged again");
        HealthLossGuards.set(projected, key, projectedMass, 1, false);
        org.academy.internal.common.entitycontrol.TrueHealthOffsetRuntime.clear(projected);
    }

    private static void resources(GameTestHelper helper, ServerPlayer attacker, ServerPlayer defender) {
        var system = learn(defender);
        var manager = system.getIronSandResourceManager();
        var account = manager.account(defender);
        var power = system.getPlayerAbilityPowerMultiplier(defender.getUUID());
        close(helper, 100 * power, account.capacity(), "Learned capacity follows A");
        close(helper, account.capacity(), account.current(), "First learn starts full");
        account.tryConsume(30);
        var before = account.current();
        check(helper, manager.setDefense(defender, true), "Defense can start");
        manager.setDefense(defender, false);
        manager.setDefense(defender, true);
        close(helper, before, account.current(), "Toggling does not refill MP");
        manager.tick(defender);
        var recovered = account.current();
        manager.tick(defender);
        close(helper, recovered, account.current(), "Duplicate tick cannot double regeneration");
        check(helper, recovered > before, "Recovery continues while defense is active");
        system.setPlayerSkillProficiency(defender.getUUID(), Skills.IRON_SAND_ARSENAL.get(), 2000);
        manager.reconcileCapacity(defender);
        close(helper, 120 * power, account.capacity(), "2000 expands capacity");
        close(helper, recovered, account.current(), "Capacity growth does not refill");
        manager.onPlayerLogout(defender);
        manager.onPlayerLogin(defender);
        close(helper, recovered, account.current(), "Relog does not refill");
        check(helper, !manager.isDefenseActive(defender), "Relog starts with defense off");
        MagneticFieldEffects.setPassives(defender, true);
        close(helper, 2, defender.getAttribute(PlayerAttributes.TRUE_RESISTANCE).getValue(), "Buffer grants +2");
        check(helper, MentalImmunity.isImmune(defender), "Runtime immunity covers players");
        check(helper, MentalImmunity.feedback(defender).orElseThrow().equals(Component.translatable(
                "message.academy.mentalout.protected.electromagnetic_field")), "Immunity uses the requested feedback");
        MagneticFieldEffects.setPassives(defender, false);
        close(helper, 0, defender.getAttribute(PlayerAttributes.TRUE_RESISTANCE).getValue(), "Cleanup removes only own buffer");
        defender.setHealth(0);
        manager.tick(defender);
        close(helper, 0, account.current(), "Death clears MP");
        check(helper, !manager.isDefenseActive(defender), "Death clears defense");
        var legacyData = system.getPlayerData(attacker.getUUID());
        system.setPlayerAbilityCategory(attacker.getUUID(), AbilityCategories.ELECTROMASTER.get());
        system.setPlayerLevel(attacker.getUUID(), 5);
        var oldShield = new org.academy.internal.common.skilldata.ElectromagneticShieldData();
        oldShield.setProficiency(2400);
        legacyData.getMutableSkillDataMap().put("academy:electromagnetic_shield", oldShield);
        legacyData.setElectromasterMigrated(false);
        manager.onPlayerLogin(attacker);
        check(helper, !legacyData.getSkillDataMap().containsKey("academy:electromagnetic_shield"), "Login removes legacy shield");
        close(helper, 2400, Skills.IRON_SAND_ARSENAL.get().getProficiency(attacker), "Login migrates shield proficiency");
        check(helper, Skills.IRON_SAND_ARSENAL.get().isEnabled(attacker), "Migrated hybrid skill remains available");
    }

    private static void attacks(GameTestHelper helper, ServerPlayer attacker, ServerPlayer defender) {
        var system = learn(attacker);
        var base = helper.absoluteVec(new Vec3(6, 4, 4));
        attacker.snapTo(base.x, base.y, base.z, 0, 0);
        var target = helper.spawn(EntityTypes.HUSK, 6, 4, 8);
        target.setNoAi(true);
        target.getAttribute(Attributes.MAX_HEALTH).setBaseValue(1000);
        target.setHealth(1000);
        target.setItemSlot(EquipmentSlot.CHEST, new ItemStack(Items.DIAMOND_CHESTPLATE));
        var neutral = helper.spawn(EntityTypes.COW, 7, 4, 8);
        var cowHealth = neutral.getHealth();
        var actor = new AbilityActorContext(attacker, Skills.IRON_SAND_ARSENAL.get(), 1.5, 2, 3, new TestAccount(100));
        IronSandActions.whip(actor, 12);
        close(helper, 970, target.getHealth(), "Whip bypasses armor and deals 10AD");
        close(helper, 1, AbilityHitEffects.electricalCharge(target), "One successful hit adds exactly one charge");
        target.invulnerableTime = 0;
        IronSandActions.whip(actor, 12);
        close(helper, 925, target.getHealth(), "Precharged whip deals 15AD in one hit");
        close(helper, 2, AbilityHitEffects.electricalCharge(target), "Bonus does not add a second charge");
        target.invulnerableTime = 0;
        var wear = target.getItemBySlot(EquipmentSlot.CHEST).getDamageValue();
        IronSandActions.cloudPulse(actor, 16);
        close(helper, 877, target.getHealth(), "Cloud deals 16AD");
        check(helper, target.getItemBySlot(EquipmentSlot.CHEST).getDamageValue() > wear, "3000 cloud wears armor");
        close(helper, cowHealth, neutral.getHealth(), "Neutral entities are excluded");
        var npc = helper.spawn(EntityTypes.COW, 6, 4, 5);
        npc.setYRot(0);
        MagneticFieldEffects.setPassives(npc, true);
        close(helper, 2, npc.getAttribute(PlayerAttributes.TRUE_RESISTANCE).getValue(), "NPC buffer uses public API");
        target.invulnerableTime = 0;
        var before = target.getHealth();
        IronSandActions.whip(new AbilityActorContext(npc, Skills.IRON_SAND_ARSENAL.get(), 1, 1, 0, new TestAccount(20)), 12);
        check(helper, target.getHealth() < before, "NPC actor uses the same targeting and damage API");
    }

    private static void projectiles(GameTestHelper helper, ServerPlayer attacker, ServerPlayer defender) {
        var owner = helper.spawn(EntityTypes.SKELETON, 3, 4, 3);
        var mass = new TestAccount(18);
        var center = defender.getBoundingBox().getCenter();
        var arrow = new net.minecraft.world.entity.projectile.arrow.Arrow(EntityTypes.ARROW, helper.getLevel());
        arrow.setOwner(owner);
        arrow.setPos(center.add(-10, 0, 0));
        arrow.setDeltaMovement(20, 0, 0);
        helper.getLevel().addFreshEntity(arrow);
        check(helper, HostileProjectiles.tryDestroy(defender, arrow, mass, 2, 9), "Swept interception catches fast arrow");
        close(helper, 9, mass.current(), "Intercept cost is paid once");
        check(helper, !HostileProjectiles.tryDestroy(defender, arrow, mass, 2, 9), "Removed projectile cannot repay");
        arrow = new net.minecraft.world.entity.projectile.arrow.Arrow(EntityTypes.ARROW, helper.getLevel());
        arrow.setOwner(defender);
        arrow.setPos(center);
        check(helper, !HostileProjectiles.tryDestroy(defender, arrow, mass, 2, 9), "Own projectile is excluded");
        arrow.setOwner(owner);
        mass.mass = 8.99;
        check(helper, !HostileProjectiles.tryDestroy(defender, arrow, mass, 2, 9), "Partial MP cannot destroy projectile");
        close(helper, 8.99, mass.current(), "Failed interception does not debit");
    }

    private static void support(GameTestHelper helper, ServerPlayer attacker, ServerPlayer defender) {
        var position = helper.absoluteVec(new Vec3(6.5, 12, 6.5));
        attacker.snapTo(position.x, position.y, position.z, 0, 0);
        var query = new MagneticSupportQuery();
        helper.setBlock(11, 12, 6, Blocks.STONE);
        check(helper, !query.supported(helper.getLevel(), attacker.getBoundingBox(), 4), "Distant wall outside base support");
        check(helper, query.supported(helper.getLevel(), attacker.getBoundingBox(), 6), "Side wall supports upgraded hover");
        helper.setBlock(11, 12, 6, Blocks.AIR);
        helper.setBlock(6, 16, 6, Blocks.STONE);
        check(helper, query.supported(helper.getLevel(), attacker.getBoundingBox(), 4), "Ceiling supports hover");
        helper.setBlock(6, 16, 6, Blocks.AIR);
        check(helper, !query.supported(helper.getLevel(), attacker.getBoundingBox(), 4), "Removed support invalidates cache");
        attacker.setNoGravity(true);
        GravityControl.set(attacker, AcademyCraft.academy("test_a"), true);
        GravityControl.set(attacker, AcademyCraft.academy("test_b"), true);
        GravityControl.set(attacker, AcademyCraft.academy("test_a"), false);
        check(helper, attacker.isNoGravity(), "Releasing one source preserves another");
        GravityControl.set(attacker, AcademyCraft.academy("test_b"), false);
        check(helper, attacker.isNoGravity(), "Final release restores existing no-gravity state");
    }

    private static final class NotifyingCow extends net.minecraft.world.entity.animal.cow.Cow {
        int notifications;
        NotifyingCow(ServerLevel level) { super(EntityTypes.COW, level); }
        @Override public boolean hurtServer(ServerLevel level, net.minecraft.world.damagesource.DamageSource source, float amount) {
            notifications++;
            setHealth(getHealth() - 4);
            return true;
        }
    }

    private static final class TestAccount implements AbilityResourceAccount {
        double mass;
        TestAccount(double mass) { this.mass = mass; }
        @Override public double current() { return mass; }
        @Override public double capacity() { return 1000; }
        @Override public boolean tryConsume(double cost) { if (cost < 0 || cost > mass + 1e-6) return false; mass = Math.max(0, mass - cost); return true; }
        @Override public double recover(double amount) { mass += amount; return amount; }
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
            var attacker = player(helper, 2, 3, 5);
            var defender = player(helper, 8, 3, 5);
            try {
                switch (scenario) {
                    case "health" -> health(helper, attacker, defender);
                    case "resources" -> resources(helper, attacker, defender);
                    case "attacks" -> attacks(helper, attacker, defender);
                    case "projectiles" -> projectiles(helper, attacker, defender);
                    case "support" -> support(helper, attacker, defender);
                    default -> throw new IllegalArgumentException(scenario);
                }
                helper.succeed();
            } catch (ReflectiveOperationException exception) {
                throw new IllegalStateException("Cannot exercise skill entry point", exception);
            } finally {
                var players = helper.getLevel().getServer().getPlayerList();
                players.remove(attacker);
                players.remove(defender);
            }
        }

        @Override
        public MapCodec<? extends GameTestInstance> codec() {
            return CODEC;
        }

        @Override
        protected MutableComponent typeDescription() {
            return Component.literal("Magnetic field and iron sand rework regression");
        }
    }
}
