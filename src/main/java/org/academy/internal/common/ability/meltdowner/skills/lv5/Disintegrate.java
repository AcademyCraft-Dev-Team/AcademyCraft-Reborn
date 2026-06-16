package org.academy.internal.common.ability.meltdowner.skills.lv5;

import io.netty.buffer.ByteBuf;
import net.minecraft.client.Minecraft;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.world.entity.LivingEntity;
import org.academy.AcademyCraftClient;
import org.academy.AcademyCraftConfig;
import org.academy.api.client.config.KeyBindingConfig;
import org.academy.api.client.input.InputSystem;
import org.academy.api.client.renderer.RendererManager;
import org.academy.api.common.ability.AbilityLevel;
import org.academy.api.common.ability.Skill;
import org.academy.api.common.gson.TypeHandler;
import org.academy.api.common.util.LevelUtil;
import org.academy.api.server.vanilla.MinecraftServerContext;
import org.academy.internal.client.renderer.effect.ParticleEffectWrapper;
import org.academy.internal.client.renderer.effect.TrailEffectWrapper;
import org.academy.internal.common.ability.AbilityCategories;
import org.academy.internal.common.ability.SkillNames;
import org.academy.internal.common.ability.Skills;
import org.academy.internal.common.network.PacketTypes;
import org.lwjgl.glfw.GLFW;
import org.misaka.MisakaNetworkClient;
import org.misaka.MisakaNetworkServer;
import org.misaka.api.common.network.ThreadType;
import org.misaka.api.common.network.annotation.PacketTarget;
import org.misaka.api.common.network.annotation.SubscribePacket;
import org.misaka.api.common.network.packet.Packet;
import org.misaka.api.common.network.packet.PacketType;
import java.util.*;

public class Disintegrate extends Skill {
    public Disintegrate() {
        super(Builder.of(AbilityCategories.MELTDOWNER.get()).level(AbilityLevel.LEVEL5).cpCost(200).iterationTicks(60).maxStacks(1));
    }

    @Override public void initClient() {
        var key = getKey();
        AcademyCraftConfig.registerTypeHandler(key, Client.Config.Action.INSTANCE);
        Client.CONFIG = AcademyCraftClient.Config.INSTANCE.getConfig(key);
        RendererManager.registerEffectRenderer(TrailEffectWrapper.INSTANCE);
        RendererManager.registerEffectRenderer(ParticleEffectWrapper.INSTANCE);
        InputSystem.addKeyBinding(Client.KEY, Client.CONFIG.getKeyBinding(Client.KEY,
                new InputSystem.InputPair(InputSystem.InputType.KEYBOARD, new InputSystem.KeyInfo(
                        new LinkedHashSet<>(Set.of(GLFW.GLFW_KEY_K)), GLFW.GLFW_RELEASE,
                        new LinkedHashSet<>(Set.of(GLFW.GLFW_MOD_ALT, GLFW.GLFW_MOD_SHIFT)))))
        , Client::onUse);
    }
    @Override public void initServer(MinecraftServerContext c) { MisakaNetworkServer.NETWORK_MANAGER.register(Server.class); }

    public static final class Client {
        public static final String KEY = SkillNames.DISINTEGRATE + "_use";
        public static Config CONFIG = new Config();
        public static void onUse() {
            if (!org.academy.api.client.ability.AbilitySystemClient.canUseSkill(Skills.DISINTEGRATE.get())) return;
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
            MisakaNetworkClient.sendPacket(UsePacket.INSTANCE);
        }
        public static class Config extends KeyBindingConfig {
            public static final class Action implements TypeHandler<Config> {
                public static final TypeHandler<Config> INSTANCE = new Action();
                private Action() {} @Override public Disintegrate.Client.Config getDefault() { return new Config(); }
                @Override public Class<Config> getTypeClass() { return Config.class; }
            }
        }
    }

    public static final class Server {
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
                    LevelUtil.destroyBlocksAlongPath(sl, eye, target, 0.2f, 999, true, true, true, false);
                }
                double dmg = 0;
                for (var e : l.getEntitiesOfClass(LivingEntity.class, new net.minecraft.world.phys.AABB(eye, target).inflate(1), e -> e != player && e.isAlive()))
                    dmg = e.getHealth() * 0.99;
                for (var e : l.getEntitiesOfClass(LivingEntity.class, new net.minecraft.world.phys.AABB(eye, target).inflate(1), e -> e != player && e.isAlive()))
                    e.hurtServer(l, l.damageSources().magic(), (float)dmg);
            });
        }
    }

    @PacketTarget(ThreadType.SERVER)
    public static final class UsePacket extends Packet<ServerGamePacketListenerImpl, UsePacket> {
        public static final UsePacket INSTANCE = new UsePacket();
        public static final StreamCodec<ByteBuf, UsePacket> CODEC = StreamCodec.unit(INSTANCE);
        private UsePacket() {} @Override public PacketType<ServerGamePacketListenerImpl, UsePacket> getPacketType() { return PacketTypes.DISINTEGRATE_USE.get(); }
    }
}
