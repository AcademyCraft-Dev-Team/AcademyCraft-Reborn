package org.academy.internal.common.ability.builtin.teleport.skills;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.RenderBuffers;
import net.minecraft.network.protocol.game.ServerboundCustomPayloadPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.academy.AcademyCraftClient;
import org.academy.api.client.input.InputSystem;
import org.academy.api.client.network.AcademyCraftNetworkSystemClient;
import org.academy.api.client.network.packet.C2SRequestPacket;
import org.academy.api.client.render.AcademyCraftRenderSystem;
import org.academy.api.client.util.ClientUtil;
import org.academy.api.client.util.RenderUtil;
import org.academy.api.common.ability.Skill;
import org.academy.api.common.network.AcademyCraftNetworkResourceLocations;
import org.academy.api.server.network.AcademyCraftRequestHandlerServer;
import org.academy.api.server.network.AcademyCraftRequestHandlersServer;
import org.lwjgl.glfw.GLFW;

import java.util.List;
import java.util.function.Supplier;

public final class SelfTeleport extends Skill {
    public static final Skill INSTANCE = new SelfTeleport();
    public static final String KEY_NAME_START = "self_teleport.start";
    public static final String KEY_NAME_END = "self_teleport.end";
    public static final Supplier<List<Integer>> KEY_START = () -> AcademyCraftClient.clientConfig.getKey(KEY_NAME_START, List.of(GLFW.GLFW_KEY_E));
    public static final Supplier<List<Integer>> KEY_END = () -> AcademyCraftClient.clientConfig.getKey(KEY_NAME_END, List.of(GLFW.GLFW_KEY_E));

    private SelfTeleport() {
        super("self_teleport", 2);
    }

    @Override
    public void initClient() {
        Runnable start = new Runnable() {
            @Override
            public void run() {
                if (!ClientUtil.hasScreen()) {
                    AcademyCraftRenderSystem.RENDERER_LIST.add(Client.RENDERER);
                }
            }
        };
        Runnable end = new Runnable() {
            @Override
            public void run() {
                if (!ClientUtil.hasScreen()) {
                    AcademyCraftNetworkSystemClient.sendPacket(new C2SRequestPacket(AcademyCraftNetworkResourceLocations.C2S_SELF_TELEPORT_REQUEST));
                }
                AcademyCraftRenderSystem.RENDERER_LIST.remove(Client.RENDERER);
            }
        };
        InputSystem.KEY_PRESS_MAP.put(KEY_NAME_START, new InputSystem.KeyBinding(KEY_START, start));
        InputSystem.KEY_RELEASE_MAP.put(KEY_NAME_END, new InputSystem.KeyBinding(KEY_END, end));
    }

    @Override
    public void initServer(MinecraftServer server) {
        AcademyCraftRequestHandlersServer.REQUEST_HANDLER_MAP.put(AcademyCraftNetworkResourceLocations.C2S_SELF_TELEPORT_REQUEST, new AcademyCraftRequestHandlerServer() {
            @Override
            public void handle(ServerGamePacketListenerImpl serverGamePacketListenerImpl, ServerboundCustomPayloadPacket packet) {
                ServerPlayer serverPlayer = serverGamePacketListenerImpl.player;
                Vec3 lookDirection = serverPlayer.getLookAngle();
                Vec3 targetPosition = serverPlayer.position().add(lookDirection.scale(10));
                serverPlayer.teleportTo(targetPosition.x, targetPosition.y, targetPosition.z);
                serverPlayer.resetFallDistance();
            }
        });
    }

    @Environment(EnvType.CLIENT)
    private static final class Client {
        public static final Minecraft mc = Minecraft.getInstance();
        public static final AcademyCraftRenderSystem.Renderer RENDERER = (poseStack, f, l, bl, camera, gameRenderer, lightTexture, matrix4f, ci) -> {
            poseStack.pushPose();

            final float DISTANCE = 10f;

            RenderUtil.translateToForward(poseStack, mc.player, DISTANCE);

            final RenderBuffers renderBuffers = mc.renderBuffers();

            poseStack.translate(-0.5f, -1f, -0.5f);
            RenderUtil.BoxRenderer.renderWireframeBox(poseStack, renderBuffers.bufferSource(), new AABB(0, 0, 0, 1, 2, 1), 1f, 1f, 1f, 1f);
            poseStack.popPose();
        };
    }
}