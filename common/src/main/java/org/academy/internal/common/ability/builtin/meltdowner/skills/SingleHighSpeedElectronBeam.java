package org.academy.internal.common.ability.builtin.meltdowner.skills;

import io.netty.buffer.Unpooled;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.academy.AcademyCraftClient;
import org.academy.AcademyCraftClientConfig;
import org.academy.api.client.input.InputSystem;
import org.academy.api.client.network.AcademyCraftNetworkSystemClient;
import org.academy.api.client.util.ClientUtil;
import org.academy.api.common.ability.Skill;
import org.academy.api.common.network.AcademyCraftNetworkResourceLocations;
import org.academy.api.common.network.packet.ClientToServerPacket;
import org.academy.api.server.network.AcademyCraftNetworkSystemServer;
import org.academy.internal.common.world.entity.AcademyCraftEntityTypes;
import org.academy.internal.common.world.entity.skill.HighSpeedElectronBeam;
import org.jetbrains.annotations.NotNull;
import org.lwjgl.glfw.GLFW;

import java.util.*;

public class SingleHighSpeedElectronBeam extends Skill {
    public static final Skill INSTANCE = new SingleHighSpeedElectronBeam();
    public static final String KEY_NAME_START = "single_high_speed_electron_beam.start_shoot";
    public static final String KEY_NAME_END = "single_high_speed_electron_beam.end_shoot";
    public static AcademyCraftClientConfig.InputPair KEY_START;
    public static AcademyCraftClientConfig.InputPair KEY_END;

    private SingleHighSpeedElectronBeam() {
        super("single_high_speed_electron_beam", 1);
    }

    @Override
    public void initClient() {
        KEY_START = AcademyCraftClient.clientConfig.getKey(
                KEY_NAME_START,
                new AcademyCraftClientConfig.InputPair(
                        AcademyCraftClientConfig.InputType.MOUSE,
                        new InputSystem.InputEvent(
                                new LinkedHashSet<>(Set.of(GLFW.GLFW_MOUSE_BUTTON_LEFT)),
                                GLFW.GLFW_PRESS,
                                new LinkedHashSet<>(Set.of(GLFW.GLFW_MOD_ALT))
                        )
                )
        );
        KEY_END = AcademyCraftClient.clientConfig.getKey(
                KEY_NAME_END,
                new AcademyCraftClientConfig.InputPair(
                        AcademyCraftClientConfig.InputType.MOUSE,
                        new InputSystem.InputEvent(
                                new LinkedHashSet<>(Set.of(GLFW.GLFW_MOUSE_BUTTON_LEFT)),
                                GLFW.GLFW_RELEASE,
                                new LinkedHashSet<>()
                        )
                )
        );
        InputSystem.registerKeyBinding(KEY_NAME_START, KEY_START, Client::handleKeyStart);
        InputSystem.registerKeyBinding(KEY_NAME_END, KEY_END, Client::handleKeyEnd);
    }

    @Override
    public void initServer(MinecraftServer server) {
        AcademyCraftNetworkSystemServer.CLIENT_TO_SERVER_PACKET_HANDLER_MAP.put(AcademyCraftNetworkResourceLocations.C2S_SINGLE_HIGH_SPEED_ELECTRON_BEAM_PACKET, (serverGamePacketListenerImpl, packet) -> Server.handle(((ServerGamePacketListenerImpl) serverGamePacketListenerImpl).player, packet.friendlyByteBuf.readLong()));
    }

    public static final class Client {
        public static boolean pressed = false;
        public static long pressTime = 0;
        public static long releaseTime = 0;

        public static void handleKeyStart() {
            if (ClientUtil.isScreenNull()) {
                pressTime = System.currentTimeMillis();
                pressed = true;
            }
        }

        public static void handleKeyEnd() {
            if (pressed) {
                releaseTime = System.currentTimeMillis();
                final long time = releaseTime - pressTime;
                AcademyCraftNetworkSystemClient.sendPacket(new ClientToServerPacket(AcademyCraftNetworkResourceLocations.C2S_SINGLE_HIGH_SPEED_ELECTRON_BEAM_PACKET, new FriendlyByteBuf(Unpooled.buffer().writeLong(time))));
                pressed = false;
            }
        }
    }

    public static final class Server {
        public static final Map<UUID, UUID> PLAYER_BEAM = new HashMap<>();

        public static void handle(final @NotNull ServerPlayer player, final long time) {
            final Level level = player.level();
            final HighSpeedElectronBeam highSpeedElectronBeam = new HighSpeedElectronBeam(AcademyCraftEntityTypes.HIGH_SPEED_ELECTRON_BEAM_ENTITY_TYPE, level);

            final Vec3 eyePos = player.getEyePosition().add(0, -0.5, 0);

            final float yaw = player.getYRot();
            final float pitch = player.getXRot();

            final double offsetFactor = 2;

            final double randomOffsetX = ((Math.random() * 1.5) - 0.75) * offsetFactor; // (-0.75 ~ 0.75) * offsetFactor
            final double randomOffsetZ = ((Math.random() * 1.5) - 0.75) * offsetFactor; // (-0.75 ~ 0.75) * offsetFactor
            final double randomOffsetY = ((Math.random() * 0.5) - 0.25) * offsetFactor; // (-0.25 ~ 0.25) * offsetFactor

            final double beamDistance = 1.75;
            final double yawRad = Math.toRadians(yaw);
            final double pitchRad = Math.toRadians(pitch);

            final double forwardOffsetX = -Math.sin(yawRad) * Math.cos(pitchRad) * beamDistance;
            final double forwardOffsetZ = Math.cos(yawRad) * Math.cos(pitchRad) * beamDistance;
            final double forwardOffsetY = -Math.sin(pitchRad) * beamDistance;

            final Vec3 spawnPos = eyePos.add(forwardOffsetX, forwardOffsetY, forwardOffsetZ).add(randomOffsetX, randomOffsetY, randomOffsetZ);

            highSpeedElectronBeam.setPos(spawnPos);
            highSpeedElectronBeam.setYRot(yaw);
            highSpeedElectronBeam.setXRot(pitch);

            level.addFreshEntity(highSpeedElectronBeam);
        }
    }
}