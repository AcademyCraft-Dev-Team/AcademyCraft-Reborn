package org.academy.mixin.common;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.sensing.Sensing;
import org.academy.api.common.entitycontrol.MentalPerceptionApi;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Sensing.class)
public abstract class MixinSensing {
    @Shadow
    @Final
    private Mob mob;

    @Inject(method = "hasLineOfSight", at = @At("HEAD"), cancellable = true)
    private void academy$filterMentalPerception(
            Entity target,
            CallbackInfoReturnable<Boolean> cir
    ) {
        if (target instanceof LivingEntity living && !MentalPerceptionApi.canPerceive(mob, living)) {
            cir.setReturnValue(false);
        }
    }
}
