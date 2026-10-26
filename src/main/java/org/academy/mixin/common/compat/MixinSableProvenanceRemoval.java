package org.academy.mixin.common.compat;

import org.academy.internal.common.structure.sable.SableProvenanceLifecycle;
import org.academy.internal.common.structure.sable.reflect.SableReflection;
import org.academy.internal.common.structure.sable.reflect.api.api.sublevel.ServerSubLevelContainer;
import org.academy.internal.common.structure.sable.reflect.api.sublevel.storage.SubLevelRemovalReason;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Coerce;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Pseudo
@Mixin(targets = "dev.ryanhcode.sable.api.sublevel.ServerSubLevelContainer", remap = false)
public abstract class MixinSableProvenanceRemoval {
    @Inject(method = "removeSubLevel", at = @At("HEAD"))
    private void academy$journalPermanentRemoval(int x, int z, @Coerce Object reason, CallbackInfo callback) {
        SableProvenanceLifecycle.removing(SableReflection.wrap(ServerSubLevelContainer.class, this), x, z,
                SableReflection.wrap(SubLevelRemovalReason.class, reason));
    }
}
