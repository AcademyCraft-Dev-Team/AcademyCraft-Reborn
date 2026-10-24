package org.academy.mixin.common.compat;

import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.ryanhcode.sable.companion.math.BoundingBox3dc;
import dev.ryanhcode.sable.sublevel.SubLevel;
import org.academy.internal.common.structure.sable.SableRestorationIsolation;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.List;

/** Pinned Sable compatibility: journal-owned sources remain private until rollback/retirement. */
@Mixin(value = SubLevelContainer.class, remap = false)
public abstract class MixinSableRestorationIsolation {
    @Shadow @Final private List<SubLevel> allSubLevels;

    @Inject(method = "tick", at = @At("HEAD"))
    private void academy$isolateBeforePublication(CallbackInfo callback) {
        SableRestorationIsolation.beforeTick((SubLevelContainer) (Object) this, allSubLevels);
    }

    @Inject(method = "getFirstEmptyPlot", at = @At("HEAD"))
    private void academy$reserveRecoveryPlots(CallbackInfoReturnable<org.joml.Vector2i> callback) {
        SableRestorationIsolation.reservePendingPlots((SubLevelContainer) (Object) this);
    }

    @Inject(method = "getAllSubLevels", at = @At("RETURN"), cancellable = true)
    private void academy$hideRecoverySources(CallbackInfoReturnable<List<? extends SubLevel>> callback) {
        callback.setReturnValue(SableRestorationIsolation.visible(allSubLevels));
    }

    @Redirect(method = "processSubLevelRemovals", at = @At(value = "FIELD",
            target = "Ldev/ryanhcode/sable/api/sublevel/SubLevelContainer;allSubLevels:Ljava/util/List;"))
    private List<SubLevel> academy$keepPrivateSources(SubLevelContainer container) {
        return SableRestorationIsolation.visible(allSubLevels);
    }

    @Inject(method = "queryIntersecting", at = @At("HEAD"), cancellable = true)
    private void academy$excludePrivateCollision(BoundingBox3dc bounds, CallbackInfoReturnable<Iterable<SubLevel>> callback) {
        var visible = SableRestorationIsolation.visible(allSubLevels);
        if (visible != allSubLevels) callback.setReturnValue(() -> visible.stream()
                .filter(source -> source.boundingBox().intersects(bounds)).iterator());
    }
}
