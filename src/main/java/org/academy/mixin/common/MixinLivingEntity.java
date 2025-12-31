package org.academy.mixin.common;

import net.minecraft.world.entity.LivingEntity;
import org.academy.api.client.util.QuantumUtil;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LivingEntity.class)
public abstract class MixinLivingEntity {
    @Inject(method = "tick", at = @At("TAIL"))
    private void academy$quantumHealthFluctuation(CallbackInfo ci) {
        QuantumUtil.quantumHealthFluctuation((LivingEntity) (Object) this);
    }
}