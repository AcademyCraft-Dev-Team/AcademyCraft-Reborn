package org.academy.internal.common.ability.builtin.teleporter.skills;

import com.mojang.blaze3d.vertex.VertexConsumer;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.RenderBuffers;
import org.academy.AcademyCraft;
import org.academy.api.client.input.InputSystem;
import org.academy.api.client.render.AcademyCraftRenderSystem;
import org.academy.api.client.util.RenderUtil;
import org.academy.api.common.ability.Skill;
import org.lwjgl.glfw.GLFW;

import java.util.List;

public final class SelfTeleport extends Skill {
    public static final Skill INSTANCE = new SelfTeleport();

    private SelfTeleport() {
        super("self_teleport", 1);
    }

    @Override
    public void initClient() {
        Runnable start = new Runnable() {
            @Override
            public void run() {
                AcademyCraft.LOGGER.info("SelfTeleport started");
                AcademyCraftRenderSystem.RENDERER_LIST.add(Client.RENDERER);
            }
        };
        Runnable stop = new Runnable() {
            @Override
            public void run() {
                AcademyCraft.LOGGER.info("SelfTeleport stopped");
                AcademyCraftRenderSystem.RENDERER_LIST.remove(Client.RENDERER);
            }
        };
        InputSystem.KEY_PRESS_MAP.put(List.of(GLFW.GLFW_KEY_E), start);
        InputSystem.KEY_RELEASE_MAP.put(List.of(GLFW.GLFW_KEY_E), stop);
    }

    @Environment(EnvType.CLIENT)
    private static final class Client {
        public static final Minecraft mc = Minecraft.getInstance();
        public static final AcademyCraftRenderSystem.Renderer RENDERER = (poseStack, f, l, bl, camera, gameRenderer, lightTexture, matrix4f, ci) -> {
            AcademyCraft.LOGGER.info("SelfTeleport client");
            poseStack.pushPose();

            final float RAY_DISTANCE = 10f;

            RenderUtil.translateToForward(poseStack, mc.player, RAY_DISTANCE);

            final RenderBuffers renderBuffers = mc.renderBuffers();
            final VertexConsumer buffer = renderBuffers.bufferSource().getBuffer(RenderUtil.GLOWING_CYLINDER);

            RenderUtil.RayRenderer.renderRay(
                    poseStack,
                    buffer,
                    1f, 0.5f, 0,    // 颜色参数（R, G, B）
                    1f,             // 透明度
                    0, 50,          // 高度范围
                    0.125f,         // 半径
                    32               // 分段精度
            );

            poseStack.popPose();
        };
    }
}