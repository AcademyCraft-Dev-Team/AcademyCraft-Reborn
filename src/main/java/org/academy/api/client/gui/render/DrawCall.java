package org.academy.api.client.gui.render;

import com.mojang.blaze3d.buffers.GpuBufferSlice;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.textures.GpuTextureView;
import org.jspecify.annotations.Nullable;

import java.util.Map;

public record DrawCall(
        RenderPipeline pipeline,
        @Nullable ScissorRect scissorArea,
        Map<String, GpuTextureView> samplers,
        Map<String, GpuBufferSlice> uniforms,
        int baseVertex,
        int indexCount
) {
}