package org.academy.mixin.common.compat;

import org.academy.internal.common.structure.sable.reflect.api.sublevel.SubLevel;
import org.academy.internal.common.structure.sable.reflect.api.sublevel.system.SubLevelPhysicsSystem;
import org.academy.internal.common.structure.sable.SableRestorationIsolation;
import org.academy.internal.common.structure.sable.reflect.SableReflection;
import org.spongepowered.asm.mixin.injection.Coerce;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** The recovery transaction already detached this body's native handle. */
@Pseudo
@Mixin(targets = "dev.ryanhcode.sable.sublevel.system.SubLevelPhysicsSystem", remap = false)
public abstract class MixinSableRestorationPhysics {
    @Inject(method = "onSubLevelRemoved", at = @At("HEAD"), cancellable = true)
    private void academy$skipAlreadyDetached(@Coerce Object source, @Coerce Object reason, CallbackInfo callback) {
        if (SableRestorationIsolation.detached(SableReflection.wrap(SubLevel.class, source))) callback.cancel();
    }
}
