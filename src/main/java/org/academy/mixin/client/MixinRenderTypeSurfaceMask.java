package org.academy.mixin.client;

import net.minecraft.client.renderer.rendertype.PreparedRenderType;
import net.minecraft.client.renderer.rendertype.RenderType;
import org.academy.api.client.render.post.WorldSurfaceMasks;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(RenderType.class)
public abstract class MixinRenderTypeSurfaceMask {
    @Inject(method = "prepare", at = @At("RETURN"), cancellable = true)
    private void academy$surfaceMask(CallbackInfoReturnable<PreparedRenderType> cir) {
        cir.setReturnValue(WorldSurfaceMasks.redirect(cir.getReturnValue()));
    }
}
