package org.academy.mixin.client;

import net.minecraft.client.Camera;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.world.phys.Vec3;
import org.academy.internal.client.ability.mentalout.CameraSmoothingBridge;
import org.academy.internal.client.ability.mentalout.PlayerControlClientState;
import org.academy.internal.client.ability.mentalout.WideAreaInterferenceClientState;
import org.academy.internal.client.ability.teleport.ChunkLeapGodView;
import org.academy.internal.client.animation.AbilityDeveloperSleepClient;
import org.academy.internal.client.render.vfxgraph.CameraShakeManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Camera.class)
public abstract class MixinCamera implements CameraSmoothingBridge {
    @Shadow
    private float depthFar;
    @Shadow
    private float eyeHeight;
    @Shadow
    private float eyeHeightOld;
    @Shadow
    private float fovModifier;
    @Shadow
    private float oldFovModifier;

    @Override
    public CameraSmoothingState academy$captureSmoothingState() {
        return new CameraSmoothingState(eyeHeight, eyeHeightOld, fovModifier, oldFovModifier);
    }

    @Override
    public void academy$restoreSmoothingState(CameraSmoothingState state) {
        eyeHeight = state.eyeHeight();
        eyeHeightOld = state.previousEyeHeight();
        fovModifier = state.fovModifier();
        oldFovModifier = state.previousFovModifier();
    }

    @Inject(
            method = "update",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/Camera;alignWithEntity(F)V",
                    shift = At.Shift.AFTER
            )
    )
    private void academy$applyControlledPlayerView(DeltaTracker deltaTracker, CallbackInfo ci) {
        if (PlayerControlClientState.hasControllerView()) {
            ((Camera) (Object) this).setRotation(
                    PlayerControlClientState.controllerViewYaw(),
                    PlayerControlClientState.controllerViewPitch()
            );
        }
    }

    @Inject(method = "update", at = @At("TAIL"))
    private void academy$applySkyStrikeShake(DeltaTracker deltaTracker, CallbackInfo ci) {
        var offset = CameraShakeManager.sample();
        if (offset.isZero()) return;
        var camera = (Camera) (Object) this;
        ((Camera) (Object) this).setRotation(camera.yRot() + offset.yaw(), camera.xRot() + offset.pitch());
    }

    @Inject(method = "update", at = @At("TAIL"))
    private void academy$alignWithAbilityDeveloperPod(DeltaTracker deltaTracker, CallbackInfo ci) {
        var camera = (Camera) (Object) this;
        if (camera.entity() == null) return;
        var adjustment = AbilityDeveloperSleepClient.cameraAdjustment(
                camera,
                deltaTracker.getGameTimeDeltaPartialTick(false)
        );
        if (adjustment == null) return;
        ((Camera) (Object) this).setPosition(adjustment.position());
        ((Camera) (Object) this).setRotation(camera.yRot(), camera.xRot() + adjustment.pitchOffset());
    }

    @Inject(method = "update", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/Camera;alignWithEntity(F)V", shift = At.Shift.AFTER))
    private void academy$applyWideAreaInterferenceCamera(DeltaTracker deltaTracker, CallbackInfo ci) {
        if (!WideAreaInterferenceClientState.hasCameraOverride()) return;
        if (WideAreaInterferenceClientState.isRtsView()) {
            depthFar = Math.max(depthFar, (float) WideAreaInterferenceClientState.pickDistance());
        }
        ((Camera) (Object) this).setPosition(WideAreaInterferenceClientState.cameraPosition());
        ((Camera) (Object) this).setRotation(
                WideAreaInterferenceClientState.cameraYaw(),
                WideAreaInterferenceClientState.cameraPitch()
        );
    }

    @Inject(method = "update", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/Camera;alignWithEntity(F)V", shift = At.Shift.AFTER))
    private void academy$applyChunkLeapGodView(DeltaTracker deltaTracker, CallbackInfo ci) {
        if (!ChunkLeapGodView.shouldOverrideCamera()) return;
        depthFar = Math.max(depthFar, (float) ChunkLeapGodView.farExtra());
        ((Camera) (Object) this).setPosition(ChunkLeapGodView.cameraPosition());
        ((Camera) (Object) this).setRotation(ChunkLeapGodView.cameraYaw(), ChunkLeapGodView.cameraPitch());
    }

    @Inject(method = "getMaxZoom", at = @At("HEAD"), cancellable = true)
    private void academy$ignoreDetachedViewCollision(
            float cameraDist,
            CallbackInfoReturnable<Float> cir
    ) {
        if (WideAreaInterferenceClientState.hasCameraOverride()
                || ChunkLeapGodView.shouldOverrideCamera()) {
            cir.setReturnValue(cameraDist);
        }
    }

    @Inject(method = "extractRenderState", at = @At("TAIL"))
    private void academy$disableDetachedViewOcclusion(
            CameraRenderState cameraState,
            DeltaTracker deltaTracker,
            CallbackInfo ci
    ) {
        if (WideAreaInterferenceClientState.hasCameraOverride()
                || ChunkLeapGodView.shouldOverrideCamera()) {
            cameraState.smartCull = false;
        }
    }

    @Inject(method = "extractRenderState", at = @At("TAIL"))
    private void academy$stabilizeAbilityDeveloperView(
            CameraRenderState cameraState,
            DeltaTracker deltaTracker,
            CallbackInfo ci
    ) {
        AbilityDeveloperSleepClient.stabilizeCamera((Camera) (Object) this, cameraState);
    }
}
