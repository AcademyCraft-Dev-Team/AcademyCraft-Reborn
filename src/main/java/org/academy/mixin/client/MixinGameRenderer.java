package org.academy.mixin.client;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.SubmitNodeStorage;
import net.minecraft.client.renderer.feature.FeatureRenderDispatcher;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.world.level.GameType;
import net.neoforged.neoforge.common.NeoForge;
import org.academy.api.client.hud.HudManager;
import org.academy.api.client.render.Render;
import org.academy.api.client.renderer.RendererManager;
import org.academy.api.client.vanilla.RenderLoopEvent;
import org.academy.internal.client.renderer.effect.PlatinumCosmosPass;
import org.academy.internal.client.renderer.effect.WorldLineOverlayPass;
import org.academy.internal.client.ability.mentalout.MentalIntrusionClientState;
import org.joml.Matrix4fStack;
import org.joml.Matrix4fc;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
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
    @Final
    private SubmitNodeStorage handAndScreenSubmitNodeStorage;

    @Shadow
    @Final
    private FeatureRenderDispatcher featureRenderDispatcher;

    @Unique
    private final SubmitNodeStorage academy$hiddenHudEffectSubmitNodeStorage = new SubmitNodeStorage();

    @Inject(method = "render", at = @At("HEAD"))
    private void onFrameUpdate(CallbackInfo ci) {
        NeoForge.EVENT_BUS.post(new RenderLoopEvent());
    }

    @Inject(method = "renderLevel", at = @At("HEAD"))
    private void academy$beginPlatinumCosmosFrame(DeltaTracker deltaTracker, CallbackInfo ci) {
        PlatinumCosmosPass.beginFrame(minecraft.level);
        WorldLineOverlayPass.beginFrame(minecraft.level);
    }

    @Inject(
            method = "renderLevel",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/renderer/LevelRenderer;render(Lcom/mojang/blaze3d/resource/GraphicsResourceAllocator;Lnet/minecraft/client/DeltaTracker;ZLnet/minecraft/client/renderer/state/level/CameraRenderState;Lorg/joml/Matrix4fc;Lcom/mojang/blaze3d/buffers/GpuBufferSlice;Lorg/joml/Vector4f;Z)V",
                    shift = At.Shift.AFTER
            )
    )
    private void academy$renderPlatinumCosmosAfterWorld(
            DeltaTracker deltaTracker, CallbackInfo ci
    ) {
        var cameraState = minecraft.gameRenderer.gameRenderState()
                .levelRenderState.cameraRenderState;
        PlatinumCosmosPass.renderWorld(
                featureRenderDispatcher,
                cameraState.viewRotationMatrix
        );
        WorldLineOverlayPass.renderWorld(
                featureRenderDispatcher,
                cameraState.viewRotationMatrix
        );
    }

    @Inject(
            method = "render",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/render/GuiRenderer;render()V")
    )
    private void render(DeltaTracker deltaTracker, boolean advanceGameTime, CallbackInfo ci) {
        var resourcesLoaded = minecraft.isGameLoadFinished();
        var shouldRenderLevel = resourcesLoaded && advanceGameTime && minecraft.level != null;
        if (shouldRenderLevel) HudManager.INSTANCE.render();
    }

    @Inject(method = "render", at = @At(value = "INVOKE", target = "Lcom/mojang/blaze3d/resource/CrossFrameResourcePool;endFrame()V"))
    private void onRender(CallbackInfo ci) {
        Render.Buffers.getResourcePool().endFrame();
    }

    @Inject(method = "close", at = @At("HEAD"))
    private void onClose(CallbackInfo ci) {
        Render.Buffers.getResourcePool().close();
    }

    @Inject(
            method = "renderItemInHand",
            at = @At("HEAD"),
            cancellable = true
    )
    private void academy$hideMentalIntrusionHands(
            CameraRenderState cameraState,
            float deltaPartialTick,
            Matrix4fc modelViewMatrix,
            CallbackInfo ci
    ) {
        if (MentalIntrusionClientState.isActive()) ci.cancel();
    }

    @Inject(
            method = "renderItemInHand",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/renderer/ItemInHandRenderer;submitHandsWithItems(FLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;Lnet/minecraft/client/player/LocalPlayer;I)V"
            ),
            locals = LocalCapture.CAPTURE_FAILSOFT
    )
    private void onRenderItemInHand(
            CameraRenderState cameraState,
            float deltaPartialTick,
            Matrix4fc modelViewMatrix,
            CallbackInfo ci,
            PoseStack poseStack,
            Matrix4fStack modelViewStack
    ) {
        var player = minecraft.player;
        if (player == null) return;
        RendererManager.renderEffectFirstPerson(
                poseStack,
                handAndScreenSubmitNodeStorage,
                player,
                minecraft.getEntityRenderDispatcher().getPackedLightCoords(
                        player, deltaPartialTick
                ),
                deltaPartialTick
        );
    }

    @Inject(
            method = "renderLevel",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/renderer/GameRenderer;renderItemInHand(Lnet/minecraft/client/renderer/state/level/CameraRenderState;FLorg/joml/Matrix4fc;)V",
                    shift = At.Shift.AFTER
            )
    )
    private void renderFirstPersonEffectsWithHiddenHud(DeltaTracker deltaTracker, CallbackInfo ci) {
        var player = minecraft.player;
        var gameMode = minecraft.gameMode;
        if (player == null || gameMode == null || gameMode.getPlayerMode() == GameType.SPECTATOR) return;

        var gameRenderState = minecraft.gameRenderer.gameRenderState();
        if (!gameRenderState.guiRenderState.isHudHidden
                || !gameRenderState.optionsRenderState.cameraType.isFirstPerson()) return;

        var cameraState = gameRenderState.levelRenderState.cameraRenderState;
        if (cameraState.isPanoramicMode || cameraState.entityRenderState.isSleeping) return;

        var partialTick = minecraft.gameRenderer.mainCamera().getCameraEntityPartialTicks(deltaTracker);
        RendererManager.renderEffectFirstPersonWithHiddenHud(
                new PoseStack(),
                academy$hiddenHudEffectSubmitNodeStorage,
                player,
                minecraft.getEntityRenderDispatcher().getPackedLightCoords(player, partialTick),
                partialTick
        );
        featureRenderDispatcher.renderAllFeatures(academy$hiddenHudEffectSubmitNodeStorage);
        PlatinumCosmosPass.renderFirstPersonWithHiddenHud(featureRenderDispatcher, partialTick);
    }
}
