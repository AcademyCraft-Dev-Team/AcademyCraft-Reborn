package org.academy.internal.common.ability.builtin.meltdowner.skills;

import io.netty.buffer.Unpooled;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.academy.AcademyCraftClient;
import org.academy.api.client.input.InputSystem;
import org.academy.api.client.network.NetworkSystemClient;
import org.academy.api.common.ability.Skill;
import org.academy.api.common.network.NetworkResourceLocations;
import org.academy.api.common.network.packet.C2SPacket;
import org.academy.api.server.network.NetworkSystemServer;
import org.academy.internal.common.ability.builtin.SkillNames;
import org.academy.internal.common.world.entity.EntityTypes;
import org.academy.internal.common.world.entity.skill.HighSpeedElectronBeam;
import org.jetbrains.annotations.NotNull;
import org.lwjgl.glfw.GLFW;

import java.util.LinkedHashSet;
import java.util.Set;

public class SingleHighSpeedElectronBeam extends Skill {
    public static final Skill INSTANCE = new SingleHighSpeedElectronBeam();

    private SingleHighSpeedElectronBeam() {
        super(SkillNames.SINGLE_HIGH_SPEED_ELECTRON_BEAM, 1);
    }

    @Override
    public void initClient() {
        Client.KEY = AcademyCraftClient.CLIENT_CONFIG.getKey(
                Client.KEY_NAME,
                new InputSystem.InputPair(
                        InputSystem.InputType.MOUSE,
                        new InputSystem.InputEvent(
                                new LinkedHashSet<>(Set.of(GLFW.GLFW_MOUSE_BUTTON_LEFT)),
                                GLFW.GLFW_RELEASE,
                                new LinkedHashSet<>(Set.of(GLFW.GLFW_MOD_ALT))
                        )
                )
        );
        InputSystem.addKeyBinding(Client.KEY_NAME, Client.KEY, Client::handleKey);
    }

    @Override
    public void initServer(MinecraftServer server) {
        NetworkSystemServer.C2S_PACKET_HANDLER_MAP.put(NetworkResourceLocations.C2S_SINGLE_HIGH_SPEED_ELECTRON_BEAM_PACKET, (serverPacketListener, packet) -> Server.handle(serverPacketListener.player));
    }

    public static final class Client {
        public static InputSystem.InputPair KEY;
        public static final String KEY_NAME = "single_high_speed_electron_beam.shoot";

        public static void handleKey() {
     //       if (!ClientUtil.isScreenNull() || ClientUtil.lacksSkill(INSTANCE)) return;
            NetworkSystemClient.sendPacket(new C2SPacket(NetworkResourceLocations.C2S_SINGLE_HIGH_SPEED_ELECTRON_BEAM_PACKET, new FriendlyByteBuf(Unpooled.buffer())));
        }
    }

    public static final class Server {
        public static void handle(final @NotNull ServerPlayer player) {
     //       if (ServerUtil.lacksSkill(player.getUUID(), INSTANCE)) return;
            final Level level = player.level();
            final HighSpeedElectronBeam highSpeedElectronBeam = new HighSpeedElectronBeam(EntityTypes.HIGH_SPEED_ELECTRON_BEAM_ENTITY_TYPE, level);

            final Vec3 eyePos = player.getEyePosition().add(0, -0.5, 0);

            final float yaw = player.getYRot();
            final float pitch = player.getXRot();

            final double offsetFactor = 2;

            final double randomOffsetX = ((Math.random() * 1.5) - 0.75) * offsetFactor;
            final double randomOffsetZ = ((Math.random() * 1.5) - 0.75) * offsetFactor;
            final double randomOffsetY = ((Math.random() * 0.5) - 0.25) * offsetFactor;

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