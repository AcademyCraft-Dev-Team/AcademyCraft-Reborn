package org.academy.internal.common.ability.builtin.meltdowner.skills;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.annotations.SerializedName;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.academy.AcademyCraftClient;
import org.academy.AcademyCraftClientConfig;
import org.academy.AcademyCraftServer;
import org.academy.api.client.config.IClientConfigActions;
import org.academy.api.client.input.InputSystem;
import org.academy.api.client.network.NetworkSystemClient;
import org.academy.api.common.ability.Skill;
import org.academy.api.common.network.PacketTarget;
import org.academy.api.common.network.SubscribePacket;
import org.academy.api.common.network.packet.C2SPacket;
import org.academy.api.common.network.packet.EmptyPacket;
import org.academy.api.common.vanilla.ThreadType;
import org.academy.internal.common.ability.builtin.SkillNames;
import org.academy.internal.common.world.entity.EntityTypes;
import org.academy.internal.common.world.entity.skill.HighSpeedElectronBeam;
import org.jetbrains.annotations.NotNull;
import org.lwjgl.glfw.GLFW;

import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

public class SingleHighSpeedElectronBeam extends Skill {
    public static final Skill INSTANCE = new SingleHighSpeedElectronBeam();

    private SingleHighSpeedElectronBeam() {
        super(SkillNames.SINGLE_HIGH_SPEED_ELECTRON_BEAM, 1);
    }

    @Override
    public void initClient() {
        AcademyCraftClientConfig.registerConfigActions(INSTANCE.name, new Client.SingleHighSpeedElectronBeamClientConfigData());
        Client.SingleHighSpeedElectronBeamClientConfigData skillKeyConfig = AcademyCraftClient.CLIENT_CONFIG.getConfig(
                INSTANCE.name,
                Client.SingleHighSpeedElectronBeamClientConfigData.class
        );
        if (skillKeyConfig == null) {
            skillKeyConfig = new Client.SingleHighSpeedElectronBeamClientConfigData();
            AcademyCraftClient.CLIENT_CONFIG.setConfig(INSTANCE.name, skillKeyConfig);
        }

        Client.KEY = skillKeyConfig.getKeyBinding(
                Client.KEY_NAME_ACTION,
                new InputSystem.InputPair(
                        InputSystem.InputType.MOUSE,
                        new InputSystem.KeyInfo(
                                new LinkedHashSet<>(Set.of(GLFW.GLFW_MOUSE_BUTTON_LEFT)),
                                GLFW.GLFW_RELEASE,
                                new LinkedHashSet<>(Set.of(GLFW.GLFW_MOD_ALT))
                        )
                )
        );
        InputSystem.addKeyBinding(Client.KEY_NAME_ACTION, Client.KEY, Client::handleKey);
    }

    @Override
    public void initServer(MinecraftServer server) {
        AcademyCraftServer.NETWORK_SYSTEM_SERVER_INSTANCE.registerPacketListener(Server.class);
    }

    public static final class Client {
        public static final String KEY_NAME_ACTION = "single_high_speed_electron_beam.shoot_action";
        public static InputSystem.InputPair KEY;

        public static void handleKey() {
            NetworkSystemClient.sendPacket(new C2SPacket(new ShootPacket()));
        }

        public static class SingleHighSpeedElectronBeamClientConfigData implements IClientConfigActions<SingleHighSpeedElectronBeamClientConfigData> {
            @SerializedName("keyBindings")
            private final Map<String, InputSystem.InputPair> keyBindings = new HashMap<>();

            public InputSystem.InputPair getKeyBinding(String name, InputSystem.InputPair defaultConfig) {
                if (!keyBindings.containsKey(name)) {
                    setKeyBinding(name, defaultConfig);
                }
                return keyBindings.get(name);
            }
            public void setKeyBinding(String name, InputSystem.InputPair keyBinding) {
                this.keyBindings.put(name, keyBinding);
            }

            @Override
            public @NotNull SingleHighSpeedElectronBeamClientConfigData deserialize(@NotNull JsonElement jsonElement, @NotNull Gson gson) {
                return gson.fromJson(jsonElement, SingleHighSpeedElectronBeamClientConfigData.class);
            }

            @Override
            public @NotNull JsonElement serialize(@NotNull SingleHighSpeedElectronBeamClientConfigData configInstance, @NotNull Gson gson) {
                return gson.toJsonTree(configInstance);
            }

            @Override
            public @NotNull SingleHighSpeedElectronBeamClientConfigData getDefaultConfig() {
                return new SingleHighSpeedElectronBeamClientConfigData();
            }

            @Override
            public @NotNull Class<SingleHighSpeedElectronBeamClientConfigData> getConfigClass() {
                return SingleHighSpeedElectronBeamClientConfigData.class;
            }
        }
    }

    public static final class Server {
        @SubscribePacket
        public static void handle(ShootPacket packet) {
            ServerPlayer player = packet.packetListenerSupplier.get().getPlayer();
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

    @PacketTarget(ThreadType.SERVER)
    public static final class ShootPacket extends EmptyPacket<ServerGamePacketListenerImpl> {
    }
}