package org.academy.mixin.client;

import net.minecraft.client.entity.ClientAvatarEntity;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.renderer.entity.player.AvatarRenderer;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Avatar;
import net.minecraft.world.item.ItemStack;
import org.academy.internal.client.renderer.effect.StormWingEffectRenderer;
import org.academy.internal.client.renderer.entity.layers.SkillEffectsLayer;
import org.academy.internal.common.attachment.AttachmentTypes;
import org.academy.internal.common.world.item.Items;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * For SkillEffectsLayer
 */
@Mixin(AvatarRenderer.class)
public abstract class MixinAvatarRenderer {
/*    @Inject(method = "getArmPose(Lnet/minecraft/world/entity/Avatar;Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/world/InteractionHand;)Lnet/minecraft/client/model/HumanoidModel$ArmPose;", at = @At("HEAD"), cancellable = true)
    private static void getArmPose(Avatar avatar, ItemStack itemStack, InteractionHand hand, CallbackInfoReturnable<HumanoidModel.ArmPose> cir) {
        var itemstack = avatar.getItemInHand(hand);
        if (itemstack.getItem() == Items.IMAGIPHASE_DOWSING_ROD.get()) {
            cir.setReturnValue(HumanoidModel.ArmPose.CROSSBOW_HOLD);
        }
    }*/

    @Unique
    public AvatarRenderer<?> academyCraft$instance;

    @Inject(method = "<init>", at = @At("TAIL"))
    private void onInit(CallbackInfo ci) {
        academyCraft$instance = (AvatarRenderer<?>) (Object) this;
        academyCraft$instance.addLayer(new SkillEffectsLayer(academyCraft$instance));
    }

    @Inject(method = "extractRenderState(Lnet/minecraft/world/entity/Avatar;Lnet/minecraft/client/renderer/entity/state/AvatarRenderState;F)V", at = @At("HEAD"))
    private <AvatarlikeEntity extends Avatar & ClientAvatarEntity> void onExtractRenderState(AvatarlikeEntity avatarlikeEntity, AvatarRenderState avatarRenderState, float partialTick, CallbackInfo ci) {
        avatarRenderState.setRenderData(
                StormWingEffectRenderer.CONTEXT_KEY,
                avatarlikeEntity.getData(AttachmentTypes.ACTIVATED_STORM_WING)
        );
    }
}