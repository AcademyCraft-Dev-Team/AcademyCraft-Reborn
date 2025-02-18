package org.academy.api.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.RenderBuffers;
import org.academy.AbilitySystemClient;
import org.academy.api.client.util.RenderUtil;
import org.joml.Matrix4f;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.ArrayList;
import java.util.List;

public class RenderSystem {
    /**
     * 直接渲染是在摄像机的位置渲染，请注意
     */
    public static final List<AbilitySystemClient.Renderer> RENDERER_LIST = new ArrayList<>();

    static {
        // test
        RenderSystem.RENDERER_LIST.add(new AbilitySystemClient.Renderer() {
            @Override
            public void render(PoseStack poseStack, float f, long l, boolean bl, Camera camera, GameRenderer gameRenderer, LightTexture lightTexture, Matrix4f matrix4f, CallbackInfo ci) {
                Minecraft mc = Minecraft.getInstance();
                LocalPlayer player = mc.player;

                if (player == null) return;

                poseStack.pushPose();

                final float RAY_DISTANCE = 10f;

                RenderUtil.translateToForward(poseStack, player, RAY_DISTANCE);

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
            }
        });
    }
}