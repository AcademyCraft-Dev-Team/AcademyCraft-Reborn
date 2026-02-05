package org.academy.api.client.gui.command;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.FilterMode;
import com.mojang.blaze3d.textures.GpuTextureView;
import com.mojang.blaze3d.vertex.VertexConsumer;
import org.academy.api.client.Render;
import org.academy.api.client.render.MsdfUniformData;
import org.academy.api.client.render.TextureBinding;
import org.academy.api.client.render.UniformPayload;
import org.joml.Matrix4f;
import org.joml.Vector4f;

import java.util.List;

public class GlyphDrawCommand extends DrawCommand {
    private final float x;
    private final float y;
    private final float quadWidth, quadHeight;
    private final float red, green, blue, alpha;
    private final float u0, v0, u1, v1;

    public GlyphDrawCommand(
            GpuTextureView textureView,
            float x,
            float y,
            float quadWidth, float quadHeight,
            float u0, float v0, float u1, float v1,
            float red, float green, float blue, float alpha,
            float range,
            float thickness
    ) {
        super(Render.RenderPipelines.MSDF_TEXT,
                List.of(new TextureBinding(
                                "Sampler0",
                                textureView,
                                RenderSystem.getSamplerCache().getClampToEdge(FilterMode.LINEAR)
                        )
                ),
                List.of(new UniformPayload<>(
                                "MsdfUniforms",
                                MsdfUniformData.class,
                                new MsdfUniformData(
                                        range,
                                        thickness,
                                        0.0f,
                                        new Vector4f(0, 0, 0, 0)
                                ),
                                MsdfUniformData.UBO_SIZE
                        )
                )
        );
        this.x = x;
        this.y = y;
        this.red = red;
        this.green = green;
        this.blue = blue;
        this.alpha = alpha;
        this.quadWidth = quadWidth;
        this.quadHeight = quadHeight;
        this.u0 = u0;
        this.u1 = u1;
        this.v0 = v0;
        this.v1 = v1;
    }

    @Override
    public void generateVertices(VertexConsumer consumer, Matrix4f pose) {
        var x0 = x;
        var y0 = y;
        var y1 = y + quadHeight;
        var x1 = x + quadWidth;

        consumer.addVertex(pose, x0, y0, 0).setUv(u0, v0).setColor(red, green, blue, alpha);
        consumer.addVertex(pose, x0, y1, 0).setUv(u0, v1).setColor(red, green, blue, alpha);
        consumer.addVertex(pose, x1, y1, 0).setUv(u1, v1).setColor(red, green, blue, alpha);
        consumer.addVertex(pose, x1, y0, 0).setUv(u1, v0).setColor(red, green, blue, alpha);
    }
}