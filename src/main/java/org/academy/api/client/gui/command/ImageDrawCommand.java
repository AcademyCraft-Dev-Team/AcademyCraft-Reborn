package org.academy.api.client.gui.command;

import com.mojang.blaze3d.buffers.GpuBufferSlice;
import com.mojang.blaze3d.textures.GpuTextureView;
import org.academy.api.client.Render;

import java.util.Collections;
import java.util.Map;

public class ImageDrawCommand extends PosTexColorRectDrawCommand {
    protected final GpuTextureView textureView;

    public ImageDrawCommand(
            GpuTextureView gpuTextureView,
            float width,
            float height,
            float u0,
            float v0,
            float u1,
            float v1,
            float red,
            float green,
            float blue,
            float alpha
    ) {
        super(Render.RenderPipelines.IMAGE,width,height,u0,v0,u1,v1,red,green,blue,alpha);
        textureView = gpuTextureView;
    }

    @Override
    public Map<String, GpuTextureView> getSamplers() {
        return Map.of("Sampler0", textureView);
    }

    @Override
    public Map<String, GpuBufferSlice> getUniforms() {
        return Collections.emptyMap();
    }
}