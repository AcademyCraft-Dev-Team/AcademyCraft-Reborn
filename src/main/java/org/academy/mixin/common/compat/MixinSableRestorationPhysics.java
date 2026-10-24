package org.academy.mixin.common.compat;

import dev.ryanhcode.sable.sublevel.SubLevel;
import dev.ryanhcode.sable.sublevel.storage.SubLevelRemovalReason;
import dev.ryanhcode.sable.sublevel.system.SubLevelPhysicsSystem;
import org.academy.internal.common.structure.sable.SableRestorationIsolation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** The recovery transaction already detached this body's native handle. */
@Mixin(value = SubLevelPhysicsSystem.class, remap = false)
public abstract class MixinSableRestorationPhysics {
    @Inject(method = "onSubLevelRemoved", at = @At("HEAD"), cancellable = true)
    private void academy$skipAlreadyDetached(SubLevel source, SubLevelRemovalReason reason, CallbackInfo callback) {
        if (SableRestorationIsolation.detached(source)) callback.cancel();
    }
}
