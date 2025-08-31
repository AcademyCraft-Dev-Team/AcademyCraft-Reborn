package org.academy.mixin.client;

import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.renderer.entity.player.PlayerRenderer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import org.academy.internal.client.renderer.entity.layers.SkillEffectsLayer;
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
@Mixin(PlayerRenderer.class)
public abstract class MixinPlayerRenderer {
    @Inject(method = "getArmPose(Lnet/minecraft/world/entity/player/Player;Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/world/InteractionHand;)Lnet/minecraft/client/model/HumanoidModel$ArmPose;", at = @At("HEAD"), cancellable = true)
    private static void getArmPose(Player player, ItemStack stack, InteractionHand hand, CallbackInfoReturnable<HumanoidModel.ArmPose> cir) {
        var itemstack = player.getItemInHand(hand);
        if (itemstack.getItem() == Items.IMAGIPHASE_DOWSING_ROD.get()) {
            cir.setReturnValue(HumanoidModel.ArmPose.CROSSBOW_HOLD);
        }
    }

    @Unique
    public PlayerRenderer academyCraft$instance;

    @Inject(method = "<init>", at = @At("TAIL"))
    private void onInit(CallbackInfo ci) {
        academyCraft$instance = (PlayerRenderer) (Object) this;
        academyCraft$instance.addLayer(new SkillEffectsLayer(academyCraft$instance));
    }
}