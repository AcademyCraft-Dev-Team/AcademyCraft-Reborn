package org.academy.mixin.client;

import net.minecraft.client.Camera;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.LightTexture;
import net.neoforged.neoforge.common.NeoForge;
import org.academy.api.client.render.LevelRenderEvent;
import org.academy.api.client.render.MatrixStack;
import org.academy.api.client.render.post.BloomEffect;
import org.joml.Matrix4f;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LevelRenderer.class)
public abstract class MixinLevelRenderer {
    @Shadow
    @Final
    private Minecraft minecraft;

    @Inject(method = "renderLevel", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/MultiBufferSource$BufferSource;endLastBatch()V", shift = At.Shift.AFTER, ordinal = 0))
    private void onPostRenderEntities(DeltaTracker deltaTracker, boolean renderBlockOutline, Camera camera, GameRenderer gameRenderer, LightTexture lightTexture, Matrix4f frustumMatrix, Matrix4f projectionMatrix, CallbackInfo ci) {
        var stack = new MatrixStack();
        var event = new LevelRenderEvent(deltaTracker.getGameTimeDeltaPartialTick(true), stack);
        NeoForge.EVENT_BUS.post(event);
    }

    @Inject(method = "renderLevel", at = @At(value = "INVOKE", target = "Lorg/joml/Matrix4fStack;popMatrix()Lorg/joml/Matrix4fStack;"))
    private void onCheckPoseStack(DeltaTracker deltaTracker, boolean renderBlockOutline, Camera camera, GameRenderer gameRenderer, LightTexture lightTexture, Matrix4f frustumMatrix, Matrix4f projectionMatrix, CallbackInfo ci) {
        BloomEffect.process();
    }

    @Inject(method = "graphicsChanged", at = @At("TAIL"))
    private void onGraphicsChanged(CallbackInfo ci) {
        var main = minecraft.getMainRenderTarget();
        BloomEffect.resize(main.width, main.height);
    }
}