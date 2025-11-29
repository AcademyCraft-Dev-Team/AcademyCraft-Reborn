package org.academy.mixin.client;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.SubmitNodeStorage;
import net.minecraft.world.level.GameType;
import net.neoforged.neoforge.common.NeoForge;
import org.academy.api.client.Render;
import org.academy.api.client.gui.animation.AnimationManager;
import org.academy.api.client.renderer.RendererManager;
import org.academy.api.client.vanilla.RenderLoopEvent;
import org.academy.api.client.vanilla.ResizeDisplayEvent;
import org.joml.Matrix4f;
import org.joml.Matrix4fStack;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.LocalCapture;


@Mixin(GameRenderer.class)
public abstract class MixinGameRenderer {
    @Shadow
    @Final
    private Minecraft minecraft;

    @Shadow
    public abstract SubmitNodeStorage getSubmitNodeStorage();

    /**
     * For ResizeDisplayEvent
     */
    @Inject(method = "resize",at = @At("TAIL"))
    private void resize(int width, int height, CallbackInfo ci) {
        var event = new ResizeDisplayEvent(width, height);
        NeoForge.EVENT_BUS.post(event);
    }

    @Inject(method = "render",at = @At("HEAD"))
    private void onFrameUpdate(CallbackInfo ci) {
        AnimationManager.onFrameUpdate();
        NeoForge.EVENT_BUS.post(new RenderLoopEvent());
    }

    @Inject(method = "render", at = @At("TAIL"))
    private void onRender(CallbackInfo ci) {
        if (!minecraft.noRender) {
            Render.Buffers.getResourcePool().endFrame();
        }
    }

    @Inject(method = "close",at = @At("HEAD"))
    private void onClose(CallbackInfo ci) {
        Render.Buffers.getResourcePool().close();
    }

    @Inject(
            method = "renderItemInHand",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/GameRenderer;bobHurt(Lcom/mojang/blaze3d/vertex/PoseStack;F)V"),
            locals = LocalCapture.CAPTURE_FAILSOFT
    )
    private void onRenderItemInHand(
            float partialTick,
            boolean sleeping,
            Matrix4f projectionMatrix,
            CallbackInfo ci,
            PoseStack poseStack,
            Matrix4fStack matrix4fStack) {
        var player = minecraft.player;
        if (minecraft.options.getCameraType().isFirstPerson()
                && !sleeping
                && !minecraft.options.hideGui
                && player != null
                && minecraft.gameMode != null
                && minecraft.gameMode.getPlayerMode() != GameType.SPECTATOR
        ) {
            RendererManager.renderEffectFirstPerson(
                    poseStack,
                    getSubmitNodeStorage(),
                    player,
                    minecraft.getEntityRenderDispatcher().getPackedLightCoords(player, partialTick),
                    partialTick
            );
        }
    }
}