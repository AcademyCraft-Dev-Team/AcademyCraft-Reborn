package org.academy.mixin.common;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import org.academy.api.common.entitycontrol.MentalPerceptionApi;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Player.class)
public abstract class MixinPlayerMentalPerception {
    @Inject(method = "attack", at = @At("HEAD"), cancellable = true)
    private void academy$rejectHiddenAttack(Entity target, CallbackInfo ci) {
        if ((Object) this instanceof ServerPlayer observer
                && target instanceof LivingEntity living
                && !MentalPerceptionApi.canPerceive(observer, living)) {
            ci.cancel();
        }
    }

    @Inject(method = "interactOn", at = @At("HEAD"), cancellable = true)
    private void academy$rejectHiddenInteraction(
            Entity target,
            InteractionHand hand,
            Vec3 location,
            CallbackInfoReturnable<InteractionResult> cir
    ) {
        if ((Object) this instanceof ServerPlayer observer
                && target instanceof LivingEntity living
                && !MentalPerceptionApi.canPerceive(observer, living)) {
            cir.setReturnValue(InteractionResult.FAIL);
        }
    }
}
