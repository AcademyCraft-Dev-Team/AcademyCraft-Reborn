package org.academy.mixin.client;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Camera;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.LightTexture;
import org.academy.AcademyCraft;
import org.academy.api.client.render.LevelRenderEvent;
import org.academy.api.client.render.MatrixStack;
import org.academy.api.client.render.post.BloomEffect;
import org.academy.api.client.util.RenderUtil;
import org.joml.Matrix4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LevelRenderer.class)
public abstract class MixinLevelRenderer {
    @Inject(method = "renderLevel", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/MultiBufferSource$BufferSource;endLastBatch()V", shift = At.Shift.AFTER, ordinal = 0))
    private void onPostRenderEntities(PoseStack poseStack, float partialTick, long finishNanoTime, boolean renderBlockOutline, Camera camera, GameRenderer gameRenderer, LightTexture lightTexture, Matrix4f projectionMatrix, CallbackInfo ci) {
        var stack = new MatrixStack();
        stack.setFrom(poseStack.last());
        var event = new LevelRenderEvent(partialTick, stack);
        AcademyCraft.EVENT_BUS.post(event);
    }

    @Inject(method = "renderLevel", at = @At(value = "RETURN"))
    private void onCheckPoseStack(PoseStack poseStack, float partialTick, long finishNanoTime, boolean renderBlockOutline, Camera camera, GameRenderer gameRenderer, LightTexture lightTexture, Matrix4f projectionMatrix, CallbackInfo ci) {
        if (!RenderUtil.IS_SHADER_PACK_IN_USE.get()) {
            BloomEffect.process();
        }
    }
}