package org.academy.internal.common.ability.meltdowner.skills;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
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
import org.academy.internal.common.world.entity.EntityTypes;
import org.academy.internal.common.world.entity.skill.HighSpeedElectronBeam;
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

public class SingleHighSpeedElectronBeam extends Skill {
    public SingleHighSpeedElectronBeam() {
        super(Builder
                .of(AbilityCategories.MELTDOWNER.get())
                .level(AbilityLevel.LEVEL1)
                .cpCost(30)
                .iterationTicks(4)
                .maxStacks(1)
        );
    }

    @Override
    public void initClient() {
        RendererManager.registerEffectRenderer(TrailEffectWrapper.INSTANCE);
        var key = getKey();
        AcademyCraftConfig.registerTypeHandler(key, Client.Config.Handler.INSTANCE);
        var skillKeyConfig = AcademyCraftClient.Config.INSTANCE.<Client.Config>getConfig(key);

        InputSystem.addKeyBinding(Client.KEY_NAME_SHOOT, skillKeyConfig.getKeyBinding(
                Client.KEY_NAME_SHOOT,
                new InputSystem.InputPair(
                        InputSystem.InputType.MOUSE,
                        new InputSystem.KeyInfo(
                                new LinkedHashSet<>(Set.of(GLFW.GLFW_MOUSE_BUTTON_LEFT)),
                                GLFW.GLFW_RELEASE,
                                new LinkedHashSet<>(Set.of(GLFW.GLFW_MOD_ALT))
                        )
                )
        ), Client::handleKey);
    }

    @Override
    public void initServer(MinecraftServerContext context) {
        MisakaNetworkServer.NETWORK_MANAGER.register(Server.class);
    }

    public static final class Client {
        public static final String KEY_NAME_SHOOT = SkillNames.SINGLE_HIGH_SPEED_ELECTRON_BEAM + "_shoot";

        public static void handleKey() {
            if (!org.academy.api.client.ability.AbilitySystemClient.canUseSkill(Skills.SINGLE_HIGH_SPEED_ELECTRON_BEAM.get()))
                return;
            MisakaNetworkClient.sendPacket(ShootPacket.INSTANCE);
            var p = net.minecraft.client.Minecraft.getInstance().player;
            if (p == null) return;
            TrailEffectWrapper.INSTANCE.createTrail(1.5f, 0.06f, 0.9f, 0.5f, 0.1f)
                    .addPoint((float) p.getX(), (float) p.getEyeY(), (float) p.getZ());
        }

        public static class Config extends KeyBindingConfig {
            public static final class Handler implements TypeHandler<Config> {
                public static final TypeHandler<Config> INSTANCE = new Handler();

                private Handler() {
                }

                @Override
                public SingleHighSpeedElectronBeam.Client.Config getDefault() {
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
        @SubscribePacket
        public static void handle(ShootPacket packet) {
            var player = packet.getPacketListener().getPlayer();
            final var level = player.level();
            final var highSpeedElectronBeam = new HighSpeedElectronBeam(EntityTypes.HIGH_SPEED_ELECTRON_BEAM.get(), level);

            final var eyePos = player.getEyePosition().add(0, -0.5, 0);

            final var yaw = player.getYRot();
            final var pitch = player.getXRot();

            final var offsetFactor = 2;

            final var randomOffsetX = ((Math.random() * 1.5) - 0.75) * offsetFactor;
            final var randomOffsetZ = ((Math.random() * 1.5) - 0.75) * offsetFactor;
            final var randomOffsetY = ((Math.random() * 0.5) - 0.25) * offsetFactor;

            final var beamDistance = 1.75;
            final var yawRad = Math.toRadians(yaw);
            final var pitchRad = Math.toRadians(pitch);

            final var forwardOffsetX = -Math.sin(yawRad) * Math.cos(pitchRad) * beamDistance;
            final var forwardOffsetZ = Math.cos(yawRad) * Math.cos(pitchRad) * beamDistance;
            final var forwardOffsetY = -Math.sin(pitchRad) * beamDistance;

            final var spawnPos = eyePos.add(forwardOffsetX, forwardOffsetY, forwardOffsetZ).add(randomOffsetX, randomOffsetY, randomOffsetZ);

            highSpeedElectronBeam.setPos(spawnPos);
            highSpeedElectronBeam.setYRot(yaw);
            highSpeedElectronBeam.setXRot(pitch);

            level.addFreshEntity(highSpeedElectronBeam);
        }
    }

    @PacketTarget(ThreadType.SERVER)
    public static final class ShootPacket extends Packet<ServerGamePacketListenerImpl, ShootPacket> {
        public static final ShootPacket INSTANCE = new ShootPacket();
        public static final StreamCodec<ByteBuf, ShootPacket> CODEC = StreamCodec.unit(INSTANCE);

        private ShootPacket() {
        }

        @Override
        public PacketType<ServerGamePacketListenerImpl, ShootPacket> getPacketType() {
            return PacketTypes.SINGLE_HIGH_SPEED_ELECTRON_BEAM_SHOOT.get();
        }
    }
}