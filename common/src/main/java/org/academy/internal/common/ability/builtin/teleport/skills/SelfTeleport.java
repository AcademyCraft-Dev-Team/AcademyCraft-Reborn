package org.academy.internal.common.ability.builtin.teleport.skills;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.annotations.SerializedName;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.RenderBuffers;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.academy.AcademyCraftClient;
import org.academy.AcademyCraftClientConfig;
import org.academy.api.client.config.IClientConfigActions;
import org.academy.api.client.input.InputSystem;
import org.academy.api.client.network.NetworkSystemClient;
import org.academy.api.client.renderer.CameraRenderer;
import org.academy.api.client.renderer.RendererManager;
import org.academy.api.client.util.ClientUtil;
import org.academy.api.client.util.RenderUtil;
import org.academy.api.common.ability.Skill;
import org.academy.api.common.network.SubscribePacket;
import org.academy.api.common.network.NetworkSystem;
import org.academy.api.common.network.PacketTarget;
import org.academy.api.common.network.packet.C2SPacket;
import org.academy.api.common.network.packet.EmptyPacket;
import org.academy.api.common.vanilla.ThreadType;
import org.academy.internal.common.ability.builtin.SkillNames;
import org.jetbrains.annotations.NotNull;
import org.lwjgl.glfw.GLFW;

import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

public final class SelfTeleport extends Skill {
    public static final Skill INSTANCE = new SelfTeleport();
    public static final float DISTANCE = 10F;
    public static InputSystem.InputPair KEY_START;
    public static InputSystem.InputPair KEY_END;
    public static Client.SelfTeleportClientConfigData CONFIG;

    static {
        NetworkSystem.registerPacketType(SelfTeleportPacket.class);
    }

    private SelfTeleport() {
        super(SkillNames.SELF_TELEPORT, 2);
    }

    @Override
    public void initClient() {
        AcademyCraftClientConfig.registerConfigActions(INSTANCE.name, new Client.SelfTeleportClientConfigData());
        CONFIG = AcademyCraftClient.CLIENT_CONFIG.getConfig(
                INSTANCE.name,
                Client.SelfTeleportClientConfigData.class
        );
        if (CONFIG == null) {
            CONFIG = new Client.SelfTeleportClientConfigData();
            AcademyCraftClient.CLIENT_CONFIG.setConfig(INSTANCE.name, CONFIG);
        }

        KEY_START = CONFIG.getKeyBinding(Client.KEY_NAME_START_ACTION,
                new InputSystem.InputPair(InputSystem.InputType.KEYBOARD, new InputSystem.KeyInfo(
                        new LinkedHashSet<>(Set.of(GLFW.GLFW_KEY_Z)),
                        GLFW.GLFW_PRESS,
                        new LinkedHashSet<>()
                )));
        KEY_END = CONFIG.getKeyBinding(Client.KEY_NAME_END_ACTION,
                new InputSystem.InputPair(InputSystem.InputType.KEYBOARD, new InputSystem.KeyInfo(
                        new LinkedHashSet<>(Set.of(GLFW.GLFW_KEY_Z)),
                        GLFW.GLFW_RELEASE,
                        new LinkedHashSet<>()
                )));

        InputSystem.addKeyBinding(Client.KEY_NAME_START_ACTION, KEY_START, Client::start);
        InputSystem.addKeyBinding(Client.KEY_NAME_END_ACTION, KEY_END, Client::end);
        RendererManager.registerCameraRenderer(Client.CAMERA_RENDERER);
    }

    @Override
    public void initServer(MinecraftServer server) {
        NetworkSystem.registerPacketListener(Server.class);
    }

    private static final class Server {
        @SubscribePacket
        public static void handleTeleport(SelfTeleportPacket packet) {
            ServerPlayer serverPlayer = packet.packetListenerSupplier.get().getPlayer();
            Vec3 lookDirection = serverPlayer.getLookAngle();
            Vec3 targetPosition = serverPlayer.position().add(lookDirection.scale(DISTANCE));
            serverPlayer.teleportTo(targetPosition.x, targetPosition.y, targetPosition.z);
            serverPlayer.resetFallDistance();
        }
    }

    private static final class Client {
        public static final String KEY_NAME_START_ACTION = "self_teleport.start_action";
        public static final String KEY_NAME_END_ACTION = "self_teleport.end_action";
        public static boolean started;
        private static final CameraRenderer CAMERA_RENDERER = (poseStack, f, l, bl, camera, gameRenderer, lightTexture, matrix4f) -> {
            Minecraft minecraft = Minecraft.getInstance();
            LocalPlayer localPlayer = minecraft.player;
            if (localPlayer != null && started) {
                poseStack.pushPose();
                final AABB aabb = new AABB(0, 0, 0, 1, 2, 1);

                final Vec3 lookVec = localPlayer.getLookAngle();
                final Vec3 offsetVec = lookVec.scale(DISTANCE);

                RenderUtil.applyCameraOffset(poseStack, Minecraft.getInstance().options.getCameraType(), lookVec);
                RenderUtil.applyOffset(poseStack, offsetVec);

                final RenderBuffers renderBuffers = minecraft.renderBuffers();

                poseStack.translate(-0.5f, -1f, -0.5f);
                RenderUtil.LineBoxRenderer.renderWireframeBox(poseStack, renderBuffers.bufferSource(), aabb, 1f, 1f, 1f, 1f);
                poseStack.popPose();
            }
        };

        private static void start() {
            if (ClientUtil.hasScreen()) return;
            started = true;
        }

        private static void end() {
            started = false;
            if (ClientUtil.hasScreen()) return;
            NetworkSystemClient.sendPacket(new C2SPacket(new SelfTeleportPacket()));
        }

        public static class SelfTeleportClientConfigData implements IClientConfigActions<SelfTeleportClientConfigData> {
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
            public @NotNull SelfTeleportClientConfigData deserialize(@NotNull JsonElement jsonElement, @NotNull Gson gson) {
                return gson.fromJson(jsonElement, SelfTeleportClientConfigData.class);
            }

            @Override
            public @NotNull JsonElement serialize(@NotNull SelfTeleportClientConfigData configInstance, @NotNull Gson gson) {
                return gson.toJsonTree(configInstance);
            }

            @Override
            public @NotNull SelfTeleportClientConfigData getDefaultConfig() {
                return new SelfTeleportClientConfigData();
            }

            @Override
            public @NotNull Class<SelfTeleportClientConfigData> getConfigClass() {
                return SelfTeleportClientConfigData.class;
            }
        }
    }

    @PacketTarget(ThreadType.SERVER)
    public static final class SelfTeleportPacket extends EmptyPacket<ServerGamePacketListenerImpl> {
    }
}