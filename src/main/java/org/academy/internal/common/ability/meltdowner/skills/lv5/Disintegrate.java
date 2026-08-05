package org.academy.internal.common.ability.meltdowner.skills.lv5;

import com.mojang.blaze3d.platform.InputConstants;
import io.netty.buffer.ByteBuf;
import net.minecraft.client.Minecraft;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.academy.AcademyCraftClient;
import org.academy.AcademyCraftConfig;
import org.academy.api.client.ability.AbilitySystemClient;
import org.academy.api.client.config.KeyBindingConfig;
import org.academy.api.client.input.InputSystem;
import org.academy.api.client.renderer.RendererManager;
import org.academy.api.common.ability.AbilityLevel;
import org.academy.api.common.ability.Skill;
import org.academy.api.common.damage.SkillDamageSource;
import org.academy.api.common.gson.TypeHandler;
import org.academy.api.common.util.LevelUtil;
import org.academy.api.server.vanilla.MinecraftServerContext;
import org.academy.internal.client.renderer.effect.ParticleEffectWrapper;
import org.academy.internal.client.renderer.effect.TrailEffectWrapper;
import org.academy.internal.common.ability.AbilityCategories;
import org.academy.internal.common.ability.SkillNames;
import org.academy.internal.common.ability.Skills;
import org.academy.internal.common.network.PacketTypes;
import org.academy.internal.common.world.damagesource.DestroyBlocksSetting;
import org.misaka.MisakaNetworkClient;
import org.misaka.MisakaNetworkServer;
import org.misaka.api.common.network.ThreadType;
import org.misaka.api.common.network.annotation.PacketTarget;
import org.misaka.api.common.network.annotation.SubscribePacket;
import org.misaka.api.common.network.packet.Packet;
import org.misaka.api.common.network.packet.PacketType;

public class Disintegrate extends Skill {
    public Disintegrate() {
        super(Builder.of(AbilityCategories.MELTDOWNER.get()).level(AbilityLevel.LEVEL5).energyCost(100_000).cpCost(200).iterationTicks(60).maxStacks(1));
    }

    @Override
    public void initClient() {
        var key = getKey();
        AcademyCraftConfig.registerTypeHandler(key, Client.Config.Action.INSTANCE);
        Client.CONFIG = AcademyCraftClient.Config.INSTANCE.getConfig(key);
        RendererManager.registerEffectRenderer(TrailEffectWrapper.INSTANCE);
        RendererManager.registerEffectRenderer(ParticleEffectWrapper.INSTANCE);
        InputSystem.addKeyBinding(Client.KEY, Client.CONFIG.getKeyBinding(Client.KEY,
                        InputSystem.combo(InputSystem.InputType.KEYBOARD, InputConstants.KEY_K, InputConstants.PRESS, InputConstants.MOD_ALT | InputConstants.MOD_SHIFT))
                , ctx -> Client.onUse());
    }

    @Override
    public void initServer(MinecraftServerContext c) {
        MisakaNetworkServer.NETWORK_MANAGER.register(Server.class);
    }

    public static final class Client {
        public static final String KEY = SkillNames.DISINTEGRATE + "_use";
        public static Config CONFIG = new Config();

        public static void onUse() {
            if (!AbilitySystemClient.canUseSkill(Skills.DISINTEGRATE.get())) return;
            var p = Minecraft.getInstance().player;
            if (p != null) {
                var trail = TrailEffectWrapper.INSTANCE.createTrail(0.6f, 0.03f, 0.2f, 1.0f, 0.3f);
                trail.addPoint((float) p.getX(), (float) p.getEyeY(), (float) p.getZ());
                var emitter = ParticleEffectWrapper.INSTANCE.createEmitter(
                        (float) p.getX(), (float) p.getEyeY(), (float) p.getZ());
                emitter.setColor(0.2f, 0.9f, 0.3f);
                emitter.setEmissionRate(0);
                emitter.burst(20);
                emitter.setLifetime(0.8f, 0.3f);
            }
            MisakaNetworkClient.send(UsePacket.INSTANCE);
        }

        public static class Config extends KeyBindingConfig {
            public static final class Action implements TypeHandler<Config> {
                public static final TypeHandler<Config> INSTANCE = new Action();

                private Action() {
                }

                @Override
                public Disintegrate.Client.Config getDefault() {
                    return new Config();
                }

                @Override
                public Class<Config> getTypeClass() {
                    return Config.class;
                }
            }
        }
    }

    public static final class Server {
        public static float calculateDamage(float currentHealth, float playerMultiplier) {
            return Math.max(0.0f, currentHealth) * 0.99f * Math.max(0.0f, playerMultiplier);
        }

        @SubscribePacket
        public static void handle(UsePacket p) {
            var player = p.getPacketListener().getPlayer();
            Skills.DISINTEGRATE.get().executeActive(player, (ctx, c) -> {
                var l = player.level();
                var eye = player.getEyePosition();
                var look = player.getLookAngle();
                var range = LevelUtil.getValidViewDistance(player, 30);
                var target = eye.add(look.scale(range));
                if (l instanceof ServerLevel sl) {
                    if (DestroyBlocksSetting.canDestroyBlocks(player, Skills.DISINTEGRATE.get())) {
                        LevelUtil.destroyBlocksAlongPath(
                                sl, eye, target, 0.2f, 999,
                                true, true, true, false, player
                        );
                    }
                    var multiplier = ctx.system().getPlayerDamageMultiplier(player.getUUID());
                    var source = SkillDamageSource.of(player, Skills.DISINTEGRATE.get());
                    var box = new AABB(eye, target).inflate(1.0);
                    for (var entity : sl.getEntitiesOfClass(LivingEntity.class, box,
                            entity -> entity != player && entity.isAlive()
                                    && !player.isAlliedTo(entity)
                                    && distanceToSegmentSqr(entity.getBoundingBox().getCenter(), eye, target) <= 1.0)) {
                        entity.hurtServer(sl, source,
                                calculateDamage(entity.getHealth(), multiplier));
                    }
                    var delta = target.subtract(eye);
                    for (var i = 0; i <= 24; i++) {
                        var point = eye.add(delta.scale(i / 24.0));
                        sl.sendParticles(ParticleTypes.END_ROD,
                                point.x, point.y, point.z, 1, 0.02, 0.02, 0.02, 0.0);
                    }
                }
            });
        }

        private static double distanceToSegmentSqr(Vec3 point,
                                                   Vec3 start,
                                                   Vec3 end) {
            var segment = end.subtract(start);
            var lengthSqr = segment.lengthSqr();
            if (lengthSqr < 1.0e-9) return point.distanceToSqr(start);
            var t = Math.clamp(point.subtract(start).dot(segment) / lengthSqr, 0.0, 1.0);
            return point.distanceToSqr(start.add(segment.scale(t)));
        }
    }

    @PacketTarget(ThreadType.SERVER)
    public static final class UsePacket extends Packet<ServerGamePacketListenerImpl, UsePacket> {
        public static final UsePacket INSTANCE = new UsePacket();
        public static final StreamCodec<ByteBuf, UsePacket> CODEC = StreamCodec.unit(INSTANCE);

        private UsePacket() {
        }

        @Override
        public PacketType<ServerGamePacketListenerImpl, UsePacket> getPacketType() {
            return PacketTypes.DISINTEGRATE_USE.get();
        }
    }
}
