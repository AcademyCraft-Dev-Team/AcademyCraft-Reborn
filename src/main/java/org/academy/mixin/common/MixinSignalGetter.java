package org.academy.mixin.common;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.SignalGetter;
import org.academy.internal.common.ability.electromaster.skills.lv1.PulseCharge;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(SignalGetter.class)
public interface MixinSignalGetter {
    @Inject(method = "hasNeighborSignal", at = @At("HEAD"), cancellable = true)
    private void academy$hasPulseChargeSignal(BlockPos pos, CallbackInfoReturnable<Boolean> cir) {
        if ((Object) this instanceof Level level && PulseCharge.Server.hasArtificialSignal(level, pos)) {
            cir.setReturnValue(true);
        }
    }

    @Inject(method = "getBestNeighborSignal", at = @At("HEAD"), cancellable = true)
    private void academy$getPulseChargeSignal(BlockPos pos, CallbackInfoReturnable<Integer> cir) {
        if ((Object) this instanceof Level level && PulseCharge.Server.hasArtificialSignal(level, pos)) {
            cir.setReturnValue(15);
        }
    }
}
