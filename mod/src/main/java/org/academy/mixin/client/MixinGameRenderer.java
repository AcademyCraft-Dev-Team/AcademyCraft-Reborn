package org.academy.mixin.client;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import com.mojang.blaze3d.resource.GraphicsResourceAllocator;
import com.mojang.renderpearl.api.buffers.GpuBufferSlice;
import net.minecraft.client.Camera;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.feature.FeatureRenderDispatcher;
import net.minecraft.client.renderer.fog.FogData;
import net.minecraft.client.renderer.fog.FogRenderer;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.material.FogType;
import net.neoforged.neoforge.common.NeoForge;
import org.academy.api.client.gui.animation.AnimationManager;
import org.academy.api.client.gui.frame.UiFrame;
import org.academy.api.client.render.Render;
import org.academy.api.client.vanilla.RenderLoopEvent;
import org.academy.api.client.vanilla.WorldCompositeEvent;
import org.academy.internal.client.ability.mentalout.WideAreaInterferenceClientState;
import org.academy.internal.client.hud.HudManager;
import org.academy.internal.client.render.vfx.SpatialCutFrameProjectionContext;
import org.academy.internal.client.render.vfx.WingAvatarRegistry;
import org.academy.internal.client.render.vfx.WorldLineOverlayPass;
import org.joml.Matrix4f;
import org.joml.Vector4f;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;


@Mixin(GameRenderer.class)
public abstract class MixinGameRenderer {
    @Shadow
    @Final
    private Minecraft minecraft;
    @Shadow
    @Final
    private FeatureRenderDispatcher featureRenderDispatcher;

    @WrapOperation(
            method = "renderLevel",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/renderer/LevelRenderer;render(Lcom/mojang/blaze3d/resource/GraphicsResourceAllocator;ZLnet/minecraft/client/renderer/state/level/CameraRenderState;Lcom/mojang/renderpearl/api/buffers/GpuBufferSlice;Lorg/joml/Vector4f;ZZ)V"
            )
    )
    private void academy$captureSpatialCutFrameProjection(
            LevelRenderer renderer,
            GraphicsResourceAllocator resourceAllocator,
            boolean renderOutline,
            CameraRenderState cameraState,
            GpuBufferSlice terrainFog,
            Vector4f fogColor,
            boolean shouldRenderSky,
            boolean consistentDepthRequired,
            Operation<Void> original,
            @Local(name = "projectionMatrix") Matrix4f projectionMatrix
    ) {
        try (var ignored = SpatialCutFrameProjectionContext.push(projectionMatrix)) {
            original.call(renderer, resourceAllocator, renderOutline, cameraState,
                    terrainFog, fogColor, shouldRenderSky, consistentDepthRequired);
            WorldLineOverlayPass.renderWorld(
                    featureRenderDispatcher,
                    cameraState.viewRotationMatrix
            );
        }
    }

    @WrapOperation(method = "extractCamera", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/renderer/fog/FogRenderer;setupFog(Lnet/minecraft/client/Camera;ILnet/minecraft/client/DeltaTracker;FLnet/minecraft/client/multiplayer/ClientLevel;)Lnet/minecraft/client/renderer/fog/FogData;"))
    private FogData academy$extendRtsDistanceFog(
            FogRenderer renderer, Camera camera,
            int renderDistanceInChunks, DeltaTracker deltaTracker, float darkenWorldAmount, ClientLevel level,
            Operation<FogData> original) {
        var fog = original.call(renderer, camera, renderDistanceInChunks, deltaTracker, darkenWorldAmount, level);
        if (WideAreaInterferenceClientState.isRtsView()
                && camera.getFluidInCamera() == FogType.ATMOSPHERIC
                && !(camera.entity() instanceof LivingEntity living
                && (living.hasEffect(MobEffects.BLINDNESS)
                || living.hasEffect(MobEffects.DARKNESS)))) {
            var distance = (float) camera.position().distanceTo(
                    WideAreaInterferenceClientState.focusPosition());
            fog.renderDistanceStart += distance;
            fog.renderDistanceEnd += distance;
            fog.environmentalStart += distance;
            fog.environmentalEnd += distance;
            fog.skyEnd += distance;
            fog.cloudEnd += distance;
        }
        return fog;
    }

    @Inject(method = "render", at = @At("HEAD"))
    private void onFrameUpdate(CallbackInfo ci) {
        AnimationManager.INSTANCE.onFrameUpdate();
        UiFrame.INSTANCE.onFrame();
        NeoForge.EVENT_BUS.post(new RenderLoopEvent());
    }

    @Inject(method = "renderLevel", at = @At("HEAD"))
    private void academy$beginPlatinumCosmosFrame(CallbackInfo ci) {
        assert minecraft.level != null;
        WorldLineOverlayPass.beginFrame(minecraft.level);
        WingAvatarRegistry.beginFrame();
    }

    @Inject(
            method = "render",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/render/GuiRenderer;render()V")
    )
    private void beforeGuiRender(CallbackInfo ci) {
        if (minecraft.gameRenderer.gameRenderState().shouldRenderLevel) HudManager.INSTANCE.render();
    }

    @Inject(
            method = "render",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/render/GuiRenderer;render()V", shift = At.Shift.AFTER)
    )
    private void afterGuiRender(CallbackInfo ci) {
        NeoForge.EVENT_BUS.post(new WorldCompositeEvent());
    }

    @Inject(method = "render", at = @At(value = "INVOKE", target = "Lcom/mojang/blaze3d/resource/CrossFrameResourcePool;endFrame()V"))
    private void onRender(CallbackInfo ci) {
        Render.Buffers.getResourcePool().endFrame();
    }

    @Inject(method = "close", at = @At("HEAD"))
    private void onClose(CallbackInfo ci) {
        Render.Buffers.getResourcePool().close();
    }
}
