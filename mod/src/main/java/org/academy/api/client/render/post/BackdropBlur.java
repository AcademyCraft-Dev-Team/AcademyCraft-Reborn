package org.academy.api.client.render.post;

import com.mojang.blaze3d.resource.RenderTargetDescriptor;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.renderpearl.api.GpuFormat;
import com.mojang.renderpearl.api.pipeline.RenderPipeline;
import com.mojang.renderpearl.api.textures.FilterMode;
import com.mojang.renderpearl.api.textures.GpuTextureView;
import org.academy.api.client.render.Render;
import org.academy.api.client.render.TextureBinding;
import org.academy.api.client.render.UniformBinding;
import org.joml.Vector2f;
import org.joml.Vector4f;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

public final class BackdropBlur {
    public static final float SIGMA_SCALE = 0.5f;
    public static final int MAX_LAYERS = 8;

    private BackdropBlur() {
    }

    public static float layerRadius(int level, float sigma) {
        return sigma * ((1 << level) - 1);
    }

    public static float radiusToLevel(float radius, float sigma) {
        if (radius <= 0f) return 0f;
        if (sigma <= 0f) return 0f;
        return (float) (Math.log(1f + radius / sigma) / Math.log(2));
    }

    public static int layersFor(float radius, float sigma) {
        var k = radiusToLevel(radius, sigma);
        if (k <= 0f) return 1;
        var lo = (int) Math.floor(k);
        var need = lo + 1 + (k > lo ? 1 : 0);
        return Math.clamp(need, 1, MAX_LAYERS);
    }

    public static float clampLevel(float level, int layerCount) {
        var max = layerCount - 1;
        if (level <= 0f) return 0f;
        return Math.min(level, max);
    }

    public static LevelSpan levelSpan(float radius, float sigma, int layerCount) {
        var k = clampLevel(radiusToLevel(radius, sigma), layerCount);
        var lo = (int) Math.floor(k);
        var frac = k - (float) Math.floor(k);
        var hi = frac <= 0f ? lo : Math.min(lo + 1, layerCount - 1);
        return new LevelSpan(lo, hi, frac);
    }

    public static List<Size> pyramidSize(int width, int height, int levelCount) {
        var sizes = new ArrayList<Size>(levelCount);
        var w = width;
        var h = height;
        for (var i = 0; i < levelCount; i++) {
            sizes.add(new Size(w, h));
            w = Math.max(1, w / 2);
            h = Math.max(1, h / 2);
        }
        return sizes;
    }

    public static void applyGaussian(
            GpuTextureView sampler,
            GpuTextureView output,
            @Nullable GpuTextureView depth,
            int width,
            int height,
            float radius
    ) {
        var desc = new RenderTargetDescriptor(
                width,
                height,
                new RenderTargetDescriptor.TextureProperties(new Vector4f(0f), GpuFormat.RGBA8_UNORM),
                null
        );
        var swap = Render.Buffers.getResourcePool().acquire(desc);
        try {
            var swapView = swap.getColorTextureView();
            if (swapView == null) return;
            var blurUboSlice = Render.BlurUniforms.getBlurUniformsBuffer().slice();
            var gpuSampler = RenderSystem.getSamplerCache().getClampToEdge(FilterMode.LINEAR);
            var textures = List.of(new TextureBinding("Sampler0", sampler, gpuSampler));
            var uniforms = List.of(new UniformBinding("BlurInfo", blurUboSlice));
            var vec2 = new Vector2f(width, height);

            RenderPipeline pipeline = depth != null
                    ? Render.RenderPipelines.CUTOUT_GAUSSIAN_BLUR
                    : Render.RenderPipelines.GAUSSIAN_BLUR;

            Render.BlurUniforms.writeBlurUniforms(vec2, 1f, 0f, radius);
            Render.runBlitPass(
                    swapView, depth, false, false, pipeline,
                    Render.Buffers.getInstance().getFSQuadVBNDC(), textures, uniforms
            );
            if (depth != null) {
                Render.runBlitPass(
                        swapView, depth, false, false,
                        Render.RenderPipelines.BLIT_SCREEN_WITHOUT_BLEND_INVERSE_CUTOUT,
                        Render.Buffers.getInstance().getFSQuadVBNDC(), textures, List.of()
                );
            }
            Render.BlurUniforms.writeBlurUniforms(vec2, 0f, 1f, radius);
            Render.runBlitPass(
                    output, depth, false, false, pipeline,
                    Render.Buffers.getInstance().getFSQuadVBNDC(),
                    List.of(new TextureBinding("Sampler0", swapView, gpuSampler)), uniforms
            );
        } finally {
            Render.Buffers.getResourcePool().release(desc, swap);
        }
    }

    public record LevelSpan(int lo, int hi, float frac) {
        public boolean isIdentity() {
            return lo == 0 && hi == 0;
        }
    }

    public record Size(int width, int height) {
    }
}
