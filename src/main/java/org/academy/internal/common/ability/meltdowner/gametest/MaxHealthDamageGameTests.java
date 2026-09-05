package org.academy.internal.common.ability.meltdowner.gametest;

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
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterGameTestsEvent;
import net.neoforged.neoforge.registries.RegisterEvent;
import org.academy.AcademyCraft;
import org.academy.internal.common.ability.Skills;
import org.academy.internal.common.ability.meltdowner.skills.lv5.Disintegrate;
import org.academy.internal.common.world.damagesource.DamageTypes;
import org.academy.internal.common.world.entity.skill.HighSpeedElectronBeam;

import java.util.List;
import java.util.UUID;

/** Verify the actual projectile pipeline, not only the skill's formula helper. */
@EventBusSubscriber(modid = AcademyCraft.MOD_ID)
public final class MaxHealthDamageGameTests {
    private static final Identifier TYPE = AcademyCraft.academy("max_health_damage_function");

    private MaxHealthDamageGameTests() {
    }

    @SubscribeEvent
    private static void registerType(RegisterEvent event) {
        event.register(Registries.TEST_INSTANCE_TYPE, TYPE, () -> DamageTest.CODEC);
    }

    @SubscribeEvent
    private static void registerTests(RegisterGameTestsEvent event) {
        var environment = event.registerEnvironment(AcademyCraft.academy("max_health_damage"),
                new TestEnvironmentDefinition.AllOf(List.of()));
        var data = new TestData<>(environment, Identifier.withDefaultNamespace("empty"),
                40, 0, true, Rotation.NONE, false, 1, 1, false, 16);
        event.registerTest(AcademyCraft.academy("max_health_damage_beam"), new DamageTest(data));
    }

    private static void verify(GameTestHelper helper) {
        var level = helper.getLevel();
        var profile = new GameProfile(UUID.randomUUID(), "damage-test");
        var cookie = CommonListenerCookie.createInitial(profile, false);
        var player = new ServerPlayer(level.getServer(), level, profile, cookie.clientInformation());
        var connection = new Connection(PacketFlow.SERVERBOUND);
        new EmbeddedChannel(connection);
        level.getServer().getPlayerList().placeNewPlayer(connection, player, cookie);
        player.setGameMode(GameType.SURVIVAL);
        var beam = new HighSpeedElectronBeam(
                org.academy.internal.common.world.entity.EntityTypes.HIGH_SPEED_ELECTRON_BEAM.get(), level);
        try {
            var system = org.academy.api.server.ability.AbilitySystemServer.getSystem(player);
            system.setPlayerAbilityCategory(player.getUUID(),
                    org.academy.internal.common.ability.AbilityCategories.MELTDOWNER.get());
            system.setPlayerLevel(player.getUUID(), 5);
            system.addPlayerSkill(player, Skills.DISINTEGRATE.get().getKeyString());
            if (!Skills.DISINTEGRATE.get().isEnabled(player)) Skills.DISINTEGRATE.get().toggle(player);
            helper.assertTrue(Skills.DISINTEGRATE.get().isEnabled(player),
                    "Test caster must have Disintegrate enabled before launching its beam");
            var target = helper.spawn(EntityTypes.COW, 3, 2, 5);
            target.setNoAi(true);
            target.getAttribute(Attributes.MAX_HEALTH).setBaseValue(1000.0);
            target.setHealth(1000.0f);
            var origin = helper.absoluteVec(new Vec3(3.5, 2.5, 1.5));
            player.snapTo(origin.x, origin.y, origin.z - 2, 0, 0);
            var damage = Disintegrate.Server.DAMAGE;
            beam.configure(player, Skills.DISINTEGRATE.get(), damage.baseDamage(),
                    damage.maxHealthRatio(), 2.0f, false, false);
            beam.setAttackDelayTicks(0);
            beam.setBeamLength(8.0f);
            beam.setPos(origin);
            beam.setYRot(0.0f);
            beam.setXRot(0.0f);
            level.addFreshEntity(beam);
            beam.tick();
            helper.assertTrue(Math.abs(target.getHealth() - 800.0f) < 0.001f,
                    "Disintegrate beam must deal 5% of 1000 plus 75 times 2: health=" + target.getHealth());
            helper.assertTrue(target.getLastDamageSource() != null
                            && target.getLastDamageSource().is(DamageTypes.MELT_DAMAGE),
                    "Disintegrate must retain its melt damage type");
        } finally {
            beam.discard();
            level.getServer().getPlayerList().remove(player);
        }
        helper.succeed();
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
            return Component.literal("Maximum-health skill damage balance");
        }
    }
}
