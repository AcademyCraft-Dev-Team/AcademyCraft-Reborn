package org.academy.api.client.render.post;

import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.buffers.Std140Builder;
import com.mojang.blaze3d.buffers.Std140SizeCalculator;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.resource.RenderTargetDescriptor;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.FilterMode;
import com.mojang.blaze3d.textures.GpuTextureView;
import it.unimi.dsi.fastutil.ints.Int2ObjectLinkedOpenHashMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectMaps;
import org.academy.api.client.Render;
import org.academy.api.client.render.TextureBinding;
import org.academy.api.client.render.UniformBinding;
import org.joml.Vector2f;
import org.joml.Vector4f;
import org.jspecify.annotations.Nullable;
import org.lwjgl.system.MemoryStack;

import java.util.List;

public final class BlurEffect {
    private static final int MAX_GAUSSIAN_SAMPLES = 12;
    private static final Int2ObjectMap<GaussianSamples> SAMPLES_CACHE = Int2ObjectMaps.synchronize(new Int2ObjectLinkedOpenHashMap<>());
    private static final GpuBuffer blurUniformsBuffer;

    static {
        var device = RenderSystem.getDevice();
        var uboUsage = GpuBuffer.USAGE_UNIFORM | GpuBuffer.USAGE_COPY_DST;
        blurUniformsBuffer = device.createBuffer(() -> "Blur UBO", uboUsage, BlurUniforms.UBO_SIZE);
    }

    private record GaussianSamples(int sampleCount, Vector4f[] samples) {}

    private static GaussianSamples getGaussianSamples(int radius) {
        return SAMPLES_CACHE.computeIfAbsent(radius, key -> {
            var samples = new Vector4f[MAX_GAUSSIAN_SAMPLES];
            var weights = new float[key + 1];
            var totalWeight = 0.0f;
            var sigma = key / 2.0f;

            for (var i = 0; i <= key; i++) {
                weights[i] = (float) (Math.exp(-0.5 * (i * i) / (sigma * sigma)));
                totalWeight += (i == 0 ? 1.0f : 2.0f) * weights[i];
            }

            for (var i = 0; i < weights.length; i++) {
                weights[i] /= totalWeight;
            }

            var sampleCount = 0;
            samples[sampleCount++] = new Vector4f(0.0f, 0.0f, weights[0], 0.0f);

            for (var i = 1; i < key; i += 2) {
                var weight1 = weights[i];
                var weight2 = weights[i + 1];
                var total = weight1 + weight2;
                var offset = (i * weight1 + (i + 1.0f) * weight2) / total;
                samples[sampleCount++] = new Vector4f(offset, offset, total, 0.0f);
            }

            for (var i = sampleCount; i < MAX_GAUSSIAN_SAMPLES; i++) {
                samples[i] = new Vector4f();
            }

            return new GaussianSamples(sampleCount, samples);
        });
    }

    public static void close() {
        blurUniformsBuffer.close();
    }

    /**
     * 应用高斯模糊喵
     *
     * @param width 采样宽度喵
     * @param height 采样高度喵
     * @param sampler 采样目标喵
     * @param output 输出目标喵
     * @param depth 模板喵
     * @param radius 模糊半径喵
     */
    public static void apply(
            int width, int height,
            GpuTextureView sampler,
            GpuTextureView output,
            @Nullable GpuTextureView depth,
            float radius
    ) {
        if (radius < 0.1f) return;

        var resourcePool = Render.Buffers.getResourcePool();
        var desc = new RenderTargetDescriptor(width, height, false, 0);

        RenderTarget swapTarget = null;

        try {
            swapTarget = resourcePool.acquire(desc);

            var swap = swapTarget.getColorTextureView();

            if (swap == null) return;

            var blurUboSlice = blurUniformsBuffer.slice();
            var gpuSampler = RenderSystem.getSamplerCache().getClampToEdge(FilterMode.LINEAR);
            var textures = List.of(new TextureBinding("DiffuseSampler", sampler, gpuSampler));
            var uniforms = List.of(new UniformBinding("BlurInfo", blurUboSlice));

            var vec2 = new Vector2f(width, height);
            writeBlurUniforms(vec2, 1.0F, 0.0F, radius);
            Render.runBlitPass(
                    swap, depth,
                    false, false,
                    Render.RenderPipelines.CUTOUT_GAUSSIAN_BLUR,
                    Render.Buffers.getInstance().getFullScreenQuadVBNDC(),
                    textures, uniforms
            );
            Render.runBlitPass(
                    swap, depth,
                    false, false,
                    Render.RenderPipelines.BLIT_SCREEN_WITHOUT_BLEND_INVERSE_CUTOUT,
                    Render.Buffers.getInstance().getFullScreenQuadVBNDC(),
                    textures, List.of()
            );
            writeBlurUniforms(vec2, 0.0F, 1.0F, radius);
            Render.runBlitPass(
                    output, depth,
                    false, false,
                    Render.RenderPipelines.CUTOUT_GAUSSIAN_BLUR,
                    Render.Buffers.getInstance().getFullScreenQuadVBNDC(),
                    List.of(new TextureBinding("DiffuseSampler", swap, gpuSampler)), uniforms
            );
        } finally {
            if (swapTarget != null) resourcePool.release(desc, swapTarget);
        }
    }

    private static void writeBlurUniforms(Vector2f outSize, float dirX, float dirY, float radius) {
        try (var memoryStack = MemoryStack.stackPush()) {
            var samples = getGaussianSamples((int) Math.ceil(radius));
            var builder = Std140Builder.onStack(memoryStack, BlurUniforms.UBO_SIZE);
            new BlurUniforms(outSize, new Vector2f(dirX, dirY), samples.sampleCount, samples.samples).write(builder);
            var byteBuffer = builder.get();
            RenderSystem.getDevice().createCommandEncoder().writeToBuffer(blurUniformsBuffer.slice(), byteBuffer);
        }
    }

    private record BlurUniforms(Vector2f outSize, Vector2f blurDir, int sampleCount, Vector4f[] samples) {
        public static final int UBO_SIZE;

        static {
            var calculator = new Std140SizeCalculator().putVec2().putVec2().putInt();
            for (var i = 0; i < MAX_GAUSSIAN_SAMPLES; i++) {
                calculator.putVec4();
            }
            UBO_SIZE = calculator.get();
        }

        public void write(Std140Builder builder) {
            builder.putVec2(outSize).putVec2(blurDir).putInt(sampleCount);
            for (var sample : samples) {
                builder.putVec4(sample);
            }
        }
    }

    private BlurEffect() {}
}