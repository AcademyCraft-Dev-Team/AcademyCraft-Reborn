package org.academy.mixin.client;

import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.renderer.texture.TextureAtlas;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(TextureAtlas.class)
public abstract class MixinTextureAtlas {
    // 临时修复mc的屎山时序问题
    @Inject(method = "uploadAnimationFrames", cancellable = true, at = @At("HEAD"))
    public void fix(CallbackInfo ci) {
        if (RenderSystem.getGlobalSettingsUniform() == null) ci.cancel();
    }
}
