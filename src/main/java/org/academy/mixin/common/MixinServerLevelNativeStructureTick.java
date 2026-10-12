package org.academy.mixin.common;

import net.minecraft.server.level.ServerLevel;
import org.academy.internal.common.structure.NativeStructureRuntime;
import org.academy.internal.common.structure.space.NativeStructureDimensions;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.function.BooleanSupplier;

/** Paused and quarantined native containers must not keep mutating through vanilla tickers. */
@Mixin(value = ServerLevel.class, priority = 2100)
public abstract class MixinServerLevelNativeStructureTick {
    @Inject(method = "tick(Ljava/util/function/BooleanSupplier;)V", at = @At("HEAD"), cancellable = true)
    private void academy$freezeInactiveStructureSpace(BooleanSupplier haveTime, CallbackInfo callback) {
        var level = (ServerLevel) (Object) this;
        if (NativeStructureDimensions.isStorageLevel(level.dimension())
                && !NativeStructureRuntime.canTickStorageLevel(level)) callback.cancel();
    }
}
