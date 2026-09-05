package org.academy.internal.server.config.gametest;

import com.mojang.authlib.GameProfile;
import com.mojang.serialization.MapCodec;
import io.netty.channel.embedded.EmbeddedChannel;
import net.minecraft.core.BlockPos;
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
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterGameTestsEvent;
import net.neoforged.neoforge.registries.RegisterEvent;
import org.academy.AcademyCraft;
import org.academy.api.common.util.LevelUtil;
import org.academy.api.server.ability.AbilityBlockDrops;
import org.academy.api.server.ability.AbilityEffectPolicy;
import org.academy.internal.common.ability.Skills;
import org.academy.internal.common.attachment.AttachmentTypes;
import org.academy.internal.common.world.damagesource.DamageTypes;
import org.academy.internal.common.world.damagesource.DestroyBlocksSetting;
import org.academy.internal.common.world.damagesource.PvpSetting;
import org.academy.internal.common.world.damagesource.SkillDamageUtil;
import org.academy.internal.server.config.DimensionEffectsConfig;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@EventBusSubscriber(modid = AcademyCraft.MOD_ID)
public final class DimensionEffectsGameTests {
    private static final Identifier TYPE = AcademyCraft.academy("dimension_effects_function");

    private DimensionEffectsGameTests() {
    }

    @SubscribeEvent
    private static void registerType(RegisterEvent event) {
        event.register(Registries.TEST_INSTANCE_TYPE, TYPE, () -> PolicyTest.CODEC);
    }

    @SubscribeEvent
    private static void registerTests(RegisterGameTestsEvent event) {
        var environment = event.registerEnvironment(
                AcademyCraft.academy("dimension_effects"),
                new TestEnvironmentDefinition.AllOf(List.of()));
        var data = new TestData<>(environment, Identifier.withDefaultNamespace("empty"),
                40, 0, true, Rotation.NONE, false, 1, 1, false, 16);
        event.registerTest(AcademyCraft.academy("dimension_effects_authority"), new PolicyTest(data));
    }

    private static void verify(GameTestHelper helper) {
        var level = helper.getLevel();
        var config = level.getServer().getAcademyCraftServer().getDimensionEffectsConfig();
        var previousEnabled = config.enabled;
        var previousPvp = config.pvp;
        var previousBlocks = config.blockDestruction;
        var attacker = createPlayer(helper, "policy-attacker");
        var target = createPlayer(helper, "policy-target");
        try {
            var dimension = level.dimension().identifier().toString();
            var skill = Skills.MINING_BEAM.get();
            attacker.setData(AttachmentTypes.PVP_ENABLED.get(), false);
            target.setData(AttachmentTypes.PVP_ENABLED.get(), false);
            attacker.setData(AttachmentTypes.DESTROY_BLOCKS_ENABLED.get(), false);
            attacker.setData(AttachmentTypes.SKILL_DESTROY_BLOCKS_ENABLED.get(),
                    Map.of(skill.getKeyString(), false));

            config.enabled = false;
            helper.assertTrue(PvpSetting.protectionReason(attacker, target)
                    == PvpSetting.ProtectionReason.ATTACKER_DISABLED, "Disabled config must preserve personal PVP");
            helper.assertTrue(!DestroyBlocksSetting.canDestroyBlocks(attacker, skill),
                    "Disabled config must preserve mining beam's personal switch");

            config.pvp = new DimensionEffectsConfig.DimensionRule();
            config.blockDestruction = new DimensionEffectsConfig.DimensionRule();
            config.pvp.mode = DimensionEffectsConfig.Mode.WHITELIST;
            config.pvp.dimensions = Set.of(dimension);
            config.blockDestruction.mode = DimensionEffectsConfig.Mode.WHITELIST;
            config.blockDestruction.dimensions = Set.of(dimension);
            config.enabled = true;
            helper.assertTrue(PvpSetting.protectionReason(attacker, target) == PvpSetting.ProtectionReason.NONE,
                    "Server allow must override both players' disabled PVP switches");
            helper.assertTrue(DestroyBlocksSetting.canDestroyBlocks(attacker)
                            && DestroyBlocksSetting.canDestroyBlocks(attacker, skill),
                    "Server allow must override global and independent per-skill block switches");

            // A second real server level must use its own dimension, not an owner/global cached result.
            var nether = level.getServer().getLevel(Level.NETHER);
            helper.assertTrue(nether != null, "Nether level must be present");
            helper.assertTrue(AbilityEffectPolicy.blockDestruction(nether) == AbilityEffectPolicy.Decision.DENY,
                    "Whitelist must distinguish levels on the same server");
            var remoteProfile = new GameProfile(UUID.randomUUID(), "policy-remote");
            var remoteTarget = new ServerPlayer(level.getServer(), nether, remoteProfile,
                    CommonListenerCookie.createInitial(remoteProfile, false).clientInformation());
            helper.assertTrue(PvpSetting.protectionReason(attacker, remoteTarget)
                            == PvpSetting.ProtectionReason.DIMENSION_DISABLED,
                    "PVP must use the target dimension, not the attacker's allowed dimension");
            helper.assertTrue(!AbilityBlockDrops.run(nether, attacker, () -> {
                throw new AssertionError("Denied remote block action was executed");
            }), "Remote block actions must use their actual effect level");

            config.pvp.mode = DimensionEffectsConfig.Mode.BLACKLIST;
            config.blockDestruction.mode = DimensionEffectsConfig.Mode.BLACKLIST;
            // Simulate a client enabling every personal switch: the server denial still wins.
            attacker.setData(AttachmentTypes.PVP_ENABLED.get(), true);
            target.setData(AttachmentTypes.PVP_ENABLED.get(), true);
            attacker.setData(AttachmentTypes.DESTROY_BLOCKS_ENABLED.get(), true);
            attacker.setData(AttachmentTypes.SKILL_DESTROY_BLOCKS_ENABLED.get(), Map.of());
            helper.assertTrue(PvpSetting.protectionReason(attacker, target)
                            == PvpSetting.ProtectionReason.DIMENSION_DISABLED,
                    "Server denial must override client switches");
            helper.assertTrue(!DestroyBlocksSetting.canDestroyBlocks(attacker)
                            && !DestroyBlocksSetting.canDestroyBlocks(attacker, skill),
                    "Mining beam must not bypass a forbidden dimension");
            var beforeHealth = target.getHealth();
            helper.assertTrue(!SkillDamageUtil.apply(attacker, target, skill, DamageTypes.MELT_DAMAGE, 5.0f),
                    "Forbidden direct skill damage was accepted");
            helper.assertTrue(!SkillDamageUtil.apply(attacker, target, skill, DamageTypes.CTA, 5.0f),
                    "Forbidden true-health skill damage was accepted");
            helper.assertTrue(target.getHealth() == beforeHealth, "Forbidden damage changed target health");
            var cow = helper.spawn(EntityTypes.COW, 5, 2, 1);
            helper.assertTrue(PvpSetting.protectionReason(attacker, cow) == PvpSetting.ProtectionReason.NONE,
                    "PVP dimension restrictions must not reject PVE");
            helper.assertTrue(PvpSetting.protectionReason(attacker, attacker) == PvpSetting.ProtectionReason.NONE,
                    "PVP dimension restrictions must not reject self effects");

            var pos = new BlockPos(2, 2, 2);
            helper.setBlock(pos, Blocks.STONE);
            var absolute = helper.absolutePos(pos);
            helper.assertTrue(!AbilityBlockDrops.destroyBlock(level, absolute, false, attacker),
                    "Forbidden ability block destruction was accepted");
            var start = helper.absoluteVec(new Vec3(0.5, 2.5, 2.5));
            var end = helper.absoluteVec(new Vec3(4.5, 2.5, 2.5));
            LevelUtil.destroyBlocksAlongPath(level, start, end, 0.1f, 3,
                    false, false, false, false, attacker);
            helper.assertTrue(level.getBlockState(absolute).is(Blocks.STONE),
                    "A delayed/reflected beam bypassed the dimension restriction");

            config.blockDestruction.dimensions = Set.of();
            helper.assertTrue(AbilityBlockDrops.destroyBlock(level, absolute, false, attacker),
                    "Allowed ability block destruction must still execute");
            helper.assertTrue(level.getBlockState(absolute).isAir(), "Allowed block was not destroyed");
        } finally {
            config.enabled = previousEnabled;
            config.pvp = previousPvp;
            config.blockDestruction = previousBlocks;
            level.getServer().getPlayerList().remove(attacker);
            level.getServer().getPlayerList().remove(target);
        }
        helper.succeed();
    }

    private static ServerPlayer createPlayer(GameTestHelper helper, String name) {
        var profile = new GameProfile(UUID.randomUUID(), name);
        var cookie = CommonListenerCookie.createInitial(profile, false);
        var player = new ServerPlayer(helper.getLevel().getServer(), helper.getLevel(),
                profile, cookie.clientInformation());
        var position = helper.absoluteVec(new Vec3(1.5, 2, 1.5));
        player.snapTo(position.x, position.y, position.z, 0, 0);
        var connection = new Connection(PacketFlow.SERVERBOUND);
        new EmbeddedChannel(connection);
        helper.getLevel().getServer().getPlayerList().placeNewPlayer(connection, player, cookie);
        player.setGameMode(GameType.SURVIVAL);
        return player;
    }

    private static final class PolicyTest extends GameTestInstance {
        private static final MapCodec<PolicyTest> CODEC = TestData.CODEC.xmap(PolicyTest::new, PolicyTest::info);

        private PolicyTest(TestData<Holder<TestEnvironmentDefinition<?>>> info) {
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
            return Component.literal("Server dimension effect authority");
        }
    }
}
