package org.academy.mixin.client;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.ItemInHandRenderer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Avatar;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import org.academy.internal.client.ability.mentalout.ControlledItemInHandRendererBridge;
import org.academy.internal.client.ability.mentalout.PlayerControlClientState;
import org.academy.internal.client.renderer.special.AbilityControlTabletSpecialRenderer;
import org.academy.internal.client.renderer.special.ImagPhaseDowsingRodSpecialRenderer;
import org.academy.internal.common.ability.mentalout.PlayerControlSessionManager;
import org.academy.internal.common.attachment.AttachmentTypes;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ItemInHandRenderer.class)
public abstract class MixinItemInHandRenderer implements ControlledItemInHandRendererBridge {
    @Shadow
    private ItemStack mainHandItem;

    @Shadow
    private ItemStack offHandItem;

    @Override
    public void academy$submitControlledHands(
            AbstractClientPlayer player,
            PlayerControlSessionManager.TargetViewState state,
            float partialTick,
            PoseStack poseStack,
            SubmitNodeCollector collector,
            int packedLight
    ) {
        var previousMainHand = mainHandItem;
        var previousOffHand = offHandItem;
        mainHandItem = state.selectedItem();
        offHandItem = state.offhand();
        try {
            var attack = player.getAttackAnim(partialTick);
            ((ItemInHandRendererInvoker) this).academy$submitArmWithItem(
                    player,
                    partialTick,
                    PlayerControlClientState.controllerViewPitch(),
                    InteractionHand.MAIN_HAND,
                    attack,
                    mainHandItem,
                    0.0f,
                    poseStack,
                    collector,
                    packedLight
            );
            ((ItemInHandRendererInvoker) this).academy$submitArmWithItem(
                    player,
                    partialTick,
                    PlayerControlClientState.controllerViewPitch(),
                    InteractionHand.OFF_HAND,
                    0.0f,
                    offHandItem,
                    0.0f,
                    poseStack,
                    collector,
                    packedLight
            );
        } finally {
            mainHandItem = previousMainHand;
            offHandItem = previousOffHand;
        }
    }

    @Inject(method = "renderItem", at = @At("HEAD"), cancellable = true)
    private void hideMagneticWeaponInMainHand(
            LivingEntity entity,
            ItemStack stack,
            ItemDisplayContext displayContext,
            PoseStack poseStack,
            SubmitNodeCollector collector,
            int packedLight,
            CallbackInfo ci
    ) {
        AbilityControlTabletSpecialRenderer.prepareItemRender(entity, stack, displayContext);
        ImagPhaseDowsingRodSpecialRenderer.prepareItemRender(entity, stack, displayContext);
        if (!(entity instanceof Avatar avatar)) return;
        var data = avatar.getData(AttachmentTypes.MAGNETIC_WEAPON_DATA.get());
        if (!data.active() || !data.hideMainHand()) return;
        var mainHandContext = avatar.getMainArm() == HumanoidArm.RIGHT
                ? ItemDisplayContext.FIRST_PERSON_RIGHT_HAND
                : ItemDisplayContext.FIRST_PERSON_LEFT_HAND;
        if (displayContext == mainHandContext) ci.cancel();
    }
}
