package org.academy.internal.common.ability.meltdowner.skills.lv3;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.academy.AcademyCraftClient;
import org.academy.AcademyCraftConfig;
import org.academy.api.client.config.KeyBindingConfig;
import org.academy.api.client.input.InputSystem;
import org.academy.api.common.ability.AbilityLevel;
import org.academy.api.common.ability.Skill;
import org.academy.api.common.gson.TypeHandler;
import org.academy.api.common.util.LevelUtil;
import org.academy.api.server.vanilla.MinecraftServerContext;
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

public class BetaParticleStream extends Skill {
    public BetaParticleStream() {
        super(Builder
                .of(AbilityCategories.MELTDOWNER.get())
                .level(AbilityLevel.LEVEL3)
                .cpCost(40)
                .iterationTicks(10)
                .maxStacks(1)
        );
    }

    @Override
    public void initClient() {
        var key = getKey();
        AcademyCraftConfig.registerTypeHandler(key, Client.Config.Action.INSTANCE);
        Client.CONFIG = AcademyCraftClient.Config.INSTANCE.getConfig(key);

        InputSystem.addKeyBinding(Client.KEY_NAME_CHARGE, Client.CONFIG.getKeyBinding(Client.KEY_NAME_CHARGE,
                new InputSystem.InputPair(InputSystem.InputType.KEYBOARD, new InputSystem.KeyInfo(
                        new LinkedHashSet<>(Set.of(GLFW.GLFW_KEY_F)), GLFW.GLFW_PRESS,
                        new LinkedHashSet<>(Set.of(GLFW.GLFW_MOD_ALT, GLFW.GLFW_MOD_SHIFT)))))
        , Client::onChargeStart);
        InputSystem.addKeyBinding(Client.KEY_NAME_FIRE, Client.CONFIG.getKeyBinding(Client.KEY_NAME_FIRE,
                new InputSystem.InputPair(InputSystem.InputType.KEYBOARD, new InputSystem.KeyInfo(
                        new LinkedHashSet<>(Set.of(GLFW.GLFW_KEY_F)), GLFW.GLFW_RELEASE,
                        new LinkedHashSet<>(Set.of(GLFW.GLFW_MOD_ALT, GLFW.GLFW_MOD_SHIFT)))))
        , Client::onFire);
    }

    @Override
    public void initServer(MinecraftServerContext context) {
        MisakaNetworkServer.NETWORK_MANAGER.register(Server.class);
    }

    public static final class Client {
        public static final String KEY_NAME_CHARGE = SkillNames.BETA_PARTICLE_STREAM + "_charge";
        public static final String KEY_NAME_FIRE = SkillNames.BETA_PARTICLE_STREAM + "_fire";
        public static Config CONFIG = new Config();
        private static long chargeStartTime;

        public static void onChargeStart() {
            chargeStartTime = System.nanoTime();
        }

        public static void onFire() {
            var elapsedMs = (System.nanoTime() - chargeStartTime) / 1_000_000f;
            var charges = Math.clamp((int)(elapsedMs / 750f), 1, 5);
            MisakaNetworkClient.send(new MulticastPacket(charges));
        }

        public static class Config extends KeyBindingConfig {
            public static final class Action implements TypeHandler<Config> {
                public static final TypeHandler<Config> INSTANCE = new Action();
                private Action() {}
                @Override public BetaParticleStream.Client.Config getDefault() { return new Config(); }
                @Override public Class<Config> getTypeClass() { return Config.class; }
            }
        }
    }

    public static final class Server {
        private static final float DAMAGE_PER_BEAM = 6.0f;
        private static final float RANGE = 16.0f;

        @SubscribePacket
        public static void handleMulticast(MulticastPacket packet) {
            var player = packet.getPacketListener().getPlayer();
            Skills.BETA_PARTICLE_STREAM.get().executeActive(player, (ctx, actualCost) -> {
                var level = player.level();
                if (!(level instanceof ServerLevel serverLevel)) return;

                var charges = packet.charges();
                var lookVec = player.getLookAngle();
                var eyePos = player.getEyePosition();
                var spreadAngle = 10.0 * (charges - 1) / 2.0;

                for (var i = 0; i < charges; i++) {
                    var offsetAngle = -spreadAngle + i * (2.0 * spreadAngle / Math.max(charges - 1, 1));
                    var offsetYawRad = Math.toRadians(player.getYRot() + offsetAngle);
                    var pitchRad = Math.toRadians(player.getXRot());

                    var dx = -Math.sin(offsetYawRad) * Math.cos(pitchRad);
                    var dy = -Math.sin(pitchRad);
                    var dz = Math.cos(offsetYawRad) * Math.cos(pitchRad);
                    var dirVec = new Vec3(dx, dy, dz);
                    var targetPos = eyePos.add(dirVec.scale(RANGE));

                    var src = player.damageSources().magic();
                    LevelUtil.attackEntitiesAlongPath(serverLevel, eyePos, targetPos, 0.25f, src, DAMAGE_PER_BEAM);
                }
            });
        }
    }

    @PacketTarget(ThreadType.SERVER)
    public static final class MulticastPacket extends Packet<ServerGamePacketListenerImpl, MulticastPacket> {
        private final int charges;
        public static final StreamCodec<ByteBuf, MulticastPacket> CODEC =
                ByteBufCodecs.INT.map(MulticastPacket::new, MulticastPacket::charges);

        public MulticastPacket(int charges) { this.charges = charges; }
        public int charges() { return charges; }

        @Override
        public PacketType<ServerGamePacketListenerImpl, MulticastPacket> getPacketType() {
            return PacketTypes.BETA_PARTICLE_STREAM_MULTICAST.get();
        }
    }
}
