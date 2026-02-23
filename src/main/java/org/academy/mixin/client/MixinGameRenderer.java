package org.academy.mixin.client;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.SubmitNodeStorage;
import net.minecraft.client.renderer.state.CameraRenderState;
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
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/GameRenderer;bobHurt(Lnet/minecraft/client/renderer/state/CameraRenderState;Lcom/mojang/blaze3d/vertex/PoseStack;)V"),
            locals = LocalCapture.CAPTURE_FAILSOFT
    )
    private void onRenderItemInHand(
            CameraRenderState cameraState,
            float deltaPartialTick,
            Matrix4f modelViewMatrix,
            CallbackInfo ci,
            PoseStack poseStack,
            Matrix4fStack modelViewStack
    ) {
        var player = minecraft.player;
        if (player != null
                && this.minecraft.options.getCameraType().isFirstPerson()
                && !cameraState.entityRenderState.isSleeping
                && !this.minecraft.options.hideGui
                && minecraft.gameMode != null
                && this.minecraft.gameMode.getPlayerMode() != GameType.SPECTATOR
        ) {
            RendererManager.renderEffectFirstPerson(
                    poseStack,
                    getSubmitNodeStorage(),
                    player,
                    minecraft.getEntityRenderDispatcher().getPackedLightCoords(
                            player, deltaPartialTick
                    ),
                    deltaPartialTick
            );
        }
    }
}