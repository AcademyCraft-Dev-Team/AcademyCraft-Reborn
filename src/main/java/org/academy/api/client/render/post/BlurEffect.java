package org.academy.api.client.render.post;

import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.resource.RenderTargetDescriptor;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.FilterMode;
import com.mojang.blaze3d.textures.GpuTextureView;
import org.academy.api.client.Render;
import org.academy.api.client.render.TextureBinding;
import org.academy.api.client.render.UniformBinding;
import org.joml.Vector2f;

import java.util.List;

import static org.academy.api.client.Render.BlurUniforms.getBlurUniformsBuffer;
import static org.academy.api.client.Render.BlurUniforms.writeBlurUniforms;

public final class BlurEffect {
    /**
     * 应用高斯模糊喵
     *
     * @param width   采样宽度喵
     * @param height  采样高度喵
     * @param sampler 采样目标喵
     * @param output  输出目标喵
     * @param depth   模板喵
     * @param radius  模糊半径喵
     */
    public static void apply(
            int width, int height,
            GpuTextureView sampler,
            GpuTextureView output,
            GpuTextureView depth,
            int radius
    ) {
        if (radius < 1) return;

        var resourcePool = Render.Buffers.getResourcePool();
        var desc = new RenderTargetDescriptor(width, height, false, 0);

        RenderTarget swapTarget = null;

        try {
            swapTarget = resourcePool.acquire(desc);

            var swap = swapTarget.getColorTextureView();

            if (swap == null) return;

            var blurUboSlice = getBlurUniformsBuffer().slice();
            var gpuSampler = RenderSystem.getSamplerCache().getClampToEdge(FilterMode.LINEAR);
            var textures = List.of(new TextureBinding("DiffuseSampler", sampler, gpuSampler));
            var uniforms = List.of(new UniformBinding("BlurInfo", blurUboSlice));

            var vec2 = new Vector2f(width, height);
            writeBlurUniforms(vec2, 1.0F, 0.0F, radius);
            Render.runBlitPass(
                    swap, depth,
                    false, false,
                    Render.RenderPipelines.CUTOUT_GAUSSIAN_BLUR,
                    Render.Buffers.getInstance().getFSQuadVBNDC(),
                    textures, uniforms
            );
            Render.runBlitPass(
                    swap, depth,
                    false, false,
                    Render.RenderPipelines.BLIT_SCREEN_WITHOUT_BLEND_INVERSE_CUTOUT,
                    Render.Buffers.getInstance().getFSQuadVBNDC(),
                    textures, List.of()
            );
            writeBlurUniforms(vec2, 0.0F, 1.0F, radius);
            Render.runBlitPass(
                    output, depth,
                    false, false,
                    Render.RenderPipelines.CUTOUT_GAUSSIAN_BLUR,
                    Render.Buffers.getInstance().getFSQuadVBNDC(),
                    List.of(new TextureBinding("DiffuseSampler", swap, gpuSampler)), uniforms
            );
        } finally {
            if (swapTarget != null) resourcePool.release(desc, swapTarget);
        }
    }

    private BlurEffect() {
    }
}