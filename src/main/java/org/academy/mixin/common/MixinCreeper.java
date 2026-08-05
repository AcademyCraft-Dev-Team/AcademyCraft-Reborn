package org.academy.mixin.common;

import net.minecraft.world.entity.monster.Creeper;
import org.academy.internal.common.ability.mentalout.control.MentalControlRuntime;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Creeper.class)
public abstract class MixinCreeper {
    @Inject(method = "tick", at = @At("HEAD"))
    private void academy$pauseMentalStuporFuse(CallbackInfo ci) {
        var creeper = (Creeper) (Object) this;
        if (MentalControlRuntime.isFrozen(creeper)) {
            creeper.setSwellDir(0);
        }
    }

    @Redirect(
            method = "tick",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/world/entity/monster/Creeper;isIgnited()Z"
            )
    )
    private boolean academy$ignoreIgnitionDuringMentalStupor(Creeper creeper) {
        return !MentalControlRuntime.isFrozen(creeper) && creeper.isIgnited();
    }

    @Inject(method = "explodeCreeper", at = @At("HEAD"), cancellable = true)
    private void academy$preventMentalStuporExplosion(CallbackInfo ci) {
        if (MentalControlRuntime.isFrozen((Creeper) (Object) this)) {
            ci.cancel();
        }
    }
}
