package org.academy.api.client.render.post;

import com.mojang.blaze3d.GpuFormat;
import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.buffers.Std140Builder;
import com.mojang.blaze3d.buffers.Std140SizeCalculator;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.resource.RenderTargetDescriptor;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.FilterMode;
import com.mojang.blaze3d.textures.GpuTextureView;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.rendertype.OutputTarget;
import org.academy.api.client.Render;
import org.academy.api.client.render.TextureBinding;
import org.academy.api.client.render.UniformBinding;
import org.joml.Vector2f;
import org.joml.Vector4f;
import org.jspecify.annotations.Nullable;
import org.lwjgl.system.MemoryStack;

import java.util.List;

import static org.academy.api.client.Render.BlurUniforms.getBlurUniformsBuffer;
import static org.academy.api.client.Render.BlurUniforms.writeBlurUniforms;

public final class BloomEffect {
    @Nullable
    private static BloomEffect instance;
    private static final Phase BEFORE = new Phase("before");
    private static final Phase AFTER = new Phase("after");

    public static final OutputTarget BLOOM_TARGET = new OutputTarget(
            "bloom_target",
            () -> getInstance().getInput()
    );

    private static boolean hasBeenUsed;

    private @Nullable RenderTarget input;
    private final GpuBuffer bloomUniformsBuffer;

    {
        var device = RenderSystem.getDevice();
        var uboUsage = GpuBuffer.USAGE_UNIFORM | GpuBuffer.USAGE_COPY_DST;
        bloomUniformsBuffer = device.createBuffer(() -> "Bloom Blend UBO", uboUsage, BloomUniforms.UBO_SIZE);
    }

    private BloomEffect() {
    }

    public static void init() {
        instance = new BloomEffect();
    }

    public static BloomEffect getInstance() {
        if (instance == null) {
            throw new IllegalStateException("BloomEffect has not been initialized.");
        }
        return instance;
    }

    public void close() {
        bloomUniformsBuffer.close();
        BEFORE.close();
        AFTER.close();
    }

    public @Nullable RenderTarget getInput() {
        hasBeenUsed = true;
        return input;
    }

    public static Phase getBefore() {
        hasBeenUsed = true;
        return BEFORE;
    }

    public static Phase getAfter() {
        hasBeenUsed = true;
        return AFTER;
    }

    private void runBlurPass(GpuTextureView output, GpuTextureView input, Vector2f outSize, float dirX, float dirY, int radius) {
        writeBlurUniforms(outSize, dirX, dirY, radius);
        var blurUboSlice = getBlurUniformsBuffer().slice();
        var textures = List.of(
                new TextureBinding("Sampler0", input, RenderSystem.getSamplerCache().getClampToEdge(FilterMode.LINEAR))
        );
        var uniforms = List.of(
                new UniformBinding("BlurInfo", blurUboSlice)
        );
        Render.runBlitPass(output, Render.RenderPipelines.GAUSSIAN_BLUR, Render.Buffers.getInstance().getFSQuadVBNDC(), textures, uniforms, true);
    }

    private void writeBloomUniforms(float radius, float intensity) {
        try (var memoryStack = MemoryStack.stackPush()) {
            var builder = Std140Builder.onStack(memoryStack, BloomUniforms.UBO_SIZE);
            new BloomUniforms(radius, intensity).write(builder);
            var byteBuffer = builder.get();
            RenderSystem.getDevice().createCommandEncoder().writeToBuffer(bloomUniformsBuffer.slice(), byteBuffer);
        }
    }

    public void process() {
        if (!hasBeenUsed) return;

        var mc = Minecraft.getInstance();
        var mainRenderTarget = mc.gameRenderer.mainRenderTarget();
        var width = mainRenderTarget.width;
        var height = mainRenderTarget.height;
        var resourcePool = Render.Buffers.getResourcePool();

        var descInput = new RenderTargetDescriptor(
                width, height, mainRenderTarget.useDepth, mainRenderTarget.useStencil, new Vector4f(0), GpuFormat.RGBA8_UNORM
        );
        var descHalf = new RenderTargetDescriptor(
                width / 2, height / 2, false, new Vector4f(0), GpuFormat.RGBA8_UNORM
        );
        var descQuarter = new RenderTargetDescriptor(
                width / 4, height / 4, false, new Vector4f(0), GpuFormat.RGBA8_UNORM
        );
        var descEighth = new RenderTargetDescriptor(
                width / 8, height / 8, false, new Vector4f(0), GpuFormat.RGBA8_UNORM
        );

        RenderTarget pongHalf = null, pongQuarter = null, pongEighth = null;
        RenderTarget ping = null;

        try {
            var scene = PostEffect.MAIN_SCENE.getColorTextureView();
            var main = mainRenderTarget.getColorTextureView();

            input = resourcePool.acquire(descInput);
            var inputView = input.getColorTextureView();

            if (scene == null || main == null || inputView == null) return;

            input.copyDepthFrom(mainRenderTarget);
            BEFORE.draw();
            var sampler = RenderSystem.getSamplerCache().getClampToEdge(FilterMode.LINEAR);
            var textures = List.of(new TextureBinding("Sampler0", inputView, sampler));
            Render.runBlitPass(
                    main, Render.RenderPipelines.BLIT_SCREEN_PREMULTIPLIED_ALPHA,
                    Render.Buffers.getInstance().getFSQuadVBNDC(),
                    textures, List.of(),
                    false
            );
            AFTER.draw();

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
                    new TextureBinding("Sampler0", main, sampler),
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
                    List.of(new TextureBinding("Sampler0", main, sampler)),
                    List.of(),
                    false
            );
            RenderSystem.getDevice().createCommandEncoder().clearColorTexture(inputView.texture(), new Vector4f(0));
        } finally {
            if (input != null) resourcePool.release(descInput, input);
            if (ping != null) {
                if (ping.width == width / 2) resourcePool.release(descHalf, ping);
                else if (ping.width == width / 4) resourcePool.release(descQuarter, ping);
                else resourcePool.release(descEighth, ping);
            }
            if (pongHalf != null) resourcePool.release(descHalf, pongHalf);
            if (pongQuarter != null) resourcePool.release(descQuarter, pongQuarter);
            if (pongEighth != null) resourcePool.release(descEighth, pongEighth);
        }
        hasBeenUsed = false;
    }

    public record BloomUniforms(float radius, float intensity) {
        public static final int UBO_SIZE = new Std140SizeCalculator().putFloat().putFloat().get();

        public void write(Std140Builder builder) {
            builder.putFloat(radius).putFloat(intensity);
        }
    }
}