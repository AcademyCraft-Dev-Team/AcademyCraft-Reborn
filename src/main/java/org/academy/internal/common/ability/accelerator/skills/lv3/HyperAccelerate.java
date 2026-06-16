package org.academy.internal.common.ability.accelerator.skills.lv3;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.game.ClientboundSetEntityMotionPacket;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.world.phys.Vec3;
import org.academy.AcademyCraftClient;
import org.academy.AcademyCraftConfig;
import org.academy.api.client.config.KeyBindingConfig;
import org.academy.api.client.input.InputSystem;
import org.academy.api.client.renderer.RendererManager;
import org.academy.api.common.ability.AbilityLevel;
import org.academy.api.common.ability.Skill;
import org.academy.api.common.gson.TypeHandler;
import org.academy.api.server.vanilla.MinecraftServerContext;
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

import java.util.LinkedHashSet;
import java.util.Set;

public class HyperAccelerate extends Skill {
    public HyperAccelerate() {
        super(Builder
                .of(AbilityCategories.ACCELERATOR.get())
                .level(AbilityLevel.LEVEL3)
                .cpCost(50)
                .iterationTicks(8)
                .maxStacks(1)
                .dependsOn(Skills.VECTOR_ACCEL)
        );
    }

    @Override
    public int getMaxStacks(int skillLevel) {
        if (skillLevel >= 2) return 2;
        return super.getMaxStacks(skillLevel);
    }

    @Override
    public void initClient() {
        var key = getKey();
        AcademyCraftConfig.registerTypeHandler(key, Client.Config.Action.INSTANCE);
        Client.CONFIG = AcademyCraftClient.Config.INSTANCE.getConfig(key);
        RendererManager.registerEffectRenderer(TrailEffectWrapper.INSTANCE);

        InputSystem.addKeyBinding(Client.KEY_NAME_PRESS, Client.CONFIG.getKeyBinding(Client.KEY_NAME_PRESS,
                new InputSystem.InputPair(InputSystem.InputType.KEYBOARD, new InputSystem.KeyInfo(
                        new LinkedHashSet<>(Set.of(GLFW.GLFW_KEY_C)),
                        GLFW.GLFW_PRESS,
                        new LinkedHashSet<>(Set.of(GLFW.GLFW_MOD_SHIFT)))
                )
        ), Client::onChargeStart);
        InputSystem.addKeyBinding(Client.KEY_NAME_USE, Client.CONFIG.getKeyBinding(Client.KEY_NAME_USE,
                new InputSystem.InputPair(InputSystem.InputType.KEYBOARD, new InputSystem.KeyInfo(
                        new LinkedHashSet<>(Set.of(GLFW.GLFW_KEY_C)),
                        GLFW.GLFW_RELEASE,
                        new LinkedHashSet<>(Set.of(GLFW.GLFW_MOD_SHIFT)))
                )
        ), Client::onUse);
    }

    @Override
    public void initServer(MinecraftServerContext context) {
        MisakaNetworkServer.NETWORK_MANAGER.register(Server.class);
    }

    public static final class Client {
        public static final String KEY_NAME_PRESS = SkillNames.HYPER_ACCELERATE + "_press";
        public static final String KEY_NAME_USE = SkillNames.HYPER_ACCELERATE + "_use";
        public static Config CONFIG = new Config();
        private static long chargeStartTime;

        public static void onChargeStart() {
            chargeStartTime = System.nanoTime();
        }

        public static void onUse() {
            var mc = net.minecraft.client.Minecraft.getInstance();
            if (mc.player == null) return;
            var lookVec = mc.player.getViewVector(1.0f);
            var elapsedMs = (System.nanoTime() - chargeStartTime) / 1_000_000f;
            var chargeRatio = Math.clamp(elapsedMs / 2000f, 0.1f, 1.0f);
            MisakaNetworkClient.send(new LaunchPacket(chargeRatio, lookVec));
            var p = mc.player;
            var trail = TrailEffectWrapper.INSTANCE.createTrail(0.8f, 0.15f, 0.3f, 0.7f, 1.0f);
            trail.addPoint((float) p.getX(), (float) p.getY(), (float) p.getZ());
        }

        public static class Config extends KeyBindingConfig {
            public static final class Action implements TypeHandler<Config> {
                public static final TypeHandler<Config> INSTANCE = new Action();
                private Action() {}
                @Override public HyperAccelerate.Client.Config getDefault() { return new Config(); }
                @Override public Class<Config> getTypeClass() { return Config.class; }
            }
        }
    }

    public static final class Server {
        private static final double MAX_LAUNCH_SPEED = 3.0;

        @SubscribePacket
        public static void handle(LaunchPacket packet) {
            var player = packet.getPacketListener().getPlayer();
            Skills.HYPER_ACCELERATE.get().executeActive(player, (ctx, actualCost) -> {
                var dir = packet.getDirection();
                var chargeRatio = packet.getChargeRatio();
                var speed = MAX_LAUNCH_SPEED * (0.5 + 0.5 * chargeRatio);
                var velocity = dir.normalize().scale(speed);
                player.setDeltaMovement(velocity);
                player.resetFallDistance();
                player.connection.send(new ClientboundSetEntityMotionPacket(player));

                var nearby = player.level().getEntitiesOfClass(net.minecraft.world.entity.LivingEntity.class,
                        player.getBoundingBox().inflate(3.0), e -> e != player && e.isAlive());
                for (var target : nearby) {
                    target.setDeltaMovement(target.position().subtract(player.position()).normalize().scale(0.5));
                    target.hurtMarked = true;
                    target.hurtServer(player.level(), player.damageSources().playerAttack(player), 1.0f);
                }
            });
        }
    }

    @PacketTarget(ThreadType.SERVER)
    public static final class LaunchPacket extends Packet<ServerGamePacketListenerImpl, LaunchPacket> {
        private static final StreamCodec<ByteBuf, Vec3> VEC3_CODEC = StreamCodec.composite(
                ByteBufCodecs.DOUBLE, Vec3::x, ByteBufCodecs.DOUBLE, Vec3::y, ByteBufCodecs.DOUBLE, Vec3::z, Vec3::new);
        public static final StreamCodec<ByteBuf, LaunchPacket> CODEC = StreamCodec.composite(
                ByteBufCodecs.FLOAT, LaunchPacket::getChargeRatio,
                VEC3_CODEC, LaunchPacket::getDirection,
                LaunchPacket::new
        );
        private final float chargeRatio;
        private final Vec3 direction;

        public LaunchPacket(float chargeRatio, Vec3 direction) { this.chargeRatio = chargeRatio; this.direction = direction; }
        public float getChargeRatio() { return chargeRatio; }
        public Vec3 getDirection() { return direction; }
        @Override public PacketType<ServerGamePacketListenerImpl, LaunchPacket> getPacketType() {
            return PacketTypes.HYPER_ACCELERATE_LAUNCH.get();
        }
    }
}
