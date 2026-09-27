package org.academy.mixin.common;

import net.minecraft.world.entity.monster.creaking.Creaking;
import org.academy.api.common.entitycontrol.ControlCapability;
import org.academy.internal.common.ability.mentalout.control.MentalControlRuntime;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Creaking.class)
public abstract class MixinCreaking {
    @Inject(method = "canMove", at = @At("HEAD"), cancellable = true)
    private void academy$allowControlledNavigation(CallbackInfoReturnable<Boolean> cir) {
        var creaking = (Creaking) (Object) this;
        if (MentalControlRuntime.effectiveDirective(creaking, ControlCapability.PATH_CONTROL).isPresent()
                || MentalControlRuntime.effectiveDirective(creaking, ControlCapability.GUARD_CONTROL).isPresent()) {
            cir.setReturnValue(true);
        }
    }
}
