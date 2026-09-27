package org.academy.mixin.common;

import net.minecraft.world.ticks.LevelTicks;
import net.minecraft.world.ticks.ScheduledTick;
import org.academy.internal.server.time.TemporalRuntime;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = LevelTicks.class, priority = 1900)
public abstract class MixinLevelTicksTemporalScaling<T> {
    @Inject(method = "scheduleForThisTick", at = @At("HEAD"), cancellable = true)
    private void academy$deferPausedScheduledTick(
            ScheduledTick<T> tick,
            CallbackInfo ci
    ) {
        @SuppressWarnings("unchecked")
        var queue = (LevelTicks<T>) (Object) this;
        if (TemporalRuntime.deferScheduledTickIfPaused(queue, tick)) ci.cancel();
    }
}
