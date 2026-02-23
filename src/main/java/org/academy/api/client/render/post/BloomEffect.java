package org.academy.api.client.render.post;

import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.buffers.Std140Builder;
import com.mojang.blaze3d.buffers.Std140SizeCalculator;
import com.mojang.blaze3d.framegraph.FrameGraphBuilder;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.pipeline.TextureTarget;
import com.mojang.blaze3d.resource.RenderTargetDescriptor;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.FilterMode;
import com.mojang.blaze3d.textures.GpuTextureView;
import com.mojang.blaze3d.vertex.ByteBufferBuilder;
import it.unimi.dsi.fastutil.ints.Int2ObjectLinkedOpenHashMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectMaps;
import it.unimi.dsi.fastutil.objects.Object2ObjectLinkedOpenHashMap;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.rendertype.OutputTarget;
import net.minecraft.client.renderer.rendertype.RenderType;
import org.academy.api.client.Render;
import org.academy.api.client.render.TextureBinding;
import org.academy.api.client.render.UniformBinding;
import org.joml.Vector2f;
import org.joml.Vector4f;
import org.jspecify.annotations.Nullable;
import org.lwjgl.system.MemoryStack;

import java.util.List;
import java.util.SequencedMap;

import static org.academy.api.client.render.post.PostEffect.MAIN_SCENE;

public final class BloomEffect {
    @Nullable
    private static BloomEffect instance;
    private static final int MAX_GAUSSIAN_SAMPLES = 12;
    private static final Int2ObjectMap<GaussianSamples> SAMPLES_CACHE =
            Int2ObjectMaps.synchronize(new Int2ObjectLinkedOpenHashMap<>());
    private static final SequencedMap<RenderType, ByteBufferBuilder> FIXED_BUFFERS =
            new Object2ObjectLinkedOpenHashMap<>();
    private static final MultiBufferSource.BufferSource BLIT_TO_MAIN_POST =
            PostEffect.createPostEffectPassBuffer(FIXED_BUFFERS);

    public static final OutputTarget BLOOM_TARGET = new OutputTarget(
            "bloom_target",
            () -> getInstance().getInput()
    );

    private static boolean hasBeenUsed;

    private final RenderTarget input;
    private final GpuBuffer blurUniformsBuffer;
    private final GpuBuffer bloomUniformsBuffer;

    {
        var mc = Minecraft.getInstance();
        var mainRenderTarget = mc.getMainRenderTarget();
        input = new TextureTarget(
                null, mainRenderTarget.width, mainRenderTarget.height,
                true, mainRenderTarget.useStencil
        );

        var device = RenderSystem.getDevice();
        var uboUsage = GpuBuffer.USAGE_UNIFORM | GpuBuffer.USAGE_COPY_DST;
        blurUniformsBuffer = device.createBuffer(() -> "Bloom Blur UBO", uboUsage, BlurUniforms.UBO_SIZE);
        bloomUniformsBuffer = device.createBuffer(() -> "Bloom Blend UBO", uboUsage, BloomUniforms.UBO_SIZE);
    }

    public static void init() {
        instance = new BloomEffect();
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

    public static void addFixedBuffer(RenderType type) {
        FIXED_BUFFERS.put(type, new ByteBufferBuilder(type.bufferSize()));
    }

    public static BloomEffect getInstance() {
        if (instance == null) {
            throw new IllegalStateException(
                    "BloomEffect has not been initialized."
            );
        }
        return instance;
    }

    public void close() {
        input.destroyBuffers();
        blurUniformsBuffer.close();
        bloomUniformsBuffer.close();
    }

    public static void resize(int width, int height) {
        if (instance != null) {
            instance.input.resize(width, height);
        }
    }

    public RenderTarget getInput() {
        hasBeenUsed = true;
        return input;
    }

    public static MultiBufferSource.BufferSource getBlitToMainPost() {
        hasBeenUsed = true;
        return BLIT_TO_MAIN_POST;
    }

    private void writeBlurUniforms(Vector2f outSize, float dirX, float dirY, int radius) {
        try (var memoryStack = MemoryStack.stackPush()) {
            var samples = getGaussianSamples(radius);
            var builder = Std140Builder.onStack(memoryStack, BlurUniforms.UBO_SIZE);
            new BlurUniforms(outSize, new Vector2f(dirX, dirY), samples.sampleCount, samples.samples).write(builder);
            var byteBuffer = builder.get();
            RenderSystem.getDevice().createCommandEncoder().writeToBuffer(blurUniformsBuffer.slice(), byteBuffer);
        }
    }

    private void runBlurPass(
            GpuTextureView output, GpuTextureView input,
            Vector2f outSize, float dirX, float dirY, int radius
    ) {
        writeBlurUniforms(outSize, dirX, dirY, radius);
        var blurUboSlice = blurUniformsBuffer.slice();

        var textures = List.of(
                new TextureBinding(
                        "DiffuseSampler",
                        input,
                        RenderSystem.getSamplerCache().getClampToEdge(FilterMode.LINEAR)
                )
        );
        var uniforms = List.of(
                new UniformBinding("BlurInfo", blurUboSlice)
        );
        Render.runBlitPass(
                output, Render.RenderPipelines.GAUSSIAN_BLUR,
                Render.Buffers.getInstance().getFSQuadVBNDC(),
                textures, uniforms,
                true
        );
    }

    private void writeBloomUniforms(float radius, float intensity) {
        try (var memoryStack = MemoryStack.stackPush()) {
            var builder = Std140Builder.onStack(memoryStack, BloomUniforms.UBO_SIZE);
            new BloomUniforms(radius, intensity).write(builder);
            var byteBuffer = builder.get();
            RenderSystem.getDevice().createCommandEncoder().writeToBuffer(bloomUniformsBuffer.slice(), byteBuffer);
        }
    }

    public void process(FrameGraphBuilder frameGraphBuilder) {
        if (!hasBeenUsed) return;

        var mc = Minecraft.getInstance();
        var levelRenderer = mc.levelRenderer;
        var bloom = frameGraphBuilder.addPass("bloom");
        levelRenderer.targets.main = bloom.readsAndWrites(levelRenderer.targets.main);
        bloom.executes(() -> {
            var mainRenderTarget = mc.getMainRenderTarget();
            var width = mainRenderTarget.width;
            var height = mainRenderTarget.height;
            var resourcePool = Render.Buffers.getResourcePool();

            var descHalf = new RenderTargetDescriptor(
                    width / 2, height / 2, false, 0
            );
            var descQuarter = new RenderTargetDescriptor(
                    width / 4, height / 4, false, 0
            );
            var descEighth = new RenderTargetDescriptor(
                    width / 8, height / 8, false, 0
            );

            RenderTarget pongHalf = null, pongQuarter = null, pongEighth = null;
            RenderTarget ping = null;

            try {
                var scene = MAIN_SCENE.getColorTextureView();
                var main = mainRenderTarget.getColorTextureView();
                var inputView = input.getColorTextureView();

                if (scene == null || main == null || inputView == null) return;

                input.copyDepthFrom(mainRenderTarget);
                var sampler = RenderSystem.getSamplerCache().getClampToEdge(FilterMode.LINEAR);
                var textures = List.of(new TextureBinding("DiffuseSampler", inputView, sampler));
                Render.runBlitPass(
                        main, Render.RenderPipelines.BLIT_SCREEN_WITH_BLEND,
                        Render.Buffers.getInstance().getFSQuadVBNDC(),
                        textures, List.of(),
                        false
                );
                BLIT_TO_MAIN_POST.endBatch();

                {
                    ping = resourcePool.acquire(descHalf);
                    pongHalf = resourcePool.acquire(descHalf);
                }

                var pingView = ping.getColorTextureView();
                var pongHalfView = pongHalf.getColorTextureView();
                if (pingView == null || pongHalfView == null) return;

                runBlurPass(
                        pingView, inputView,
                        new Vector2f(ping.width, ping.height), 1.0f, 0.0f, 4
                );
                runBlurPass(
                        pongHalfView, pingView,
                        new Vector2f(pongHalf.width, pongHalf.height), 0.0f, 1.0f, 4
                );
                resourcePool.release(descHalf, ping);
                ping = null;

                {
                    ping = resourcePool.acquire(descQuarter);
                    pongQuarter = resourcePool.acquire(descQuarter);
                }

                var pongQuarterView = pongQuarter.getColorTextureView();
                pingView = ping.getColorTextureView();
                pongHalfView = pongHalf.getColorTextureView();
                pongQuarterView = pongQuarter.getColorTextureView();
                if (pingView == null || pongHalfView == null || pongQuarterView == null) return;

                runBlurPass(
                        pingView, pongHalfView,
                        new Vector2f(ping.width, ping.height), 1.0f, 0.0f, 6
                );
                runBlurPass(
                        pongQuarterView, pingView,
                        new Vector2f(pongQuarter.width, pongQuarter.height), 0.0f, 1.0f, 6
                );
                resourcePool.release(descQuarter, ping);
                ping = null;

                {
                    ping = resourcePool.acquire(descEighth);
                    pongEighth = resourcePool.acquire(descEighth);
                }

                pingView = ping.getColorTextureView();
                var pongEighthView = pongEighth.getColorTextureView();
                if (pingView == null || pongEighthView == null) return;

                runBlurPass(
                        pingView, pongQuarterView,
                        new Vector2f(ping.width, ping.height), 1.0f, 0.0f, 8
                );
                runBlurPass(
                        pongEighthView, pingView,
                        new Vector2f(pongEighth.width, pongEighth.height), 0.0f, 1.0f, 8
                );
                resourcePool.release(descEighth, ping);
                ping = null;

                writeBloomUniforms(1.0f, 1.0f);
                var blendSamplers = List.of(
                        new TextureBinding("DiffuseSampler", main, sampler),
                        new TextureBinding("BlurTexture1", pongHalfView, sampler),
                        new TextureBinding("BlurTexture2", pongQuarterView, sampler),
                        new TextureBinding("BlurTexture3", pongEighthView, sampler)
                );
                Render.runBlitPass(
                        main, Render.RenderPipelines.BLOOM_BLEND,
                        Render.Buffers.getInstance().getFSQuadVBNDC(),
                        blendSamplers,
                        List.of(new UniformBinding("BloomInfo", bloomUniformsBuffer.slice())),
                        false
                );
                Render.runBlitPass(
                        scene, Render.RenderPipelines.BLIT_SCREEN_WITHOUT_BLEND,
                        Render.Buffers.getInstance().getFSQuadVBNDC(),
                        List.of(new TextureBinding("DiffuseSampler", main, sampler)),
                        List.of(),
                        false
                );
                RenderSystem.getDevice().createCommandEncoder().clearColorTexture(inputView.texture(), 0);
            } finally {
                if (ping != null) {
                    if (ping.width == width / 2) resourcePool.release(descHalf, ping);
                    else if (ping.width == width / 4) resourcePool.release(descQuarter, ping);
                    else resourcePool.release(descEighth, ping);
                }
                if (pongHalf != null) resourcePool.release(descHalf, pongHalf);
                if (pongQuarter != null) resourcePool.release(descQuarter, pongQuarter);
                if (pongEighth != null) resourcePool.release(descEighth, pongEighth);
            }
        });
        hasBeenUsed = false;
    }

    public record BlurUniforms(Vector2f outSize, Vector2f blurDir, int sampleCount, Vector4f[] samples) {
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

    public record BloomUniforms(float radius, float intensity) {
        public static final int UBO_SIZE = new Std140SizeCalculator().putFloat().putFloat().get();

        public void write(Std140Builder builder) {
            builder.putFloat(radius).putFloat(intensity);
        }
    }
}