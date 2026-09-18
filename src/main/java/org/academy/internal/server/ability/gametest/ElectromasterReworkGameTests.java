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
import org.academy.api.common.ability.electromaster.LevitationTuning;
import org.academy.api.common.ability.electromaster.MagneticFieldTuning;
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
        var absorbed = new java.util.ArrayList<HealthLossGuards.Resolution>();
        HealthLossGuards.set(npc, key, account, 0.9, true, absorbed::add);
        HealthLossGuards.preview(npc, 100, 90);
        check(helper, absorbed.isEmpty(), "Preview cannot emit a shield event");
        npc.setHealth(90);
        close(helper, 100, npc.getHealth(), "setHealth is protected");
        close(helper, 91, account.current(), "setHealth pays once");
        check(helper, absorbed.size() == 1, "Successful absorption emits once");
        close(helper, 10, absorbed.getFirst().absorbed(), "Event reports protected health");
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
        var eventsBefore = absorbed.size();
        check(helper, !HealthLossGuards.commit(npc, 100, 90, _ -> false), "Rejected writer reports failure");
        close(helper, before, account.current(), "Rejected writer refunds");
        check(helper, absorbed.size() == eventsBefore, "Refunded absorption cannot emit a shield event");
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
        gravityLeases(helper, attacker);
        groundReference(helper, attacker);
        terrainFollowingNeverFalls(helper, attacker, defender);
        ascentKeepsClimbingAndSettlesAtACeiling(helper, attacker);
    }

    private static void gravityLeases(GameTestHelper helper, ServerPlayer attacker) {
        attacker.setNoGravity(true);
        GravityControl.set(attacker, AcademyCraft.academy("test_a"), true);
        GravityControl.set(attacker, AcademyCraft.academy("test_b"), true);
        GravityControl.set(attacker, AcademyCraft.academy("test_a"), false);
        check(helper, attacker.isNoGravity(), "Releasing one source preserves another");
        GravityControl.set(attacker, AcademyCraft.academy("test_b"), false);
        check(helper, attacker.isNoGravity(), "Final release restores existing no-gravity state");
    }

    /**
     * Altitude must follow the surface underfoot. A wall beside the mover is valid field support but must
     * never become the height reference, which is what made hover altitude depend on an arbitrary
     * neighbour and produced the reported drop over uneven ground.
     */
    private static void groundReference(GameTestHelper helper, ServerPlayer attacker) {
        for (var y = 6; y <= 9; y++) helper.setBlock(4, y, 4, Blocks.AIR);
        var feet = helper.absoluteVec(new Vec3(4.5, 8, 4.5));
        attacker.snapTo(feet.x, feet.y, feet.z, 0, 0);
        var query = new MagneticSupportQuery();
        check(helper, query.groundBelow(helper.getLevel(), attacker.getBoundingBox(), 4) == null,
                "Floating over a void has no ground reference");
        helper.setBlock(4, 6, 4, Blocks.STONE);
        var ground = query.groundBelow(helper.getLevel(), attacker.getBoundingBox(), 4);
        check(helper, ground != null, "A block below the feet is a ground reference");
        check(helper, ground != null && ground.isGround(), "A surface underfoot faces up");
        var groundY = ground == null ? -1 : ground.pos().getY();
        // A wall beside the mover is field support, but must never displace the ground reference used for
        // altitude. Placed nearer than the floor so it would win if the two were not kept apart.
        helper.setBlock(6, 8, 4, Blocks.STONE);
        var beside = query.nearest(helper.getLevel(), attacker.getBoundingBox(), 4);
        var under = query.groundBelow(helper.getLevel(), attacker.getBoundingBox(), 4);
        check(helper, beside != null, "A side wall is field support");
        check(helper, under != null && under.pos().getY() == groundY,
                "The wall must not displace the ground reference");
        helper.setBlock(6, 8, 4, Blocks.AIR);
    }

    /**
     * Regression for the reported drop over uneven ground, and for the follow-up stutter where velocity was
     * truncated almost every tick while admission still reported support.
     *
     * <p>The mover is started already at full horizontal speed, because the defect was a loss of speed that
     * had been achieved rather than a slow ramp: with the bug present the velocity collapses within a few
     * ticks of crossing the step. Each tick therefore has to keep essentially all of its horizontal speed
     * and must not be clamped at all while there is ground underfoot. An earlier version of this test only
     * asserted "moved more than six blocks in sixty ticks", which passed even while the mover was crawling,
     * and so failed to catch the stutter.</p>
     */
    private static void terrainFollowingNeverFalls(GameTestHelper helper, ServerPlayer attacker, ServerPlayer defender) {
        // A short run: a one-block step up, then a plateau, all within the test structure.
        for (var x = 2; x <= 13; x++) helper.setBlock(x, 2, 6, Blocks.STONE);
        for (var x = 6; x <= 9; x++) helper.setBlock(x, 3, 6, Blocks.STONE);

        var speed = MagneticFieldTuning.FLIGHT_SPEED_PER_TICK;
        var start = helper.absoluteVec(new Vec3(3.5, 4.5, 6.5));
        attacker.snapTo(start.x, start.y, start.z, -90, 0);
        // Already cruising: the bug dropped achieved speed, so start from it and require it be kept.
        attacker.setDeltaMovement(new Vec3(speed, 0, 0));
        var movement = new MagneticLevitation();
        var tuning = LevitationTuning.DEFAULT;
        var radius = 4.0;
        var worstClearance = Double.MAX_VALUE;
        var stepped = false;
        var ticks = 5;
        for (var tick = 0; tick < ticks; tick++) {
            var step = movement.step(attacker, 1, 0, 0, speed, radius, tuning);
            check(helper, step.supported(), "Field must stay available while crossing connected terrain");
            check(helper, !step.clamped(),
                    "A step over ground must never be clamped: tick=" + tick + " y=" + attacker.getY()
                            + " velocity=" + step.velocity());
            var horizontal = Math.hypot(step.velocity().x, step.velocity().z);
            check(helper, horizontal > speed * 0.9,
                    "Horizontal speed must be preserved over terrain, not clamped to a crawl: tick=" + tick
                            + " horizontal=" + horizontal + " expected=" + speed);
            // Unbounded descent is the signature of the original bug; the solver must stay within its limits.
            check(helper, Math.abs(step.velocity().y) <= tuning.maxClimbSpeed() + 1.0e-9,
                    "Vertical motion must stay bounded (no free fall): tick=" + tick + " y=" + step.velocity().y);
            if (step.velocity().y > 1.0e-6) stepped = true;
            attacker.setDeltaMovement(step.velocity());
            attacker.move(MoverType.SELF, step.velocity());
            var ground = movement.ground();
            if (ground != null) worstClearance = Math.min(worstClearance, attacker.getY() - ground.surfaceY());
            defender.snapTo(attacker.getX(), attacker.getY(), attacker.getZ(), 0, 0);
        }
        check(helper, worstClearance < Double.MAX_VALUE,
                "Ground reference must be found while crossing connected terrain");
        check(helper, worstClearance > 0.0,
                "Mover must never end up inside the ground while terrain-following: clearance=" + worstClearance);
        check(helper, stepped, "Solver must lift the mover over stepped terrain");
        check(helper, attacker.getX() > start.x + 6,
                "Forward travel must not be swallowed: start=" + start.x + " x=" + attacker.getX());

        // Leaving the field is still bounded: past the last block the mover must fall back, not fly on.
        var offEdge = movement.step(attacker, 1, 0, 0, speed, radius, tuning);
        check(helper, offEdge.supported(), "The last block of the run still anchors the mover");
        var venturing = new MagneticLevitation();
        var away = helper.absoluteVec(new Vec3(60.5, 40.0, 6.5));
        attacker.snapTo(away.x, away.y, away.z, -90, 0);
        var unsupported = venturing.step(attacker, 1, 0, 0, speed, radius, tuning);
        check(helper, !unsupported.supported(),
                "Away from any collidable block the field must report no support");
    }

    /**
     * Regression for the reported "ascent and descent crawl to almost nothing". Climbing means moving away
     * from the ground support, so an uncapped target clearance makes the mover climb straight out of its own
     * field and then get clamped every tick while it keeps asking for more altitude — a permanent fight with
     * the boundary that reads as a stutter. Holding jump must instead climb at a usable rate and then settle
     * at a steady ceiling well inside the field.
     *
     * <p>Stalling is only a symptom once the ceiling is reached, so the assertions are: it climbs promptly,
     * reaches a real altitude, and the settled state is stable and unclamped. The last part is what
     * distinguishes a working cap from the original per-tick boundary fight.</p>
     */
    private static void ascentKeepsClimbingAndSettlesAtACeiling(GameTestHelper helper, ServerPlayer attacker) {
        for (var x = 2; x <= 13; x++) {
            for (var z = 2; z <= 13; z++) helper.setBlock(x, 2, z, Blocks.STONE);
        }
        var speed = MagneticFieldTuning.FLIGHT_SPEED_PER_TICK;
        var radius = MagneticFieldTuning.supportRadius(0);
        var tuning = LevitationTuning.DEFAULT;
        var start = helper.absoluteVec(new Vec3(7.5, 3.5, 7.5));
        attacker.snapTo(start.x, start.y, start.z, 0, 0);
        attacker.setDeltaMovement(Vec3.ZERO);
        var movement = new MagneticLevitation();
        var rise = 0.0;
        var peakClimb = 0.0;
        var reachedCeiling = -1;
        var previousY = attacker.getY();
        for (var tick = 0; tick < 40; tick++) {
            // Holding jump the whole time.
            var step = movement.step(attacker, 0, 0, 1, speed, radius, tuning);
            check(helper, step.supported(), "Holding jump must not leave the field: tick=" + tick);
            peakClimb = Math.max(peakClimb, step.velocity().y);
            if (reachedCeiling < 0 && step.velocity().y < 0.05) reachedCeiling = tick;
            attacker.setDeltaMovement(step.velocity());
            attacker.move(MoverType.SELF, step.velocity());
            var climbedThisTick = attacker.getY() - previousY;
            previousY = attacker.getY();
            if (climbedThisTick > 0) rise += climbedThisTick;
        }
        check(helper, rise > 1.0, "Holding jump must actually gain altitude: rise=" + rise);
        check(helper, peakClimb > tuning.maxClimbSpeed() * 0.5,
                "Ascent must reach a usable rate, not be clamped to a crawl: peak=" + peakClimb
                        + " expected up to " + tuning.maxClimbSpeed());
        check(helper, reachedCeiling >= 0,
                "Ascent must settle at a ceiling rather than climbing without bound");
        check(helper, reachedCeiling <= 15,
                "Ascent must reach its ceiling promptly: reached at tick " + reachedCeiling);

        // The ceiling must be a steady hold, not a per-tick fight with the boundary clamp.
        var settledClearance = Double.NaN;
        for (var tick = 0; tick < 5; tick++) {
            var step = movement.step(attacker, 0, 0, 1, speed, radius, tuning);
            check(helper, step.supported(), "The settled ceiling must stay inside the field");
            check(helper, !step.clamped(),
                    "Holding at the ceiling must not fight the boundary clamp: tick=" + tick
                            + " clearance=" + step.clearance());
            check(helper, step.clearance() <= radius,
                    "Settled clearance must stay inside the field reach: clearance=" + step.clearance()
                            + " radius=" + radius);
            if (!Double.isNaN(settledClearance)) {
                check(helper, Math.abs(step.clearance() - settledClearance) < 0.01,
                        "The settled ceiling must be stable: " + settledClearance + " -> " + step.clearance());
            }
            settledClearance = step.clearance();
            attacker.setDeltaMovement(step.velocity());
            attacker.move(MoverType.SELF, step.velocity());
        }
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
