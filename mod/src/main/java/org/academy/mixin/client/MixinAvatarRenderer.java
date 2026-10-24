package org.academy.mixin.client;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.player.AvatarRenderer;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.Avatar;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.player.PlayerModelType;
import net.minecraft.world.item.ItemStack;
import org.academy.api.client.render.vfxgraph.shape.FirstPersonHandSurface;
import org.academy.api.client.render.vfxgraph.shape.HumanoidSurfacePose;
import org.academy.internal.client.animation.AbilityDeveloperSleepClient;
import org.academy.internal.client.animation.WingFlightAnimationClient;
import org.academy.internal.common.attachment.AttachmentTypes;
import org.joml.Matrix4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@SuppressWarnings("AddedMixinMembersNamePattern")
@Mixin(AvatarRenderer.class)
public abstract class MixinAvatarRenderer {
    @Inject(method = "setupRotations*", at = @At("HEAD"))
    private void alignWithAbilityDeveloperPod(
            AvatarRenderState state,
            PoseStack poseStack,
            float bodyRot,
            float entityScale,
            CallbackInfo ci
    ) {
        AbilityDeveloperSleepClient.applyModelTransform(state, poseStack);
    }

    @Inject(method = "setupRotations*", at = @At("RETURN"))
    private void captureModelRootMatrix(AvatarRenderState state, PoseStack poseStack, float bodyRot, float entityScale, CallbackInfo ci) {
        var matrix = new Matrix4f(poseStack.last().pose());
        matrix.scale(-1.0f, -1.0f, 1.0f);
        matrix.scale(0.9375f);
        matrix.translate(0.0f, -1.501f, 0.0f);
        HumanoidSurfacePose.captureRoot(state.id, matrix);
    }

    @Inject(method = "renderHand", at = @At("RETURN"))
    private void academy$captureFirstPersonArm(
            PoseStack poseStack,
            SubmitNodeCollector submitNodeCollector,
            int lightCoords,
            Identifier skinTexture,
            ModelPart arm,
            boolean hasSleeve,
            CallbackInfo ci
    ) {
        var model = ((AvatarRenderer<?>) (Object) this).getModel();
        var side = arm == model.rightArm ? HumanoidArm.RIGHT
                : arm == model.leftArm ? HumanoidArm.LEFT : null;
        if (side == null) return;
        var pose = new PoseStack();
        pose.last().set(poseStack.last());
        arm.translateAndRotate(pose);
        var player = Minecraft.getInstance().player;
        var slim = player != null && player.getSkin().model() == PlayerModelType.SLIM;
        var view = Minecraft.getInstance().gameRenderer.mainCamera().getViewRotationMatrix(new Matrix4f());
        FirstPersonHandSurface.addArm(side, view.mul(pose.last().pose()), slim);
    }

    @Inject(
            method = "extractRenderState(Lnet/minecraft/world/entity/Avatar;Lnet/minecraft/client/renderer/entity/state/AvatarRenderState;F)V",
            at = @At("RETURN")
    )
    private void hideMagneticWeaponInMainHand(
            Avatar avatar, AvatarRenderState state, float partialTick, CallbackInfo ci
    ) {
        var data = avatar.getData(AttachmentTypes.MAGNETIC_WEAPON_DATA.get());
        if (!data.active() || !data.hideMainHand()) return;
        if (avatar.getMainArm() == HumanoidArm.RIGHT) {
            state.rightHandItemState.clear();
            state.rightHandItemStack = ItemStack.EMPTY;
        } else {
            state.leftHandItemState.clear();
            state.leftHandItemStack = ItemStack.EMPTY;
        }
    }

    @Inject(
            method = "extractRenderState(Lnet/minecraft/world/entity/Avatar;Lnet/minecraft/client/renderer/entity/state/AvatarRenderState;F)V",
            at = @At("RETURN")
    )
    private void extractWingFlightAnimation(
            Avatar avatar, AvatarRenderState state, float partialTick, CallbackInfo ci
    ) {
        WingFlightAnimationClient.prepareRenderState(avatar, state, partialTick);
    }
}
