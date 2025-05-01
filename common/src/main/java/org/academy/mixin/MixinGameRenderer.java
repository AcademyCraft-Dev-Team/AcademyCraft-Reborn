package org.academy.mixin;

import com.google.common.collect.Maps;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.server.packs.resources.ResourceProvider;
import org.academy.internal.client.renderer.Shaders;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Map;
import java.util.function.Function;

@Mixin(GameRenderer.class)
public class MixinGameRenderer {
    @Shadow
    private final Map<String, ShaderInstance> shaders = Maps.newHashMap();

    @Inject(method = "reloadShaders", at = @At("TAIL"))
    private void reloadShaders(ResourceProvider resourceProvider, CallbackInfo ci) {
        for (Function<ResourceProvider, ShaderInstance> shader : Shaders.SHADERS) {
            shaders.put(shader.toString(), shader.apply(resourceProvider));
        }
    }
}