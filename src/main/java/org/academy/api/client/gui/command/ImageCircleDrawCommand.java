package org.academy.api.client.gui.command;

import com.mojang.blaze3d.textures.GpuSampler;
import com.mojang.blaze3d.textures.GpuTextureView;
import org.academy.api.client.Render;
import org.academy.api.client.render.TextureBinding;

import java.util.List;

public class ImageCircleDrawCommand extends PosTexColorRectDrawCommand {
    protected final GpuTextureView texture;
    protected final GpuSampler sampler;

    public ImageCircleDrawCommand(
            GpuTextureView texture,
            GpuSampler sampler,
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
        super(Render.RenderPipelines.IMAGE_CIRCLE,
                width, height,
                u0, v0, u1, v1,
                red, green, blue, alpha,
                List.of(new TextureBinding("Sampler0", texture, sampler)),
                List.of()
        );
        this.texture = texture;
        this.sampler = sampler;
    }
}