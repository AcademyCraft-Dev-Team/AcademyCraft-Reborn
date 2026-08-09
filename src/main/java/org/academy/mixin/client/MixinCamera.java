package org.academy.mixin.client;

import net.minecraft.client.Camera;
import net.minecraft.client.DeltaTracker;
import org.academy.internal.client.ability.mentalout.PlayerControlClientState;
import org.academy.internal.client.renderer.vfx.CameraShakeManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Camera.class)
public abstract class MixinCamera {
    @Invoker("setRotation")
    protected abstract void academy$setRotation(float yRot, float xRot);

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
            academy$setRotation(
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
        academy$setRotation(camera.yRot() + offset.yaw(), camera.xRot() + offset.pitch());
    }
}
