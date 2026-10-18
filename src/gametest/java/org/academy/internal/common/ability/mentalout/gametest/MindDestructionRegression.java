package org.academy.internal.common.ability.mentalout.gametest;

import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.level.storage.ServerLevelData;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import org.academy.AcademyCraft;
import org.academy.api.common.entitycontrol.*;
import org.academy.api.server.ability.AbilityResourceAccount;
import org.academy.api.server.ability.AbilitySystemServer;
import org.academy.api.server.ability.electromaster.MagneticFieldEffects;
import org.academy.api.server.damage.HealthLossGuards;
import org.academy.api.server.time.TemporalApi;
import org.academy.api.server.time.TemporalChannel;
import org.academy.internal.common.ability.AbilityCategories;
import org.academy.internal.common.ability.Skills;
import org.academy.internal.common.ability.mentalout.resistance.MentalResistanceManager;
import org.academy.internal.common.ability.mentalout.skills.lv5.MindDestruction;
import org.academy.internal.common.skilldata.OutputControlData;

import java.lang.reflect.Field;
import java.util.Map;

/** Exercises actual skill pulses and suppression in the isolated mental-defense test environment. */
final class MindDestructionRegression {
    private static final net.minecraft.resources.Identifier SOURCE = AcademyCraft.academy("test_mind_destruction");

    private MindDestructionRegression() {}

    static void verify(GameTestHelper helper, ServerPlayer controller, LivingEntity immune,
                       LivingEntity resistant, LivingEntity plain, ServerPlayer player) {
        try {
            verifyPulse(helper, controller, immune, resistant, plain);
            verifyResistance(helper, controller, resistant, player);
            verifyHealthGuard(helper, immune);
        } finally {
            MindDestruction.releaseEntity(controller.getUUID());
            for (var entity : java.util.List.of(immune, resistant, plain, player)) {
                MagneticFieldEffects.setPassives(entity, false);
                MentalResistanceManager.releaseEntity(entity.getUUID());
                MentalImmunity.set(entity, SOURCE, Component.empty(), false);
            }
        }
    }

    private static void verifyPulse(GameTestHelper helper, ServerPlayer controller, LivingEntity immune,
                                    LivingEntity resistant, LivingEntity plain) {
        var server = helper.getLevel().getServer();
        immune.getAttribute(Attributes.MAX_HEALTH).setBaseValue(100);
        immune.setHealth(100);
        var effect = start(controller, immune, true);
        pulse(server, effect);
        helper.assertTrue(immune.getHealth() < 100 && !MentalImmunity.isSuppressed(immune),
                "A nonlethal pulse must damage without stripping immunity");
        helper.assertTrue(TemporalApi.get(immune).effectiveScale(immune, TemporalChannel.ENTITY) < 1,
                "The first health loss must start reaction slowdown");
        immune.setHealth(5);
        pulse(server, effect);
        helper.assertValueEqual(immune.getHealth(), 1.0f, "The lethal pulse must leave one health");
        helper.assertTrue(immune.isAlive() && MentalControlApi.isMentalProtectionSuppressed(immune),
                "The target must survive with immunity stripped");
        helper.assertTrue(!effects().containsValue(effect), "The stripping cast must terminate immediately");
        helper.assertTrue(TemporalApi.get(immune).effectiveScale(immune, TemporalChannel.ENTITY) == 1,
                "Stripping must release the cast's reaction slowdown");
        MentalControlApi.apply(ControlRequest.permanent(controller, immune, SOURCE, 100,
                new ControlDirective.FreezeAi())).close();
        for (int i = 0; i < 25; i++) MagneticFieldEffects.setPassives(immune, true);
        helper.assertTrue(!MentalImmunity.isImmune(immune), "Passive registration cannot restore stripped immunity");
        MagneticFieldEffects.setPassives(immune, false);
        MindDestruction.tick(server);
        helper.assertValueEqual(immune.getHealth(), 1.0f, "A terminated cast must not pulse again");

        MentalControlApi.restoreMentalProtection(immune);
        immune.setHealth(0.5f);
        pulse(server, start(controller, immune, false));
        helper.assertValueEqual(immune.getHealth(), 0.5f, "Protection must never heal an already weaker target");
        helper.assertTrue(MentalImmunity.isSuppressed(immune), "A lethal zero-loss clamp must still strip defenses");

        MentalControlApi.restoreMentalProtection(immune);
        immune.setHealth(20);
        var system = AbilitySystemServer.getSystem(controller);
        system.addPlayerSkill(controller, Skills.OUTPUT_CONTROL.get().getKeyString());
        var output = (OutputControlData) system.getPlayerData(controller.getUUID()).getSkillDataMap()
                .get(Skills.OUTPUT_CONTROL.get().getKeyString());
        output.setAbilityOutput(3);
        try {
            pulse(server, start(controller, immune, false));
            helper.assertValueEqual(immune.getHealth(), 1.0f, "Final damage amplification must not bypass protection");
        } finally {
            output.setAbilityOutput(1);
        }

        MentalControlApi.restoreMentalProtection(immune);
        immune.setHealth(5);
        immune.addEffect(new MobEffectInstance(MobEffects.RESISTANCE, 100, 4));
        try {
            effect = start(controller, immune, false);
            pulse(server, effect);
            helper.assertValueEqual(immune.getHealth(), 5.0f, "Cancelled damage cannot change health");
            helper.assertTrue(!MentalImmunity.isSuppressed(immune) && effects().containsValue(effect),
                    "Cancelled damage must neither strip nor terminate the cast");
        } finally {
            immune.removeEffect(MobEffects.RESISTANCE);
            MindDestruction.releaseTarget(immune.getUUID());
        }

        var budget = new Budget();
        HealthLossGuards.set(immune, SOURCE, budget, 1, true);
        try {
            pulse(server, start(controller, immune, false));
            helper.assertValueEqual(immune.getHealth(), 5.0f, "Resource protection must absorb before lethal protection");
            helper.assertTrue(!MentalImmunity.isSuppressed(immune), "Fully absorbed damage must not strip defenses");
        } finally {
            HealthLossGuards.set(immune, SOURCE, budget, 1, false);
            MindDestruction.releaseTarget(immune.getUUID());
        }

        resistant.setHealth(5);
        effect = start(controller, resistant, true);
        var stupor = (ControlHandle) value(effect, "stupor");
        helper.assertTrue(stupor != null && !stupor.isClosed(), "Tagged resistance allows initial stupor");
        pulse(server, effect);
        helper.assertTrue(resistant.isAlive() && resistant.getHealth() == 1 && stupor.isClosed(),
                "Latent tagged resistance must be stripped and stupor released on a lethal pulse");

        plain.setHealth(5);
        pulse(server, start(controller, plain, false));
        helper.assertTrue(!plain.isAlive() && !MentalImmunity.isSuppressed(plain),
                "An unprotected target must retain ordinary lethal damage");
    }

    private static void verifyResistance(GameTestHelper helper, ServerPlayer controller,
                                         LivingEntity resistant, ServerPlayer player) {
        var level = helper.getLevel();
        var server = level.getServer();
        var clock = (ServerLevelData) level.getLevelData();
        var startTime = level.getGameTime();
        MentalControlApi.restoreMentalProtection(resistant);
        // Build real automatic resistance, then prove restoration cannot revive its old timer.
        for (int tick = 0; tick <= 401; tick++) {
            clock.setGameTime(startTime + tick);
            MentalResistanceManager.markTaggedAffected(controller, resistant);
            MentalResistanceManager.tick(server);
        }
        helper.assertTrue(MentalResistanceManager.isAutomaticallyResistant(resistant), "Automatic block must be active");
        MentalControlApi.suppressMentalProtection(resistant);
        MentalControlApi.restoreMentalProtection(resistant);
        helper.assertValueEqual(MentalControlApi.resistanceRemainingTicks(resistant), 0L, "Old resistance timer must be deleted");
        MentalControlApi.suppressMentalProtection(resistant);
        for (int tick = 402; tick <= 850; tick++) {
            clock.setGameTime(startTime + tick);
            MentalResistanceManager.markTaggedAffected(controller, resistant);
            MentalResistanceManager.tick(server);
        }
        MentalControlApi.restoreMentalProtection(resistant);
        helper.assertValueEqual(MentalControlApi.resistanceRemainingTicks(resistant), 0L,
                "Suppressed tagged entities must not accumulate hidden resistance");

        var system = AbilitySystemServer.getSystem(player);
        system.setPlayerAbilityCategory(player.getUUID(), AbilityCategories.MENTALOUT.get());
        system.setPlayerLevel(player.getUUID(), 5);
        MentalResistanceManager.markAffected(controller, player, true);
        input(player, 1, 1);
        helper.assertTrue(MentalResistanceManager.progress(player) > 0, "Player must have real break-free progress");
        MentalControlApi.suppressMentalProtection(player);
        helper.assertValueEqual(MentalResistanceManager.progress(player), 0, "Suppression clears player input progress");
        for (int tick = 851; tick <= 900; tick++) {
            clock.setGameTime(startTime + tick);
            MentalResistanceManager.markAffected(controller, player, true);
            input(player, tick, MentalResistanceManager.INPUT_MASK);
            MentalResistanceManager.tick(server);
        }
        helper.assertValueEqual(MentalResistanceManager.threshold(player), 1, "Suppression blocks new input challenges");
        MentalControlApi.restoreMentalProtection(player);
        helper.assertValueEqual(MentalControlApi.resistanceRemainingTicks(player), 0L, "No hidden player block may reappear");
        for (int tick = 901; tick <= 920 && !MentalResistanceManager.isManuallyResistant(player); tick++) {
            clock.setGameTime(startTime + tick);
            MentalResistanceManager.markAffected(controller, player, true);
            input(player, tick, MentalResistanceManager.INPUT_MASK);
        }
        helper.assertTrue(MentalResistanceManager.isManuallyResistant(player), "Manual resistance setup must break free");
        player.connection.markClientLoaded();
        player.setGameMode(net.minecraft.world.level.GameType.SURVIVAL);
        org.academy.internal.common.world.damagesource.PvpSetting.trySetPvpEnabled(controller, true);
        org.academy.internal.common.world.damagesource.PvpSetting.trySetPvpEnabled(player, true);
        player.setHealth(5);
        pulse(server, start(controller, player, false));
        helper.assertTrue(player.isAlive() && player.getHealth() == 1 && MentalImmunity.isSuppressed(player),
                "A lethal player pulse must preserve one health and strip active manual resistance");
        MentalControlApi.restoreMentalProtection(player);
        helper.assertValueEqual(MentalControlApi.resistanceRemainingTicks(player), 0L, "Manual immunity timer must be deleted");
        MentalControlApi.suppressMentalProtection(player);
        org.academy.internal.common.ability.mentalout.control.MentalControlEvents.onPlayerClone(
                new net.neoforged.neoforge.event.entity.player.PlayerEvent.Clone(player, player, true));
        helper.assertTrue(!MentalImmunity.isSuppressed(player), "Respawn must restore the ability to gain protection");
    }

    private static void verifyHealthGuard(GameTestHelper helper, LivingEntity target) {
        target.setHealth(10);
        var preview = HealthLossGuards.protectDeath(target, 1, () -> HealthLossGuards.preview(target, 10, 0));
        helper.assertTrue(preview.value() == 1 && !preview.preventedDeath(), "Preview must not commit protection");
        var rejected = HealthLossGuards.protectDeath(target, 1, () ->
                HealthLossGuards.commit(target, 10, 0, _ -> false));
        helper.assertTrue(!rejected.preventedDeath(), "A rejected health submission must not report a prevented death");
        var nested = HealthLossGuards.protectDeath(target, 1, () -> HealthLossGuards.protectDeath(target, 2, () -> {
            target.setHealth(0);
            return target.getHealth();
        }));
        helper.assertTrue(nested.preventedDeath() && nested.value().preventedDeath()
                && target.getHealth() == 2, "Nested scopes must preserve the strongest floor and report settlement");
        try {
            HealthLossGuards.protectDeath(target, 1, () -> { throw new IllegalStateException("expected"); });
        } catch (IllegalStateException expected) {
            helper.assertTrue(expected.getMessage().equals("expected"), "Unexpected scope failure");
        }
        target.setHealth(0);
        helper.assertValueEqual(target.getHealth(), 0.0f, "Protection must expire even when the scoped action throws");
    }

    // Advance only this effect's due time; leave both the server clock and unrelated casts alone.
    private static void pulse(MinecraftServer server, Object effect) {
        try {
            field(effect.getClass(), "nextDamageTick").setLong(effect, server.getTickCount());
            MindDestruction.tick(server);
        } catch (ReflectiveOperationException error) { throw new AssertionError(error); }
    }

    private static Object start(ServerPlayer controller, LivingEntity target, boolean stupor) {
        try {
            var method = MindDestruction.class.getDeclaredMethod("start", ServerPlayer.class, LivingEntity.class, boolean.class);
            method.setAccessible(true);
            var before = java.util.List.copyOf(effects().values());
            method.invoke(null, controller, target, stupor);
            return effects().values().stream().filter(value -> !before.contains(value)).findFirst().orElseThrow();
        } catch (ReflectiveOperationException error) { throw new AssertionError(error); }
    }

    private static void input(ServerPlayer player, long sequence, int mask) {
        var packet = new MentalResistanceManager.InputPacket(sequence, mask);
        packet.setPacketListener(player.connection);
        MentalResistanceManager.Server.input(packet);
    }

    private static Map<?, ?> effects() { return (Map<?, ?>) value(MindDestruction.class, "ACTIVE"); }

    private static Object value(Object instance, String name) {
        try {
            return field(instance instanceof Class<?> type ? type : instance.getClass(), name)
                    .get(instance instanceof Class<?> ? null : instance);
        } catch (ReflectiveOperationException error) { throw new AssertionError(error); }
    }

    private static Field field(Class<?> type, String name) throws NoSuchFieldException {
        var field = type.getDeclaredField(name);
        field.setAccessible(true);
        return field;
    }

    private static final class Budget implements AbilityResourceAccount {
        private double current = 100;
        public double current() { return current; }
        public double capacity() { return 100; }
        public boolean tryConsume(double amount) { if (amount > current) return false; current -= amount; return true; }
        public double recover(double amount) { current += amount; return amount; }
    }
}
