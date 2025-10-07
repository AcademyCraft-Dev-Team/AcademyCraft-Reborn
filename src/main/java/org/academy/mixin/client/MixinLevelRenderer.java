package org.academy.mixin.client;

import com.mojang.blaze3d.buffers.GpuBufferSlice;
import com.mojang.blaze3d.resource.GraphicsResourceAllocator;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Camera;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.state.LevelRenderState;
import net.neoforged.neoforge.common.NeoForge;
import org.academy.api.client.render.LevelRenderEvent;
import org.academy.api.client.render.MatrixStack;
import org.academy.api.client.render.post.BloomEffect;
import org.academy.api.client.render.post.PostEffect;
import org.joml.Matrix4f;
import org.joml.Vector4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LevelRenderer.class)
public abstract class MixinLevelRenderer {
    @Inject(method = "renderLevel", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/LevelTargetBundle;clear()V"))
    private void renderLevel(GraphicsResourceAllocator graphicsResourceAllocator, DeltaTracker deltaTracker, boolean renderBlockOutline, Camera camera, Matrix4f frustumMatrix, Matrix4f projectionMatrix, Matrix4f cullingProjectionMatrix, GpuBufferSlice shaderFog, Vector4f fogColor, boolean renderSky, CallbackInfo ci) {
        PostEffect.pre();
        BloomEffect.process();
        PostEffect.post();
    }

    @Inject(method = "submitEntities", at = @At("HEAD"))
    private void submitEntities(PoseStack poseStack, LevelRenderState renderState, SubmitNodeCollector nodeCollector, CallbackInfo ci) {
        var event = new LevelRenderEvent(Minecraft.getInstance().getDeltaTracker().getGameTimeDeltaTicks(), new MatrixStack().setFrom(poseStack.last()));
        NeoForge.EVENT_BUS.post(event);
    }
}