package org.academy.internal.common.ability.builtin.teleport.skills;

import io.netty.buffer.Unpooled;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.RenderBuffers;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.academy.AcademyCraftClient;
import org.academy.AcademyCraftClientConfig;
import org.academy.api.client.input.InputSystem;
import org.academy.api.client.network.AcademyCraftNetworkSystemClient;
import org.academy.api.client.render.AcademyCraftRenderSystem;
import org.academy.api.client.util.ClientUtil;
import org.academy.api.client.util.RenderUtil;
import org.academy.api.common.ability.Skill;
import org.academy.api.common.network.AcademyCraftNetworkResourceLocations;
import org.academy.api.common.network.packet.ClientToServerPacket;
import org.academy.api.server.network.AcademyCraftNetworkSystemServer;
import org.lwjgl.glfw.GLFW;

import java.util.LinkedHashSet;
import java.util.Set;

public final class SelfTeleport extends Skill {
    public static final Skill INSTANCE = new SelfTeleport();
    public static final String NAME = "self_teleport";
    public static final String KEY_NAME_START = "self_teleport.start";
    public static final String KEY_NAME_END = "self_teleport.end";
    public static AcademyCraftClientConfig.InputPair KEY_START;
    public static AcademyCraftClientConfig.InputPair KEY_END;
    public static final float DISTANCE = 10f;

    private SelfTeleport() {
        super(NAME, 2);
    }

    @Override
    public void initClient() {
        KEY_START = AcademyCraftClient.clientConfig.getKey(KEY_NAME_START,
                new AcademyCraftClientConfig.InputPair(AcademyCraftClientConfig.InputType.KEYBOARD, new InputSystem.InputEvent(
                        new LinkedHashSet<>(Set.of(GLFW.GLFW_KEY_E)),
                        GLFW.GLFW_PRESS,
                        new LinkedHashSet<>()
                )));
        KEY_END = AcademyCraftClient.clientConfig.getKey(KEY_NAME_END,
                new AcademyCraftClientConfig.InputPair(AcademyCraftClientConfig.InputType.KEYBOARD, new InputSystem.InputEvent(
                        new LinkedHashSet<>(Set.of(GLFW.GLFW_KEY_E)),
                        GLFW.GLFW_RELEASE,
                        new LinkedHashSet<>()
                )));

        InputSystem.registerKeyBinding(KEY_NAME_START, KEY_START, Client::start);
        InputSystem.registerKeyBinding(KEY_NAME_END, KEY_END, Client::end);
    }

    @Override
    public void initServer(MinecraftServer server) {
        AcademyCraftNetworkSystemServer.CLIENT_TO_SERVER_PACKET_HANDLER_MAP.put(AcademyCraftNetworkResourceLocations.C2S_SELF_TELEPORT_PACKET, (serverPacketListener, packet) -> Server.handleTeleport(serverPacketListener.player));
    }

    private static final class Server {
        private static void handleTeleport(ServerPlayer serverPlayer) {
            Vec3 lookDirection = serverPlayer.getLookAngle();
            Vec3 targetPosition = serverPlayer.position().add(lookDirection.scale(DISTANCE));
            serverPlayer.teleportTo(targetPosition.x, targetPosition.y, targetPosition.z);
            serverPlayer.resetFallDistance();
        }
    }

    private static final class Client {
        private static final Minecraft mc = Minecraft.getInstance();
        private static final ClientLevel level = mc.level;
        private static final LocalPlayer localPlayer = mc.player;

        private static void start() {
            if (ClientUtil.isScreenNull()) {
                AcademyCraftRenderSystem.RENDERER_MAP.put(NAME, Client.RENDERER);
            }
        }

        private static void end() {
            if (ClientUtil.isScreenNull()) {
                AcademyCraftNetworkSystemClient.sendPacket(new ClientToServerPacket(AcademyCraftNetworkResourceLocations.C2S_SELF_TELEPORT_PACKET, new FriendlyByteBuf(Unpooled.buffer())));
            }
            AcademyCraftRenderSystem.RENDERER_MAP.remove(NAME);
        }

        private static final AcademyCraftRenderSystem.Renderer RENDERER = (poseStack, f, l, bl, camera, gameRenderer, lightTexture, matrix4f, ci) -> {
            if (level != null && localPlayer != null) {
                poseStack.pushPose();
                final AABB aabb = new AABB(0, 0, 0, 1, 2, 1);

                final Vec3 lookVec = localPlayer.getLookAngle();
                final Vec3 offsetVec = lookVec.scale(DISTANCE);

                RenderUtil.applyCameraOffset(poseStack, Minecraft.getInstance().options.getCameraType(), lookVec);
                RenderUtil.applyOffset(poseStack, offsetVec);

                final RenderBuffers renderBuffers = mc.renderBuffers();

                poseStack.translate(-0.5f, -1f, -0.5f);
                RenderUtil.BoxRenderer.renderWireframeBox(poseStack, renderBuffers.bufferSource(), aabb, 1f, 1f, 1f, 1f);
                poseStack.popPose();
            }
        };
    }
}