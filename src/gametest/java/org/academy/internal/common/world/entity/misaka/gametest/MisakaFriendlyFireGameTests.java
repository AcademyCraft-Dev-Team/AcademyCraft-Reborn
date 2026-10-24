package org.academy.internal.common.world.entity.misaka.gametest;

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
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterGameTestsEvent;
import net.neoforged.neoforge.registries.RegisterEvent;
import org.academy.AcademyCraft;
import org.academy.api.common.damage.SkillDamageSource;
import org.academy.internal.common.ability.Skills;
import org.academy.internal.common.ability.meltdowner.targeting.MeltdownerTargeting;
import org.academy.internal.common.world.damagesource.CtaFriendlyFireWhitelist;
import org.academy.internal.common.world.damagesource.DamageTypes;
import org.academy.internal.common.world.damagesource.FriendlyFireSetting;
import org.academy.internal.common.world.damagesource.ReflectedSkillDamageSource;
import org.academy.internal.common.world.damagesource.SkillDamageUtil;
import org.academy.internal.common.world.entity.EntityTypes;
import org.academy.internal.common.world.entity.misaka.MisakaSisterEntity;
import org.academy.internal.server.world.level.storage.MisakaSisterRoster;

import java.util.List;
import java.util.UUID;

@EventBusSubscriber(modid = AcademyCraft.MOD_ID)
public final class MisakaFriendlyFireGameTests {
    private static final Identifier TYPE = AcademyCraft.academy("misaka_friendly_fire_function");

    private MisakaFriendlyFireGameTests() {
    }

    @SubscribeEvent
    private static void registerType(RegisterEvent event) {
        event.register(Registries.TEST_INSTANCE_TYPE, TYPE, () -> FriendlyFireTest.CODEC);
    }

    @SubscribeEvent
    private static void registerTests(RegisterGameTestsEvent event) {
        var environment = event.registerEnvironment(
                AcademyCraft.academy("misaka/friendly_fire"),
                new TestEnvironmentDefinition.AllOf(List.of()));
        var data = new TestData<>(environment, Identifier.withDefaultNamespace("empty"),
                100, 0, true, Rotation.NONE, false, 1, 1, false, 16);
        event.registerTest(AcademyCraft.academy("misaka_friendly_fire"), new FriendlyFireTest(data));
    }

    /** Also exercised with a real player by the opt-in client smoke probe. */
    public static void verify(ServerPlayer player) {
        boolean previous = FriendlyFireSetting.isFriendlyFireEnabled(player);
        try {
            FriendlyFireSetting.setFriendlyFireEnabled(player, false);
            verifyProtected(player, false);
            verifyProtected(player, true);
            verifyOrdinaryDamage(player);
            FriendlyFireSetting.setFriendlyFireEnabled(player, true);
            verifyEnabled(player);
        } finally {
            FriendlyFireSetting.setFriendlyFireEnabled(player, previous);
        }
    }

    private static void verifyProtected(ServerPlayer player, boolean awakened) {
        var sister = spawnSister(player);
        try {
            var record = sister.rosterRecord().orElseThrow();
            record.awakened = awakened;
            record.favorByPlayerName.put(player.getGameProfile().name(), -20);
            sister.bindToRecord(record);
            // Even retaliation and a recent attack must not bypass the disabled switch.
            sister.setTarget(player);
            sister.setLastHurtMob(player);
            require(FriendlyFireSetting.shouldPrevent(player, sister), "Shared friendly-fire gate");
            require(CtaFriendlyFireWhitelist.shouldProtect(player, sister), "CTA hostility exception");
            require(!MeltdownerTargeting.canAffectNegatively(player, sister), "Meltdowner targeting");
            float health = sister.getHealth();
            var skill = Skills.ARC_GENERATE.get();
            for (var type : List.of(DamageTypes.RAILGUN, DamageTypes.AERO_DAMAGE,
                    DamageTypes.LAMINAR_CUT, DamageTypes.VACUUM_SUFFOCATION,
                    DamageTypes.ADIABATIC_COMPRESSION, DamageTypes.DM_DAMAGE,
                    DamageTypes.ELECTRO_DAMAGE, DamageTypes.MELT_DAMAGE,
                    DamageTypes.SPACE_DAMAGE, DamageTypes.VEC, DamageTypes.CTA,
                    DamageTypes.MENTAL_DAMAGE)) {
                var source = SkillDamageSource.of(player, skill, type);
                require(!SkillDamageUtil.apply(player, sister, skill, type, 2), "Category apply: " + type);
                require(!SkillDamageUtil.apply(player, sister, skill, type, 2, 1), "Percent apply: " + type);
                require(!SkillDamageUtil.applyDirect(player.level(), sister, source, 2), "Direct apply: " + type);
                require(!sister.hurtServer(player.level(), source, 2), "Entity hurt: " + type);
                var plain = new DamageSource(source.typeHolder(), player);
                require(!SkillDamageUtil.applyDirect(player.level(), sister, plain, 2), "Plain category: " + type);
                require(!sister.hurtServer(player.level(), plain, 2), "Plain entity hurt: " + type);
                var reflected = ReflectedSkillDamageSource.from(source, player, skill, null);
                require(!SkillDamageUtil.applyDirect(player.level(), sister, reflected, 2), "Reflected hit: " + type);
                if (DamageTypes.usesVerifiedTrueHealth(type)) {
                    require(!SkillDamageUtil.applyVerifiedTrueHealth(sister, source, 2), "True health: " + type);
                }
            }
            require(sister.getHealth() == health, "Protected health must not change");
            require(!record.incapacitated, "Protected sister must not be incapacitated");
            require(sister.getLastHurtByMob() == null, "Protected hits must not mark an attacker");
            // Use positive favor to detect accidental incoming-damage events (deadly favor is clamped).
            record.favorByPlayerName.put(player.getGameProfile().name(), 10);
            var source = SkillDamageSource.of(player, skill, DamageTypes.ELECTRO_DAMAGE);
            require(!sister.hurtServer(player.level(), source, 2), "Protect positive favor");
            require(record.favorByPlayerName.get(player.getGameProfile().name()) == 10,
                    "Rejected skill hits must not lower favor");
        } finally {
            discardSister(sister);
        }
    }

    private static void verifyEnabled(ServerPlayer player) {
        for (var type : List.of(DamageTypes.ELECTRO_DAMAGE, DamageTypes.MELT_DAMAGE, DamageTypes.CTA)) {
            var sister = spawnSister(player);
            try {
                require(!FriendlyFireSetting.shouldPrevent(player, sister), "Enabled switch permits sister");
                float health = sister.getHealth();
                require(SkillDamageUtil.apply(player, sister, Skills.ARC_GENERATE.get(), type, 2),
                        "Enabled category should hit: " + type);
                require(sister.getHealth() < health, "Enabled hit must reduce health: " + type);
            } finally {
                discardSister(sister);
            }
        }
    }

    private static void verifyOrdinaryDamage(ServerPlayer player) {
        var sister = spawnSister(player);
        try {
            require(sister.hurtServer(player.level(), player.damageSources().playerAttack(player), 2),
                    "Ordinary melee remains outside skill friendly-fire policy");
            sister.invulnerableTime = 0;
            require(sister.hurtServer(player.level(), player.damageSources().generic(), 2),
                    "Environmental damage remains available");
        } finally {
            discardSister(sister);
        }
        var caster = spawnSister(player);
        var target = spawnSister(player);
        try {
            var source = SkillDamageSource.of(caster, Skills.ARC_GENERATE.get());
            require(target.hurtServer(player.level(), source, 2), "Non-player skill damage remains available");
        } finally {
            discardSister(caster);
            discardSister(target);
        }
    }

    private static MisakaSisterEntity spawnSister(ServerPlayer player) {
        var sister = EntityTypes.MISAKA_SISTER.get().create(player.level(), EntitySpawnReason.COMMAND);
        require(sister != null, "Sister factory");
        sister.snapTo(player.getX() + 2, player.getY(), player.getZ(), 0, 0);
        sister.setNoAi(true);
        require(player.level().addFreshEntity(sister), "Add sister");
        require(sister.rosterRecord().isPresent(), "Sister roster registration");
        return sister;
    }

    private static void discardSister(MisakaSisterEntity sister) {
        var uuid = sister.getMisakaUuid();
        sister.discard();
        if (uuid != null) MisakaSisterRoster.get(sister.level().getServer()).release(uuid);
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new IllegalStateException(message);
    }

    private static void run(GameTestHelper helper) {
        var profile = new GameProfile(UUID.randomUUID(), "misaka-ff-test");
        var cookie = CommonListenerCookie.createInitial(profile, false);
        var player = new ServerPlayer(helper.getLevel().getServer(), helper.getLevel(),
                profile, cookie.clientInformation());
        var position = helper.absoluteVec(new Vec3(1.5, 2, 1.5));
        player.snapTo(position.x, position.y, position.z, 0, 0);
        var connection = new Connection(PacketFlow.SERVERBOUND);
        new EmbeddedChannel(connection);
        helper.getLevel().getServer().getPlayerList().placeNewPlayer(connection, player, cookie);
        player.setGameMode(GameType.SURVIVAL);
        try {
            verify(player);
            helper.succeed();
        } finally {
            helper.getLevel().getServer().getPlayerList().remove(player);
        }
    }

    private static final class FriendlyFireTest extends GameTestInstance {
        private static final MapCodec<FriendlyFireTest> CODEC =
                TestData.CODEC.xmap(FriendlyFireTest::new, FriendlyFireTest::info);

        private FriendlyFireTest(TestData<Holder<TestEnvironmentDefinition<?>>> info) {
            super(info);
        }

        @Override
        public void run(GameTestHelper helper) {
            MisakaFriendlyFireGameTests.run(helper);
        }

        @Override
        public MapCodec<? extends GameTestInstance> codec() {
            return CODEC;
        }

        @Override
        protected MutableComponent typeDescription() {
            return Component.literal("Misaka sister skill friendly-fire protection");
        }
    }
}
