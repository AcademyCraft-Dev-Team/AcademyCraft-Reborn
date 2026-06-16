package org.academy.internal.common.ability.teleport.skills.lv3;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.game.ClientboundSetEntityMotionPacket;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.world.entity.Pose;
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
import org.academy.internal.client.renderer.effect.DistortionEffectWrapper;
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

public class CutThrough extends Skill {
    public CutThrough() {
        super(Builder
                .of(AbilityCategories.TELEPORT.get())
                .level(AbilityLevel.LEVEL3)
                .cpCost(20)
                .iterationTicks(20)
                .maxStacks(1)
                .dependsOn(Skills.SELF_TELEPORT)
        );
    }

    @Override
    public float getCpCost(int skillLevel) {
        return 20 * getMaxDistance(skillLevel);
    }

    public float getMaxDistance(int level) {
        if (level >= 3) return 24.0f;
        return 16.0f;
    }

    @Override
    public void initClient() {
        var key = getKey();
        AcademyCraftConfig.registerTypeHandler(key, Client.Config.Action.INSTANCE);
        Client.CONFIG = AcademyCraftClient.Config.INSTANCE.getConfig(key);
        RendererManager.registerEffectRenderer(DistortionEffectWrapper.INSTANCE);

        InputSystem.addKeyBinding(Client.KEY_NAME_USE, Client.CONFIG.getKeyBinding(Client.KEY_NAME_USE,
                new InputSystem.InputPair(InputSystem.InputType.KEYBOARD, new InputSystem.KeyInfo(
                        new LinkedHashSet<>(Set.of(GLFW.GLFW_KEY_F)),
                        GLFW.GLFW_RELEASE,
                        new LinkedHashSet<>(Set.of(GLFW.GLFW_MOD_ALT, GLFW.GLFW_MOD_CONTROL)))
                )
        ), Client::onUse);
    }

    @Override
    public void initServer(MinecraftServerContext context) {
        MisakaNetworkServer.NETWORK_MANAGER.register(Server.class);
    }

    public static final class Client {
        public static final String KEY_NAME_USE = SkillNames.CUT_THROUGH + "_use";
        public static Config CONFIG = new Config();

        public static void onUse() {
            var mc = net.minecraft.client.Minecraft.getInstance();
            if (mc.player == null) return;
            var lookVec = mc.player.getViewVector(1.0f);
            MisakaNetworkClient.sendPacket(new TeleportPacket(lookVec));
            var p = mc.player;
            DistortionEffectWrapper.INSTANCE.trigger(
                    (float) p.getX(), (float) p.getY() + 1.0f, (float) p.getZ(),
                    1.0f, 1.0f,
                    0.5f, 0.2f, 0.8f, 0.7f,
                    0.1f, 0.0f, 0.3f, 0.0f);
        }

        public static class Config extends KeyBindingConfig {
            public static final class Action implements TypeHandler<Config> {
                public static final TypeHandler<Config> INSTANCE = new Action();
                private Action() {}
                @Override public CutThrough.Client.Config getDefault() { return new Config(); }
                @Override public Class<Config> getTypeClass() { return Config.class; }
            }
        }
    }

    public static final class Server {
        @SubscribePacket
        public static void handle(TeleportPacket packet) {
            var player = packet.getPacketListener().getPlayer();
            Skills.CUT_THROUGH.get().executeActive(player, (ctx, actualCost) -> {
                var skill = Skills.CUT_THROUGH.get();
                var maxDist = skill.getMaxDistance(ctx.level());
                var dir = packet.getDirection().normalize();
                var targetPos = player.getEyePosition().add(dir.scale(maxDist));
                var dimensions = player.getDimensions(Pose.STANDING);
                var teleportY = targetPos.y() - (dimensions.height() / 2.0);
                var teleportX = targetPos.x();
                var teleportZ = targetPos.z();

                player.teleportTo(teleportX, teleportY, teleportZ);
                player.resetFallDistance();
                player.setDeltaMovement(dir.scale(0.1));
                player.connection.send(new ClientboundSetEntityMotionPacket(player));
            });
        }
    }

    @PacketTarget(ThreadType.SERVER)
    public static final class TeleportPacket extends Packet<ServerGamePacketListenerImpl, TeleportPacket> {
        private static final StreamCodec<ByteBuf, Vec3> VEC3_CODEC = StreamCodec.composite(
                ByteBufCodecs.DOUBLE, Vec3::x, ByteBufCodecs.DOUBLE, Vec3::y, ByteBufCodecs.DOUBLE, Vec3::z, Vec3::new);
        public static final StreamCodec<ByteBuf, TeleportPacket> CODEC = VEC3_CODEC.map(TeleportPacket::new, TeleportPacket::getDirection);
        private final Vec3 direction;

        public TeleportPacket(Vec3 direction) { this.direction = direction; }
        public Vec3 getDirection() { return direction; }
        @Override public PacketType<ServerGamePacketListenerImpl, TeleportPacket> getPacketType() {
            return PacketTypes.CUT_THROUGH_TELEPORT.get();
        }
    }
}
