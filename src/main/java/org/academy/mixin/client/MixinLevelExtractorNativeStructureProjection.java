package org.academy.mixin.client;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.extract.LevelExtractor;
import net.minecraft.world.entity.Entity;
import org.academy.internal.common.world.entity.structure.NativeStructureProjectionEntity;
import org.jspecify.annotations.Nullable;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Sparse views can remain visible while the section containing their center is occluded. */
@Mixin(LevelExtractor.class)
public abstract class MixinLevelExtractorNativeStructureProjection {
    @Shadow @Final private LevelRenderer levelRenderer;
    @Shadow private @Nullable ClientLevel level;

    @Inject(method = "isEntityVisible", at = @At("HEAD"), cancellable = true)
    private void academy$cullProjectionBounds(Entity entity, Frustum frustum,
                                              double camX, double camY, double camZ,
                                              CallbackInfoReturnable<Boolean> callback) {
        if (!(entity instanceof NativeStructureProjectionEntity projection)) return;
        // Keep the renderer's distance/frustum checks and ordinary GPU terrain depth testing.
        // Only the extra origin-section visibility test is inapplicable to a distributed view.
        callback.setReturnValue(level != null && entity.level() == level && !entity.isRemoved()
                && !projection.view().isEmpty()
                && levelRenderer.entityRenderDispatcher().shouldRender(entity, frustum, camX, camY, camZ));
    }
}
