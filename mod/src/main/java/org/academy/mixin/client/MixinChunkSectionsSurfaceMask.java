package org.academy.mixin.client;

import com.mojang.renderpearl.api.commands.RenderPass;
import com.mojang.renderpearl.api.textures.GpuSampler;
import com.mojang.renderpearl.api.textures.GpuTextureView;
import net.minecraft.client.renderer.chunk.ChunkSectionLayerGroup;
import net.minecraft.client.renderer.chunk.ChunkSectionsToRender;
import org.academy.api.client.render.post.WorldSurfaceMasks;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ChunkSectionsToRender.class)
public abstract class MixinChunkSectionsSurfaceMask {
    @Inject(method = "renderGroup", at = @At("RETURN"))
    private void academy$transparentSurface(ChunkSectionLayerGroup group, RenderPass pass, GpuSampler sampler,
                                            GpuTextureView textureView, boolean flag, CallbackInfo ci) {
        if (group == ChunkSectionLayerGroup.TRANSLUCENT)
            WorldSurfaceMasks.captureTransparent((ChunkSectionsToRender) (Object) this, sampler);
    }
}
