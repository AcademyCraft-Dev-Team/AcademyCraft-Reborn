package org.academy.mixin.common.compat;

import org.academy.internal.common.structure.sable.reflect.api.api.sublevel.SubLevelContainer;
import org.academy.internal.common.structure.sable.reflect.api.companion.math.BoundingBox3dc;
import org.academy.internal.common.structure.sable.reflect.api.sublevel.SubLevel;
import org.academy.internal.common.structure.sable.SableRestorationIsolation;
import org.spongepowered.asm.mixin.Final;
import org.academy.internal.common.structure.sable.reflect.SableReflection;
import org.spongepowered.asm.mixin.injection.Coerce;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.List;

/** Pinned Sable compatibility: journal-owned sources remain private until rollback/retirement. */
@Pseudo
@Mixin(targets = "dev.ryanhcode.sable.api.sublevel.SubLevelContainer", remap = false)
public abstract class MixinSableRestorationIsolation {
    @Shadow @Final private List<Object> allSubLevels;

    @Inject(method = "tick", at = @At("HEAD"))
    private void academy$isolateBeforePublication(CallbackInfo callback) {
        SableRestorationIsolation.beforeTick(SableReflection.wrap(SubLevelContainer.class, this),
                SableReflection.list(SubLevel.class, allSubLevels));
    }

    @Inject(method = "getFirstEmptyPlot", at = @At("HEAD"))
    private void academy$reserveRecoveryPlots(CallbackInfoReturnable<org.joml.Vector2i> callback) {
        SableRestorationIsolation.reservePendingPlots(SableReflection.wrap(SubLevelContainer.class, this));
    }

    @Inject(method = "getAllSubLevels", at = @At("RETURN"), cancellable = true)
    private void academy$hideRecoverySources(CallbackInfoReturnable<List<Object>> callback) {
        callback.setReturnValue(SableRestorationIsolation.visibleRaw(allSubLevels));
    }

    @Redirect(method = "processSubLevelRemovals", at = @At(value = "FIELD",
            target = "Ldev/ryanhcode/sable/api/sublevel/SubLevelContainer;allSubLevels:Ljava/util/List;"))
    private List<Object> academy$keepPrivateSources(@Coerce Object container) {
        return SableRestorationIsolation.visibleRaw(allSubLevels);
    }

    @Inject(method = "queryIntersecting", at = @At("HEAD"), cancellable = true)
    private void academy$excludePrivateCollision(@Coerce Object bounds, CallbackInfoReturnable<Iterable<Object>> callback) {
        var visible = SableRestorationIsolation.visibleRaw(allSubLevels);
        if (visible != allSubLevels) callback.setReturnValue(() -> visible.stream()
                .filter(source -> SableReflection.wrap(SubLevel.class, source).boundingBox().intersects(SableReflection.wrap(BoundingBox3dc.class, bounds))).iterator());
    }
}
