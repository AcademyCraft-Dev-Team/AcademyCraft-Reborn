package org.academy.forge.mixin;

import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.platform.GlStateManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(RenderTarget.class)
public abstract class MixinRenderTarget {
    @Shadow
    public int width;

    @Shadow
    public int height;

    @Shadow
    protected int depthBufferId;

    @SuppressWarnings("BooleanMethodIsAlwaysInverted")
    @Shadow(remap = false)
    public abstract boolean isStencilEnabled();

    @Inject(method = "createBuffers", at = @At(value = "INVOKE", shift = At.Shift.AFTER, target = "Lcom/mojang/blaze3d/platform/GlStateManager;_texImage2D(IIIIIIIILjava/nio/IntBuffer;)V", ordinal = 0))
    private void createBuffers(CallbackInfo ci) {
        if (!isStencilEnabled()) {
            GlStateManager._texImage2D(3553, 0, 36013, this.width, this.height, 0, 34041, 36269, null);
        }
    }

    @Redirect(method = "createBuffers", at = @At(value = "INVOKE", target = "Lcom/mojang/blaze3d/platform/GlStateManager;_glFramebufferTexture2D(IIIII)V", ordinal = 1))
    private void createBuffersDep(int target, int attachment, int texTarget, int texture, int level) {
        if (!isStencilEnabled()) {
            GlStateManager._glFramebufferTexture2D(36160, 33306, 3553, this.depthBufferId, 0);
        }
    }
}