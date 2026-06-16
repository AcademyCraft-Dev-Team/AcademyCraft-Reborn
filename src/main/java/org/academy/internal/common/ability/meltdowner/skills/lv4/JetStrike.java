package org.academy.internal.common.ability.meltdowner.skills.lv4;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.game.ClientboundSetEntityMotionPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import org.academy.AcademyCraftClient;
import org.academy.AcademyCraftConfig;
import org.academy.api.client.config.KeyBindingConfig;
import org.academy.api.client.input.InputSystem;
import org.academy.api.client.renderer.RendererManager;
import org.academy.api.common.ability.AbilityLevel;
import org.academy.api.common.ability.Skill;
import org.academy.api.common.gson.TypeHandler;
import org.academy.api.server.ability.AbilitySystemServer;
import org.academy.api.server.ability.ServerContext;
import org.academy.api.server.vanilla.MinecraftServerContext;
import org.academy.internal.common.ability.AbilityCategories;
import org.academy.internal.common.ability.SkillNames;
import org.academy.internal.common.ability.Skills;
import org.academy.internal.common.network.PacketTypes;
import org.academy.internal.client.renderer.effect.ParticleEffectWrapper;
import org.academy.internal.client.renderer.effect.TrailEffectWrapper;
import org.academy.internal.common.world.entity.EntityTypes;
import org.academy.internal.common.world.entity.skill.Smoke;
import org.lwjgl.glfw.GLFW;
import org.misaka.MisakaNetworkClient;
import org.misaka.MisakaNetworkServer;
import org.misaka.api.common.network.ThreadType;
import org.misaka.api.common.network.annotation.PacketTarget;
import org.misaka.api.common.network.annotation.SubscribePacket;
import org.misaka.api.common.network.packet.Packet;
import org.misaka.api.common.network.packet.PacketType;

import java.util.*;

public class JetStrike extends Skill {
    public JetStrike() {
        super(Builder
                .of(AbilityCategories.MELTDOWNER.get())
                .level(AbilityLevel.LEVEL4)
                .cpCost(160)
                .iterationTicks(4)
                .maxStacks(1)
        );
    }

    @Override
    public void initClient() {
        RendererManager.registerEffectRenderer(TrailEffectWrapper.INSTANCE);
        RendererManager.registerEffectRenderer(ParticleEffectWrapper.INSTANCE);
        var key = getKey();
        AcademyCraftConfig.registerTypeHandler(key, Client.Config.Action.INSTANCE);
        Client.CONFIG = AcademyCraftClient.Config.INSTANCE.getConfig(key);
        InputSystem.addKeyBinding(Client.KEY_NAME_USE, Client.CONFIG.getKeyBinding(Client.KEY_NAME_USE,
                new InputSystem.InputPair(InputSystem.InputType.KEYBOARD, new InputSystem.KeyInfo(
                        new LinkedHashSet<>(Set.of(GLFW.GLFW_KEY_J)), GLFW.GLFW_RELEASE,
                        new LinkedHashSet<>(Set.of(GLFW.GLFW_MOD_ALT, GLFW.GLFW_MOD_SHIFT)))))
        , Client::onUse);
    }

    @Override
    public void initServer(MinecraftServerContext context) {
        MisakaNetworkServer.NETWORK_MANAGER.register(Server.class);
    }

    public static final class Client {
        public static final String KEY_NAME_USE = SkillNames.JET_STRIKE + "_use";
        public static Config CONFIG = new Config();
        public static void onUse() {
            var mc = net.minecraft.client.Minecraft.getInstance();
            if (mc.player == null) return;
            MisakaNetworkClient.sendPacket(new DashPacket(mc.player.getViewVector(1.0f)));
            var p = mc.player;
            TrailEffectWrapper.INSTANCE.createTrail(1.5f, 0.1f, 1.0f, 0.4f, 0.1f)
                    .addPoint((float) p.getX(), (float) p.getY(), (float) p.getZ());
            var emitter = ParticleEffectWrapper.INSTANCE.createEmitter(
                    (float) p.getX(), (float) p.getY() + 0.5f, (float) p.getZ());
            emitter.setColor(1.0f, 0.6f, 0.2f);
            emitter.setEmissionRate(0);
            emitter.burst(15);
            emitter.setLifetime(1.5f, 0.5f);
            emitter.setVelocity(0.5f, 0.3f);
        }
        public static class Config extends KeyBindingConfig {
            public static final class Action implements TypeHandler<Config> {
                public static final TypeHandler<Config> INSTANCE = new Action();
                private Action() {}
                @Override public JetStrike.Client.Config getDefault() { return new Config(); }
                @Override public Class<Config> getTypeClass() { return Config.class; }
            }
        }
    }

    public static final class Server {
        @SubscribePacket
        public static void handle(DashPacket packet) {
            var player = packet.getPacketListener().getPlayer();
            Skills.JET_STRIKE.get().executeActive(player, (ctx, actualCost) -> {
                var dir = packet.getDirection().normalize();
                var speed = 2.5;
                player.setDeltaMovement(dir.scale(speed));
                player.resetFallDistance();
                player.connection.send(new ClientboundSetEntityMotionPacket(player));
                AbilitySystemServer.registerContext(new Context(player, dir));
            });
        }
    }

    public static final class Context extends ServerContext {
        private final Vec3 direction;
        private int trailTicks = 10;
        private boolean ended;

        private Context(ServerPlayer player, Vec3 direction) { super(player); this.direction = direction; }

        @SubscribeEvent
        public void onTick(ServerTickEvent.Pre event) {
            trailTicks--;
            if (trailTicks < 0 || player.hasDisconnected() || !player.isAlive()) { end(); return; }

            if (trailTicks % 2 == 0 && level() instanceof ServerLevel serverLevel) {
                var pos = player.position().add(0, 0.5, 0);
                var smoke = new Smoke(EntityTypes.SMOKE.get(), level());
                smoke.setPos(pos);
                level().addFreshEntity(smoke);

                var box = player.getBoundingBox().inflate(1.0);
                var targets = level().getEntitiesOfClass(LivingEntity.class, box, e -> e != player && e.isAlive());
                for (var target : targets) {
                    target.hurtServer(serverLevel, player.damageSources().magic(), 8.0f);
                    target.setRemainingFireTicks(40);
                }
            }
        }

        private void end() { if (ended) return; ended = true; unregister(); }
    }

    @PacketTarget(ThreadType.SERVER)
    public static final class DashPacket extends Packet<ServerGamePacketListenerImpl, DashPacket> {
        private static final StreamCodec<ByteBuf, Vec3> VEC3_CODEC = StreamCodec.composite(
                ByteBufCodecs.DOUBLE, Vec3::x, ByteBufCodecs.DOUBLE, Vec3::y, ByteBufCodecs.DOUBLE, Vec3::z, Vec3::new);
        public static final StreamCodec<ByteBuf, DashPacket> CODEC = VEC3_CODEC.map(DashPacket::new, DashPacket::getDirection);
        private final Vec3 direction;
        public DashPacket(Vec3 direction) { this.direction = direction; }
        public Vec3 getDirection() { return direction; }
        @Override public PacketType<ServerGamePacketListenerImpl, DashPacket> getPacketType() {
            return PacketTypes.JET_STRIKE_DASH.get();
        }
    }
}
