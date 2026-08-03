package org.academy.internal.common.ability.darkmatter.skills;

import com.mojang.blaze3d.platform.InputConstants;
import io.netty.buffer.ByteBuf;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import org.academy.AcademyCraftClient;
import org.academy.AcademyCraftConfig;
import org.academy.api.client.ability.AbilitySystemClient;
import org.academy.api.client.config.KeyBindingConfig;
import org.academy.api.client.input.InputSystem;
import org.academy.api.client.resources.R;
import org.academy.api.client.util.ClientUtil;
import org.academy.api.common.ability.AbilityLevel;
import org.academy.api.common.ability.DevCondition;
import org.academy.api.common.ability.Skill;
import org.academy.api.common.damage.SkillDamageSource;
import org.academy.api.common.gson.TypeHandler;
import org.academy.api.server.ability.AbilitySystemServer;
import org.academy.api.server.vanilla.MinecraftServerContext;
import org.academy.internal.common.ability.AbilityCategories;
import org.academy.internal.common.ability.SkillNames;
import org.academy.internal.common.ability.Skills;
import org.academy.internal.common.network.PacketTypes;
import org.academy.internal.common.world.entity.EntityTypes;
import org.academy.internal.common.world.entity.skill.DarkmatterCutSlash;
import org.misaka.MisakaNetworkClient;
import org.misaka.MisakaNetworkServer;
import org.misaka.api.common.network.ThreadType;
import org.misaka.api.common.network.annotation.PacketTarget;
import org.misaka.api.common.network.annotation.SubscribePacket;
import org.misaka.api.common.network.packet.Packet;
import org.misaka.api.common.network.packet.PacketType;

import java.util.List;

public final class DarkmatterCut extends Skill {
    static final double RADIUS = 8.0;
    static final double SIX_WINGS_RADIUS = 24.0;
    static final double MIN_DOT = 0.5;
    static final float BASE_DAMAGE = 12.0f;
    static final float SIX_WINGS_DAMAGE = 16.0f;

    public DarkmatterCut() {
        super(Builder
                .of(AbilityCategories.DARKMATTER.get())
                .level(AbilityLevel.LEVEL2)
                .energyCost(10_000)
                .cpCost(40)
                .iterationTicks(20)
                .maxStacks(1)
                .dependsOn(Skills.DARKMATTER_DISASSEMBLE)
                .devCondition(new DevCondition.LevelCondition(AbilityLevel.LEVEL2))
                .devCondition(new DevCondition.DependencyCondition(
                        "Dark Matter Disassemble", "academy:darkmatter_disassemble"))
        );
    }

    @Override
    public void initClient() {
        var key = getKey();
        AcademyCraftConfig.registerTypeHandler(key, Client.Config.Action.INSTANCE);
        Client.CONFIG = AcademyCraftClient.Config.INSTANCE.getConfig(key);
        InputSystem.addKeyBinding(Client.KEY_NAME_CAST, Client.CONFIG.getKeyBinding(
                Client.KEY_NAME_CAST,
                InputSystem.combo(InputSystem.InputType.KEYBOARD, InputConstants.KEY_R,
                        InputConstants.RELEASE, 0)
        ), context -> Client.cast());
    }

    @Override
    public void initServer(MinecraftServerContext context) {
        MisakaNetworkServer.NETWORK_MANAGER.register(Server.class);
    }

    public static final class Client {
        public static final AbilitySystemClient.SkillInfo SKILL_INFO = AbilitySystemClient.addSkillInfo(
                AbilityCategories.DARKMATTER.get(),
                new AbilitySystemClient.SkillInfo(
                        Skills.DARKMATTER_CUT.get(),
                        List.of(DarkmatterDisassemble.Client.SKILL_INFO),
                        R.textures.darkmatter_cut_icon,
                        20,
                        104
                )
        );
        public static final String KEY_NAME_CAST = SkillNames.DARKMATTER_CUT + "_cast";
        public static Config CONFIG = new Config();

        private Client() {
        }

        private static void cast() {
            if (ClientUtil.hasScreen() || !AbilitySystemClient.canUseSkill(Skills.DARKMATTER_CUT.get())) return;
            MisakaNetworkClient.send(CastPacket.INSTANCE);
        }

        public static class Config extends KeyBindingConfig {
            public static final class Action implements TypeHandler<Config> {
                public static final TypeHandler<Config> INSTANCE = new Action();
                private Action() {
                }
                @Override public Config getDefault() { return new Config(); }
                @Override public Class<Config> getTypeClass() { return Config.class; }
            }
        }
    }

    public static final class Server {
        private Server() {
        }

        @SubscribePacket
        public static void handle(CastPacket packet) {
            var player = packet.getPacketListener().getPlayer();
            if (!(player.level() instanceof ServerLevel level)) return;
            var enhanced = DarkmatterSixWings.Server.isActive(player);
            var radius = enhanced ? SIX_WINGS_RADIUS : RADIUS;
            var origin = player.position().add(0, player.getBbHeight() * 0.5, 0);
            var look = horizontalLook(player.getLookAngle(), player.getYRot());
            var targets = level.getEntitiesOfClass(LivingEntity.class,
                    player.getBoundingBox().inflate(radius), target ->
                            target != player && target.isAlive() && !target.isRemoved()
                                    && !player.isAlliedTo(target)
                                    && insideCone(origin, look, target.getBoundingBox().getCenter(),
                                    radius, MIN_DOT));
            var skill = Skills.DARKMATTER_CUT.get();
            skill.executeActive(player, (context, actualCost) -> {
                spawnSlash(level, player, enhanced ? 3.0f : 1.0f);
                level.playSound(null, player.blockPosition(), SoundEvents.PLAYER_ATTACK_SWEEP,
                        SoundSource.PLAYERS, 1.0f, 1.0f);
                var damage = (enhanced ? SIX_WINGS_DAMAGE : BASE_DAMAGE)
                        * AbilitySystemServer.getSystem(player)
                        .getPlayerDamageMultiplier(player.getUUID());
                var source = SkillDamageSource.of(player, skill);
                for (var target : targets) {
                    if (!target.hurtServer(level, source, damage)) continue;
                    level.sendParticles(ParticleTypes.SWEEP_ATTACK,
                            target.getX(), target.getY() + target.getBbHeight() * 0.5, target.getZ(),
                            6, 0.25, 0.25, 0.25, 0.01);
                }
            });
        }

        static Vec3 horizontalLook(Vec3 look, float yaw) {
            var horizontal = new Vec3(look.x, 0, look.z);
            if (horizontal.lengthSqr() < 1.0e-6) horizontal = Vec3.directionFromRotation(0, yaw);
            return horizontal.normalize();
        }

        static boolean insideCone(Vec3 origin, Vec3 look, Vec3 target,
                                  double radius, double minimumDot) {
            var offset = target.subtract(origin);
            if (offset.lengthSqr() > radius * radius) return false;
            var horizontal = new Vec3(offset.x, 0, offset.z);
            return horizontal.lengthSqr() > 1.0e-6
                    && look.dot(horizontal.normalize()) >= minimumDot;
        }

        private static void spawnSlash(ServerLevel level, net.minecraft.server.level.ServerPlayer player,
                                       float scale) {
            var slash = new DarkmatterCutSlash(EntityTypes.DARKMATTER_CUT_SLASH.get(), level);
            var position = player.position().add(0, player.getBbHeight() * 0.3, 0)
                    .add(player.getLookAngle().normalize().scale(1.85));
            slash.setPos(position);
            slash.setYRot(player.getYRot());
            slash.setXRot(player.getXRot() + player.getRandom().nextFloat() * 60 - 30);
            slash.setScale(scale);
            slash.setDuration(4);
            slash.setSwingDirection(player.getRandom().nextBoolean() ? 1 : -1);
            level.addFreshEntity(slash);
        }
    }

    @PacketTarget(ThreadType.SERVER)
    public static final class CastPacket extends Packet<ServerGamePacketListenerImpl, CastPacket> {
        public static final CastPacket INSTANCE = new CastPacket();
        public static final StreamCodec<ByteBuf, CastPacket> CODEC = StreamCodec.unit(INSTANCE);
        private CastPacket() {
        }
        @Override public PacketType<ServerGamePacketListenerImpl, CastPacket> getPacketType() {
            return PacketTypes.DARKMATTER_CUT_CAST.get();
        }
    }
}
