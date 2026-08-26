package org.academy.mixin.client;

import net.minecraft.client.animation.KeyframeAnimation;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.client.renderer.entity.state.HumanoidRenderState;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.HumanoidArm;
import org.academy.internal.client.animation.GeckoPlayerModelAdapter;
import org.academy.internal.client.animation.WingFlightAnimationClient;
import org.academy.internal.client.animation.WingFlightAnimationTimeline;
import org.academy.internal.client.definitions.WingFlightAnimations;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Applies the authored full-body wing pose to player and matching armor models.
 */
@Mixin(HumanoidModel.class)
public abstract class MixinHumanoidModel {
    @Unique
    private KeyframeAnimation academy$startFlyingSlow;
    @Unique
    private KeyframeAnimation academy$flyingSlow;
    @Unique
    private KeyframeAnimation academy$startFlyingFast;
    @Unique
    private KeyframeAnimation academy$flyingFast;
    @Unique
    private KeyframeAnimation academy$quitFlyingFast;

    @Inject(
            method = "setupAnim(Lnet/minecraft/client/renderer/entity/state/HumanoidRenderState;)V",
            at = @At("RETURN")
    )
    private void academy$applyWingFlightAnimation(
            HumanoidRenderState state,
            CallbackInfo ci
    ) {
        if (!(state instanceof AvatarRenderState avatarState)) return;
        var playback = WingFlightAnimationClient.playback(avatarState.id);
        if (playback == null || playback.phase() == WingFlightAnimationTimeline.Phase.IDLE) return;

        var model = (HumanoidModel<?>) (Object) this;
        var mainArm = state.mainArm;
        var vanillaMainArmPose = model.getArm(mainArm).storePose();
        var vanillaOffhandArmPose = model.getArm(mainArm.getOpposite()).storePose();
        var mainHandUse = state.isUsingItem && state.useItemHand == InteractionHand.MAIN_HAND;
        var mainHandAttack = state.attackTime > 0.0f && state.attackArm == mainArm;
        var mainArmPose = mainArm == HumanoidArm.RIGHT
                ? state.rightArmPose
                : state.leftArmPose;

        academy$resetFlightParts(model);
        var animation = academy$animation(model, playback.phase());
        animation.apply((long) (playback.clipTimeSeconds() * 1000.0f), 1.0f);
        GeckoPlayerModelAdapter.applyRootPivot(model.root());

        // The Gecko clip is the body base pose. Player look and active hand actions
        // remain vanilla overlays, so flying does not lock the head or held item.
        model.head.xRot += state.xRot * (float) (Math.PI / 180.0);
        model.head.yRot += state.yRot * (float) (Math.PI / 180.0);
        if (mainHandUse || mainHandAttack) {
            model.getArm(mainArm).loadPose(vanillaMainArmPose);
            if (mainHandUse && mainArmPose.isTwoHanded()) {
                model.getArm(mainArm.getOpposite()).loadPose(vanillaOffhandArmPose);
            }
        }
    }

    @Unique
    private void academy$resetFlightParts(HumanoidModel<?> model) {
        model.root().resetPose();
        model.head.resetPose();
        model.hat.resetPose();
        model.body.resetPose();
        model.rightArm.resetPose();
        model.leftArm.resetPose();
        model.rightLeg.resetPose();
        model.leftLeg.resetPose();
    }

    @Unique
    private KeyframeAnimation academy$animation(
            HumanoidModel<?> model,
            WingFlightAnimationTimeline.Phase phase
    ) {
        return switch (phase) {
            case START_FLYING_SLOW, STOP_FLYING_SLOW -> {
                if (academy$startFlyingSlow == null) {
                    academy$startFlyingSlow = WingFlightAnimations.START_FLYING_SLOW
                            .bake(model.root());
                }
                yield academy$startFlyingSlow;
            }
            case FLYING_SLOW -> {
                if (academy$flyingSlow == null) {
                    academy$flyingSlow = WingFlightAnimations.FLYING_SLOW.bake(model.root());
                }
                yield academy$flyingSlow;
            }
            case START_FLYING_FAST -> {
                if (academy$startFlyingFast == null) {
                    academy$startFlyingFast = WingFlightAnimations.START_FLYING_FAST
                            .bake(model.root());
                }
                yield academy$startFlyingFast;
            }
            case FLYING_FAST -> {
                if (academy$flyingFast == null) {
                    academy$flyingFast = WingFlightAnimations.FLYING_FAST.bake(model.root());
                }
                yield academy$flyingFast;
            }
            case QUIT_FLYING_FAST -> {
                if (academy$quitFlyingFast == null) {
                    academy$quitFlyingFast = WingFlightAnimations.QUIT_FLYING_FAST
                            .bake(model.root());
                }
                yield academy$quitFlyingFast;
            }
            case IDLE -> throw new IllegalArgumentException("Idle wing animation has no clip");
        };
    }
}
