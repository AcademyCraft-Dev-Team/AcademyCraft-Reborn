package org.academy.internal.common.ability.builtin.teleporter.skills;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.RenderBuffers;
import net.minecraft.world.phys.AABB;
import org.academy.api.client.input.InputSystem;
import org.academy.api.client.render.AcademyCraftRenderSystem;
import org.academy.api.client.util.RenderUtil;
import org.academy.api.common.ability.Skill;
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
            }
        };
        InputSystem.KEY_PRESS_MAP.put(List.of(GLFW.GLFW_KEY_E), start);
        InputSystem.KEY_RELEASE_MAP.put(List.of(GLFW.GLFW_KEY_E), stop);
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