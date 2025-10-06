package org.academy.api.client.gui.command;

import com.mojang.blaze3d.buffers.GpuBufferSlice;
import com.mojang.blaze3d.textures.GpuTextureView;
import org.academy.api.client.Render;

import java.util.Collections;
import java.util.Map;

public class FillRectDrawCommand extends PosColorRectDrawCommand {
    public FillRectDrawCommand(
            float width,
            float height,
            float red,
            float green,
            float blue,
            float alpha
    ) {
        super(Render.RenderPipelines.POS_COLOR, width, height, red, green, blue, alpha);
    }

    @Override
    public Map<String, GpuTextureView> getSamplers() {
        return Collections.emptyMap();
    }

    @Override
    public Map<String, GpuBufferSlice> getUniforms() {
        return Collections.emptyMap();
    }
}