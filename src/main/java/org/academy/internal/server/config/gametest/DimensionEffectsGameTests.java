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
            config.pvp = new DimensionEffectsConfig.DimensionRule();
            config.blockDestruction = new DimensionEffectsConfig.DimensionRule();
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

            config.pvp.whitelist = Set.of(dimension);
            config.enabled = true;
            helper.assertTrue(PvpSetting.protectionReason(attacker, target) == PvpSetting.ProtectionReason.NONE,
                    "PVP whitelist must override both players' disabled switches");
            helper.assertTrue(!DestroyBlocksSetting.canDestroyBlocks(attacker)
                            && !DestroyBlocksSetting.canDestroyBlocks(attacker, Skills.RAILGUN.get())
                            && !DestroyBlocksSetting.canDestroyBlocks(attacker, skill),
                    "PVP-only config must let players disable global and per-skill block destruction");
            attacker.setData(AttachmentTypes.SKILL_DESTROY_BLOCKS_ENABLED.get(), Map.of());
            helper.assertTrue(DestroyBlocksSetting.canDestroyBlocks(attacker, skill),
                    "PVP-only config must preserve mining beam's independent switch");
            helper.assertTrue(!DestroyBlocksSetting.canDestroyBlocks(attacker, Skills.RAILGUN.get()),
                    "PVP-only config must still honor the global block switch for other skills");
            attacker.setData(AttachmentTypes.SKILL_DESTROY_BLOCKS_ENABLED.get(),
                    Map.of(skill.getKeyString(), false));

            config.pvp.whitelist = Set.of();
            config.blockDestruction.whitelist = Set.of(dimension);
            helper.assertTrue(PvpSetting.protectionReason(attacker, target)
                            == PvpSetting.ProtectionReason.ATTACKER_DISABLED,
                    "Block-only config must preserve personal PVP protection");
            helper.assertTrue(DestroyBlocksSetting.canDestroyBlocks(attacker)
                            && DestroyBlocksSetting.canDestroyBlocks(attacker, skill),
                    "Block whitelist must override global and independent per-skill switches");

            config.pvp.whitelist = Set.of(dimension);
            config.pvp.blacklist = Set.of(Level.NETHER.identifier().toString());
            config.blockDestruction.blacklist = Set.of(Level.NETHER.identifier().toString());
            var nether = level.getServer().getLevel(Level.NETHER);
            helper.assertTrue(nether != null, "Nether level must be present");
            helper.assertTrue(AbilityEffectPolicy.blockDestruction(nether) == AbilityEffectPolicy.Decision.DENY,
                    "Simultaneous white and black lists must distinguish levels on the same server");
            helper.assertTrue(AbilityEffectPolicy.blockDestruction(level) == AbilityEffectPolicy.Decision.ALLOW,
                    "Adding a blacklist must not suppress other whitelisted dimensions");
            var endLevel = level.getServer().getLevel(Level.END);
            helper.assertTrue(endLevel != null, "End level must be present");
            helper.assertTrue(AbilityEffectPolicy.pvp(endLevel) == AbilityEffectPolicy.Decision.DEFAULT
                            && AbilityEffectPolicy.blockDestruction(endLevel) == AbilityEffectPolicy.Decision.DEFAULT,
                    "Dimensions outside both lists must remain player-controlled");
            var remoteProfile = new GameProfile(UUID.randomUUID(), "policy-remote");
            var remoteTarget = new ServerPlayer(level.getServer(), nether, remoteProfile,
                    CommonListenerCookie.createInitial(remoteProfile, false).clientInformation());
            helper.assertTrue(PvpSetting.protectionReason(attacker, remoteTarget)
                            == PvpSetting.ProtectionReason.DIMENSION_DISABLED,
                    "PVP must use the target dimension, not the attacker's allowed dimension");
            helper.assertTrue(!AbilityBlockDrops.run(nether, attacker, () -> {
                throw new AssertionError("Denied remote block action was executed");
            }), "Remote block actions must use their actual effect level");

            config.pvp.blacklist = Set.of(dimension);
            helper.assertTrue(PvpSetting.protectionReason(attacker, target)
                            == PvpSetting.ProtectionReason.DIMENSION_DISABLED,
                    "PVP blacklist must take precedence over its whitelist");
            helper.assertTrue(DestroyBlocksSetting.canDestroyBlocks(attacker, skill),
                    "PVP denial must not override a block destruction whitelist");
            config.pvp.blacklist = Set.of();
            config.blockDestruction.blacklist = Set.of(dimension);
            helper.assertTrue(PvpSetting.protectionReason(attacker, target) == PvpSetting.ProtectionReason.NONE,
                    "Block destruction denial must not override a PVP whitelist");

            config.pvp.blacklist = Set.of(dimension);
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
                    "Mining beam and overlapping whitelists must not bypass a forbidden dimension");
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

            config.blockDestruction.blacklist = Set.of();
            helper.assertTrue(AbilityBlockDrops.destroyBlock(level, absolute, false, attacker),
                    "Whitelisted ability block destruction must still execute");
            helper.assertTrue(level.getBlockState(absolute).isAir(), "Allowed block was not destroyed");

            // Keep nonempty lists for another dimension; neither may take over this dimension.
            config.pvp.whitelist = config.blockDestruction.whitelist = Set.of("example:arena");
            config.pvp.blacklist = config.blockDestruction.blacklist = Set.of("example:protected");
            helper.assertTrue(PvpSetting.protectionReason(attacker, target) == PvpSetting.ProtectionReason.NONE,
                    "Unlisted players must be able to enable PVP");
            target.setData(AttachmentTypes.PVP_ENABLED.get(), false);
            helper.assertTrue(PvpSetting.protectionReason(attacker, target)
                            == PvpSetting.ProtectionReason.TARGET_DISABLED,
                    "Unlisted players must be able to disable PVP");
            helper.assertTrue(DestroyBlocksSetting.canDestroyBlocks(attacker, skill),
                    "Unlisted players must be able to enable per-skill destruction");
            attacker.setData(AttachmentTypes.DESTROY_BLOCKS_ENABLED.get(), false);
            attacker.setData(AttachmentTypes.SKILL_DESTROY_BLOCKS_ENABLED.get(),
                    Map.of(skill.getKeyString(), false));
            helper.assertTrue(!DestroyBlocksSetting.canDestroyBlocks(attacker)
                            && !DestroyBlocksSetting.canDestroyBlocks(attacker, skill),
                    "Unlisted players must be able to disable global and per-skill destruction");
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
