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
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.player.Input;
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

import java.util.List;
import java.util.UUID;

/** Exercises the live damage mixins, beam resolver and server magnetic movement. */
@EventBusSubscriber(modid = AcademyCraft.MOD_ID)
public final class ShieldAndMagneticRegressionGameTests {
    private static final Identifier TYPE = AcademyCraft.academy("shield_magnetic_regression_function");

    @SubscribeEvent
    private static void registerType(RegisterEvent event) {
        event.register(Registries.TEST_INSTANCE_TYPE, TYPE, () -> Instance.CODEC);
    }

    @SubscribeEvent
    private static void registerTests(RegisterGameTestsEvent event) {
        for (var scenario : List.of("shield_paths", "beam_probe", "resistance_damage", "magnetic_flight")) {
            var environment = event.registerEnvironment(AcademyCraft.academy("shield_magnetic/" + scenario),
                    new TestEnvironmentDefinition.AllOf(List.of()));
            event.registerTest(AcademyCraft.academy("shield_magnetic_" + scenario), new Instance(new TestData<>(
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
        player.setGameMode(net.minecraft.world.level.GameType.SURVIVAL);
        var position = helper.absoluteVec(new Vec3(x, y, z));
        player.snapTo(position.x, position.y, position.z, 0, 0);
        player.setNoGravity(true);
        return player;
    }

    private static void check(GameTestHelper helper, boolean condition, String message) {
        helper.assertTrue(condition, Component.literal(message));
    }

    private static void shieldPaths(GameTestHelper helper, ServerPlayer attacker, ServerPlayer defender) {
        defender.setData(AttachmentTypes.LIGHT_SHIELD_ACTIVE.get(), true);
        var start = attacker.getBoundingBox().getCenter();
        var end = start.add(12, 0, 0);
        var segment = new LinearSegment(start, end);
        for (var skill : List.of(Skills.ARC_GENERATE.get(), Skills.THUNDER_LANCE.get(),
                Skills.SINGLE_HIGH_SPEED_ELECTRON_BEAM.get())) {
            var payload = LinearAttackPayload.builder(attacker, skill, SkillDamageSource.of(attacker, skill), 0.125f)
                    .damage(_ -> 1000.0f).build();
            var resolved = LinearReflectionResolver.resolve(helper.getLevel(), segment, payload);
            check(helper, resolved.isRefracted(), skill.getKeyString() + " must refract");
            check(helper, resolved.reflectionCandidate().orElseThrow().reflector() == defender,
                    "The shield must own the fold");
            var result = LinearAttackExecutor.execute(helper.getLevel(), resolved, payload);
            check(helper, !result.outboundHits().contains(defender) && !result.returnHits().contains(defender),
                    "Neither segment may damage the shield owner");
            check(helper, defender.isAlive() && defender.getHealth() > 0, "A shielded player must remain alive");
            check(helper, !ElectromasterArcActions.strikeChain(helper.getLevel(), attacker,
                    SkillDamageSource.of(attacker, skill), attacker, defender, 1000),
                    "Secondary arcs must not bypass the shield");
            var session = new ContinuousReflectionSession();
            check(helper, ContinuousBeamReflection.resolve(helper.getLevel(), segment, payload,
                    session, 0, 4, true).isRefracted(), "Continuous beams must use the same shield resolver");
            defender.setData(AttachmentTypes.LIGHT_SHIELD_ACTIVE.get(), false);
            check(helper, !ContinuousBeamReflection.resolve(helper.getLevel(), segment, payload,
                    session, 1, 4, false).isRefracted(), "Stopping a shield must end refraction immediately");
            defender.setData(AttachmentTypes.LIGHT_SHIELD_ACTIVE.get(), true);
        }
    }

    private static void beamProbe(GameTestHelper helper, ServerPlayer attacker, ServerPlayer defender)
            throws ReflectiveOperationException {
        defender.setData(AttachmentTypes.LIGHT_SHIELD_ACTIVE.get(), true);
        var pig = helper.spawn(EntityTypes.PIG, 5, 3, 5);
        pig.setNoAi(true);
        var before = pig.getHealth();
        var beam = new HighSpeedElectronBeam(
                org.academy.internal.common.world.entity.EntityTypes.HIGH_SPEED_ELECTRON_BEAM.get(),
                helper.getLevel());
        beam.configure(attacker, Skills.SINGLE_HIGH_SPEED_ELECTRON_BEAM.get(), 2.0f, 0, 1, false, false);
        var start = attacker.getBoundingBox().getCenter();
        beam.snapTo(start.x, start.y, start.z, -90, 0);
        beam.setBeamLength(12);
        // Invoke the firing entry directly so the test is independent of learned skills/charge time.
        var fire = HighSpeedElectronBeam.class.getDeclaredMethod("fire",
                net.minecraft.server.level.ServerLevel.class, ServerPlayer.class);
        fire.setAccessible(true);
        fire.invoke(beam, helper.getLevel(), attacker);
        check(helper, beam.isReflectionActive(), "Beam visuals must retain the fold");
        check(helper, pig.getHealth() < before, "Probing the shield must not consume the beam's first hit");
        check(helper, defender.isAlive(), "A shielded player must survive the beam");
    }

    private static void resistanceDamage(GameTestHelper helper, ServerPlayer attacker, ServerPlayer defender) {
        var resistance = defender.getAttribute(PlayerAttributes.TRUE_RESISTANCE);
        check(helper, resistance != null, "Player true resistance attribute must be registered");
        var source = SkillDamageSource.of(attacker, Skills.SINGLE_HIGH_SPEED_ELECTRON_BEAM.get());
        for (var points : new double[]{2, 6, 8}) {
            resistance.setBaseValue(points);
            var before = defender.getHealth();
            defender.hurtServer(helper.getLevel(), source, 2);
            check(helper, Math.abs(defender.getHealth() - (before - 2 * (1 - points * 0.1))) < 0.01,
                    "Server damage must apply true resistance exactly once: resistance=" + points + ", before=" + before + ", after=" + defender.getHealth());
        }
        defender.hurtServer(helper.getLevel(), source, 1000);
        check(helper, defender.getHealth() == 0 && defender.isDeadOrDying(),
                "Lethal damage must leave zero health and complete the death flow");
    }

    private static void magneticFlight(GameTestHelper helper, ServerPlayer subject)
            throws ReflectiveOperationException {
        for (var pos : BlockPos.betweenClosed(1, 2, 1, 14, 2, 14)) helper.setBlock(pos, Blocks.STONE);
        var feet = helper.absoluteVec(new Vec3(4.5, 6, 4.5));
        subject.snapTo(feet.x, feet.y, feet.z, 0, 90);
        subject.setNoGravity(false);
        var mode = Class.forName(MagnetManipulation.class.getName() + "$PullMode");
        var constructor = MagnetManipulation.MoveContext.class.getDeclaredConstructor(ServerPlayer.class, mode);
        constructor.setAccessible(true);
        var context = constructor.newInstance(subject, mode.getEnumConstants()[0]);
        var move = context.getClass().getDeclaredMethod("pullPlayerToTarget");
        move.setAccessible(true);
        var release = context.getClass().getDeclaredMethod("onUnregistered");
        release.setAccessible(true);
        try {
            for (var tick = 0; tick < 100; tick++) {
                check(helper, (boolean) move.invoke(context), "Stone must be a valid self-pull anchor");
                subject.move(MoverType.SELF, subject.getDeltaMovement());
            }
            var groundY = helper.absoluteVec(new Vec3(0, 3, 0)).y;
            check(helper, Math.abs(subject.getY() - groundY - 1.5) < 0.05,
                    "Holding must settle at the default hover height");
            var initialZ = subject.getZ();
            subject.setLastClientInput(new Input(true, false, false, false, true, false, false));
            for (var tick = 0; tick < 10; tick++) {
                check(helper, (boolean) move.invoke(context), "Movement input must sustain terrain flight");
                subject.move(MoverType.SELF, subject.getDeltaMovement());
            }
            check(helper, subject.getZ() > initialZ + 1, "Forward input must move along the current yaw");
            check(helper, subject.getY() > groundY + 1.5, "Jump input must increase hover height");
        } finally {
            release.invoke(context);
        }
        check(helper, !subject.isNoGravity(), "Release must restore the original gravity state");
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
                    case "shield_paths" -> shieldPaths(helper, attacker, defender);
                    case "beam_probe" -> beamProbe(helper, attacker, defender);
                    case "resistance_damage" -> resistanceDamage(helper, attacker, defender);
                    case "magnetic_flight" -> magneticFlight(helper, attacker);
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
            return Component.literal("Shield and magnetic movement regression");
        }
    }
}
