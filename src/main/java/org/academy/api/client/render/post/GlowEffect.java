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
import org.academy.api.client.render.Render;
import org.academy.api.client.render.TextureBinding;
import org.academy.api.client.render.UniformBinding;
import org.academy.api.client.render.vfx.VfxManager;
import org.joml.Vector2f;
import org.joml.Vector4f;
import org.jspecify.annotations.Nullable;
import org.lwjgl.system.MemoryStack;

import java.util.List;

import static org.academy.api.client.render.Render.BlurUniforms.getBlurUniformsBuffer;
import static org.academy.api.client.render.Render.BlurUniforms.writeBlurUniforms;

public final class GlowEffect {
    public static final OutputTarget GLOW_TARGET = new OutputTarget(
            "glow_target",
            () -> getInstance().getInput()
    );
    private static final Phase BEFORE = new Phase("before");
    private static final Phase AFTER = new Phase("after");
    @Nullable
    private static GlowEffect instance;
    private static boolean hasBeenUsed;
    private final GpuBuffer glowUniformsBuffer;
    private @Nullable RenderTarget input;
    private @Nullable RenderTargetDescriptor inputDescriptor;

    {
        var device = RenderSystem.getDevice();
        var uboUsage = GpuBuffer.USAGE_UNIFORM | GpuBuffer.USAGE_COPY_DST;
        glowUniformsBuffer = device.createBuffer(() -> "Glow Blend UBO", uboUsage, GlowUniforms.UBO_SIZE);
    }

    private GlowEffect() {
    }

    public static void init() {
        instance = new GlowEffect();
    }

    public static GlowEffect getInstance() {
        if (instance == null) {
            throw new IllegalStateException("GlowEffect has not been initialized.");
        }
        return instance;
    }

    public static Phase getBefore() {
        hasBeenUsed = true;
        return BEFORE;
    }

    public static Phase getAfter() {
        hasBeenUsed = true;
        return AFTER;
    }

    public static void onResize() {
        if (instance != null) instance.releaseInput();
    }

    public void close() {
        releaseInput();
        glowUniformsBuffer.close();
        BEFORE.close();
        AFTER.close();
    }

    public RenderTarget getInput() {
        hasBeenUsed = true;
        return ensureInput(Minecraft.getInstance().gameRenderer.mainRenderTarget());
    }

    private RenderTarget ensureInput(RenderTarget mainRenderTarget) {
        if (input != null
                && input.width == mainRenderTarget.width
                && input.height == mainRenderTarget.height
                && input.useDepth == mainRenderTarget.useDepth
                && input.useStencil == mainRenderTarget.useStencil) {
            return input;
        }

        releaseInput();
        inputDescriptor = new RenderTargetDescriptor(
                mainRenderTarget.width,
                mainRenderTarget.height,
                mainRenderTarget.useDepth,
                mainRenderTarget.useStencil,
                new Vector4f(0),
                GpuFormat.RGBA8_UNORM
        );
        input = Render.Buffers.getResourcePool().acquire(inputDescriptor);
        input.copyDepthFrom(mainRenderTarget);
        return input;
    }

    private void releaseInput() {
        if (input != null && inputDescriptor != null) {
            Render.Buffers.getResourcePool().release(inputDescriptor, input);
        }
        input = null;
        inputDescriptor = null;
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

    private void writeGlowUniforms(float radius, float intensity) {
        try (var memoryStack = MemoryStack.stackPush()) {
            var builder = Std140Builder.onStack(memoryStack, GlowUniforms.UBO_SIZE);
            new GlowUniforms(radius, intensity).write(builder);
            var byteBuffer = builder.get();
            RenderSystem.getDevice().createCommandEncoder().writeToBuffer(glowUniformsBuffer.slice(), byteBuffer);
        }
    }

    public void process() {
        if (!hasBeenUsed && !VfxManager.INSTANCE.hasGlowData()) return;

        var mc = Minecraft.getInstance();
        var mainRenderTarget = mc.gameRenderer.mainRenderTarget();
        var width = mainRenderTarget.width;
        var height = mainRenderTarget.height;
        var resourcePool = Render.Buffers.getResourcePool();

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

            input = ensureInput(mainRenderTarget);
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

            if (VfxManager.INSTANCE.hasGlowData()) {
                VfxManager.INSTANCE.renderGlowFrame(inputView, input.getDepthTextureView());
            }

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

            writeGlowUniforms(1.0f, 1.0f);
            var blendSamplers = List.of(
                    new TextureBinding("Sampler0", main, sampler),
                    new TextureBinding("BlurTexture1", pongHalfView, sampler),
                    new TextureBinding("BlurTexture2", pongQuarterView, sampler),
                    new TextureBinding("BlurTexture3", pongEighthView, sampler)
            );
            Render.runBlitPass(
                    main, Render.RenderPipelines.GLOW_BLEND,
                    Render.Buffers.getInstance().getFSQuadVBNDC(),
                    blendSamplers,
                    List.of(new UniformBinding("GlowInfo", glowUniformsBuffer.slice())),
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
            releaseInput();
            if (ping != null) {
                if (ping.width == width / 2) resourcePool.release(descHalf, ping);
                else if (ping.width == width / 4) resourcePool.release(descQuarter, ping);
                else resourcePool.release(descEighth, ping);
            }
            if (pongHalf != null) resourcePool.release(descHalf, pongHalf);
            if (pongQuarter != null) resourcePool.release(descQuarter, pongQuarter);
            if (pongEighth != null) resourcePool.release(descEighth, pongEighth);
            hasBeenUsed = false;
        }
    }

    public record GlowUniforms(float radius, float intensity) {
        public static final int UBO_SIZE = new Std140SizeCalculator().putFloat().putFloat().get();

        public void write(Std140Builder builder) {
            builder.putFloat(radius).putFloat(intensity);
        }
    }
}
