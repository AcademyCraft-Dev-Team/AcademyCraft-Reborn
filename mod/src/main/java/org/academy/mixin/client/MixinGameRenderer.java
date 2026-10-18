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
import org.academy.api.client.render.post.DirectionalFieldRenderer;
import org.academy.api.client.render.post.GlowEffect;
import org.academy.api.client.render.post.PostEffect;
import org.academy.api.client.render.post.WorldSurfaceMasks;
import org.academy.api.client.render.vfxgraph.render.GraphCamera;
import org.academy.api.client.render.vfxgraph.runtime.VfxGraphManager;
import org.academy.api.client.vanilla.RenderLoopEvent;
import org.academy.api.client.vanilla.WorldCompositeEvent;
import org.academy.internal.client.ability.mentalout.WideAreaInterferenceClientState;
import org.academy.internal.client.hud.HudManager;
import org.academy.internal.client.render.vfxgraph.FrameProjectionContext;
import org.academy.internal.client.render.vfxgraph.WorldLineOverlayPass;
import org.academy.internal.common.ability.accelerator.Accelerator;
import org.academy.internal.common.ability.darkmatter.Darkmatter;
import org.academy.internal.common.ability.teleport.skills.lv5.SpacialExcision;
import org.joml.Matrix4f;
import org.joml.Vector3f;
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
    private void renderLevel(
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
        try (var ignored = FrameProjectionContext.push(projectionMatrix)) {
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
    private FogData extractCamera(
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
    private void render(CallbackInfo ci) {
        AnimationManager.INSTANCE.onFrameUpdate();
        UiFrame.INSTANCE.onFrame();
        NeoForge.EVENT_BUS.post(new RenderLoopEvent());
    }

    @Inject(method = "renderLevel", at = @At("HEAD"))
    private void renderLevelBefore(CallbackInfo ci) {
        assert minecraft.level != null;
        WorldLineOverlayPass.beginFrame(minecraft.level);
        Accelerator.Client.WingAvatarRegistry.beginFrame();
    }

    @Inject(
            method = "renderLevel",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/renderer/GameRenderer;render3dHud(Lnet/minecraft/client/renderer/state/level/CameraRenderState;Lnet/minecraft/client/renderer/state/level/PlayerRenderState;Lnet/minecraft/client/renderer/state/OptionsRenderState;Z)V"
            )
    )
    private void renderLevelAfter(CallbackInfo ci, @Local(name = "cameraState") CameraRenderState cameraState) {
        WideAreaInterferenceClientState.captureRenderCamera(
                cameraState.pos,
                cameraState.viewRotationMatrix,
                cameraState.projectionMatrix
        );
        var mainTarget = Minecraft.getInstance().gameRenderer.mainRenderTarget();
        var target = mainTarget.getColorTextureView();
        if (target != null) {
            var graphCamera = GraphCamera.fromGameCamera(
                    new Vector3f((float) cameraState.pos.x, (float) cameraState.pos.y, (float) cameraState.pos.z),
                    new Matrix4f(cameraState.viewRotationMatrix),
                    new Matrix4f(cameraState.projectionMatrix)
            );
            VfxGraphManager.INSTANCE.renderFrame(target, mainTarget.getDepthTextureView(), graphCamera);
            VfxGraphManager.INSTANCE.renderScreenFlashFrame(target);
        }
        SpacialExcision.Client.Vfx.prepareSourceValidation();
        PostEffect.pre();
        if (target != null) {
            VfxGraphManager.INSTANCE.renderDistortionFrame(target);
        }
        SpacialExcision.Client.Vfx.renderPost();
        WorldSurfaceMasks.captureMasks();
        DirectionalFieldRenderer.render(cameraState.pos,
                cameraState.viewRotationMatrix, cameraState.projectionMatrix,
                Darkmatter.Client.InterferenceFieldClient.areas());
        GlowEffect.getInstance().process();
        PostEffect.post();
    }

    @Inject(
            method = "render",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/render/GuiRenderer;render()V")
    )
    private void endFrame(CallbackInfo ci) {
        if (minecraft.gameRenderer.gameRenderState().shouldRenderLevel) HudManager.INSTANCE.render();
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
