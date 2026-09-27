package org.academy.mixin.client;

import com.mojang.renderpearl.api.buffers.GpuBuffer;
import com.mojang.renderpearl.api.commands.RenderPass;
import com.mojang.renderpearl.api.pipeline.IndexType;
import com.mojang.renderpearl.api.pipeline.RenderPipeline;
import net.minecraft.client.renderer.chunk.ChunkSectionLayer;
import net.minecraft.client.renderer.chunk.ChunkSectionsToRender;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(ChunkSectionsToRender.class)
public interface ChunkSectionsToRenderInvoker {
    @Invoker("render")
    void academy$render(ChunkSectionLayer layer, RenderPass pass, GpuBuffer indexBuffer,
                        IndexType indexType, RenderPipeline pipeline, RenderPipeline oitPipeline);
}
