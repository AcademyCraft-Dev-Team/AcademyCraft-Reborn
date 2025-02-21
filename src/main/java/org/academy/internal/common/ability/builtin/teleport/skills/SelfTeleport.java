package org.academy.internal.common.ability.builtin.teleport.skills;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.RenderBuffers;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.academy.api.client.input.InputSystem;
import org.academy.api.client.network.NetworkSystemClient;
import org.academy.api.client.network.packet.C2SRequestPacket;
import org.academy.api.client.render.AcademyCraftRenderSystem;
import org.academy.api.client.util.RenderUtil;
import org.academy.api.common.ability.Skill;
import org.academy.api.common.network.AcademyCraftNetworkResourceLocations;
import org.academy.api.server.network.AcademyCraftServerRequestHandlers;
import org.academy.api.server.network.ServerRequestHandler;
import org.lwjgl.glfw.GLFW;

import java.util.List;

public final class SelfTeleport extends Skill {
    public static final Skill INSTANCE = new SelfTeleport();

    private SelfTeleport() {
        super("self_teleport", 2);
    }

    @Override
    public void initClient() {
        Runnable start = new Runnable() {
            @Override
            public void run() {
                AcademyCraftRenderSystem.RENDERER_LIST.add(Client.RENDERER);
            }
        };
        Runnable stop = new Runnable() {
            @Override
            public void run() {
                AcademyCraftRenderSystem.RENDERER_LIST.remove(Client.RENDERER);
                NetworkSystemClient.sendPacket(new C2SRequestPacket(AcademyCraftNetworkResourceLocations.C2S_SELF_TELEPORT_REQUEST));
            }
        };
        InputSystem.KEY_PRESS_MAP.put(List.of(GLFW.GLFW_KEY_E), start);
        InputSystem.KEY_RELEASE_MAP.put(List.of(GLFW.GLFW_KEY_E), stop);
    }

    @Override
    public void initServer(MinecraftServer server) {
        AcademyCraftServerRequestHandlers.REQUEST_HANDLER_MAP.put(AcademyCraftNetworkResourceLocations.C2S_SELF_TELEPORT_REQUEST, new ServerRequestHandler() {
            @Override
            public void handle(ServerGamePacketListenerImpl serverGamePacketListenerImpl) {
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

            final float RAY_DISTANCE = 10f;

            // 准心前方 10 格
            RenderUtil.translateToForward(poseStack, mc.player, RAY_DISTANCE);

            final RenderBuffers renderBuffers = mc.renderBuffers();

            // 到方框的中心
            poseStack.translate(-0.5f, -1f, -0.5f);
            // glPolygonMode(GL_FRONT_AND_BACK, GL_LINE);
            RenderUtil.BoxRenderer.renderWireframeBox(poseStack, renderBuffers.bufferSource(), new AABB(0, 0, 0, 1, 2, 1), 1f, 1f, 1f, 1f);
            //glPolygonMode(GL_FRONT_AND_BACK, GL_FILL);
            poseStack.popPose();
        };
    }
}