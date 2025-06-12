package org.academy.mixin.client;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Camera;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.LightTexture;
import org.academy.AcademyCraft;
import org.academy.api.client.renderer.CameraRenderEvent;
import org.academy.api.client.renderer.RendererManager;
import org.joml.Matrix4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LevelRenderer.class)
public abstract class MixinLevelRenderer {
    @Inject(method = {"renderLevel"}, at = {@At(value = "CONSTANT", args = {"stringValue=entities"}, ordinal = 0)})
    private void afterEntities(PoseStack poseStack, float partialTick, long finishNanoTime, boolean renderBlockOutline, Camera camera, GameRenderer gameRenderer, LightTexture lightTexture, Matrix4f projectionMatrix, CallbackInfo ci) {
        CameraRenderEvent event = new CameraRenderEvent(poseStack, partialTick, finishNanoTime, renderBlockOutline, camera, gameRenderer, lightTexture, projectionMatrix);
        AcademyCraft.EVENT_BUS.post(event);
        if (event.isCanceled()) return;
        poseStack = event.poseStack;
        partialTick = event.partialTick;
        finishNanoTime = event.finishNanoTime;
        renderBlockOutline = event.renderBlockOutline;
        camera = event.camera;
        gameRenderer = event.gameRenderer;
        lightTexture = event.lightTexture;
        projectionMatrix = event.projectionMatrix;
        RendererManager.renderCamera(poseStack, partialTick, finishNanoTime, renderBlockOutline, camera, gameRenderer, lightTexture, projectionMatrix);
    }
}