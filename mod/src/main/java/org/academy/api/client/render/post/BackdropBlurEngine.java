package org.academy.api.client.render.post;

import com.mojang.blaze3d.buffers.Std140Builder;
import com.mojang.blaze3d.buffers.Std140SizeCalculator;
import com.mojang.blaze3d.pipeline.TextureTarget;
import com.mojang.blaze3d.resource.RenderTargetDescriptor;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.renderpearl.api.GpuFormat;
import com.mojang.renderpearl.api.buffers.GpuBuffer;
import com.mojang.renderpearl.api.pipeline.PrimitiveTopology;
import com.mojang.renderpearl.api.textures.FilterMode;
import com.mojang.renderpearl.api.textures.GpuTextureView;
import org.academy.api.client.gui.environment.UiEnvironment;
import org.academy.api.client.render.Render;
import org.academy.api.client.render.TextureBinding;
import org.academy.api.client.render.UniformBinding;
import org.joml.Vector2f;
import org.joml.Vector4f;
import org.jspecify.annotations.Nullable;
import org.lwjgl.system.MemoryStack;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;

public final class BackdropBlurEngine {
    private final @Nullable TextureTarget[] levels = new TextureTarget[BackdropBlur.MAX_LAYERS];
    @Nullable
    private GpuBuffer ubo;
    private int baseW = -1;
    private int baseH = -1;
    private int baseLayers = 0;

    private float sigma() {
        return BackdropBlur.SIGMA_SCALE * UiEnvironment.get().getGuiScale();
    }

    public void close() {
        for (var level : levels) if (level != null) level.destroyBuffers();
        Arrays.fill(levels, null);
        if (ubo != null) ubo.close();
        ubo = null;
        baseW = -1;
        baseH = -1;
        baseLayers = 0;
    }

    public void capture(GpuTextureView source, float maxRadius) {
        var count = Math.clamp(
                BackdropBlur.layersFor(maxRadius, sigma()),
                1,
                BackdropBlur.MAX_LAYERS
        );
        var env = UiEnvironment.get();
        ensureTargets(env.getPhysicalWidth(), env.getPhysicalHeight(), count);

        var l0 = colorView(levels[0]);
        if (l0 == null) return;
        Render.runBlitPass(
                l0, null, true, false,
                Render.RenderPipelines.BLIT_SCREEN_WITHOUT_BLEND,
                Render.Buffers.getInstance().getFSQuadVBNDC(),
                List.of(new TextureBinding(
                        "Sampler0", source,
                        RenderSystem.getSamplerCache().getClampToEdge(FilterMode.LINEAR)
                )),
                List.of()
        );

        var s = sigma();
        for (var i = 1; i < count; i++) {
            var prev = colorView(levels[i - 1]);
            var out = levels[i];
            if (prev == null || out == null) continue;
            blurDown(out, prev, s * i);
        }
    }

    public void fillRegion(
            GpuTextureView target,
            float x, float y, float w, float h,
            float radius,
            Vector4f tint
    ) {
        var env = UiEnvironment.get();
        var guiScale = env.getGuiScale();
        var physicalWidth = env.getPhysicalWidth();
        var physicalHeight = env.getPhysicalHeight();
        var rectWidth = w * guiScale;
        var rectHeight = h * guiScale;
        if (rectWidth <= 0f || rectHeight <= 0f) return;

        var xPx = x * guiScale;
        var yPx = physicalHeight - (y + h) * guiScale;
        var sx = Math.clamp((int) xPx, 0, physicalWidth);
        var sy = Math.clamp((int) yPx, 0, physicalHeight);
        var sw = Math.min((int) rectWidth, physicalWidth - sx);
        var sh = Math.min((int) rectHeight, physicalHeight - sy);
        if (sw <= 0 || sh <= 0) return;

        var s = sigma();
        var layerCount = BackdropBlur.layersFor(radius, s);
        if (layerCount > countActiveLevels()) return;
        var span = BackdropBlur.levelSpan(radius, s, layerCount);

        writeBackdropUniforms(
                xPx / physicalWidth,
                yPx / physicalHeight,
                rectWidth / physicalWidth,
                rectHeight / physicalHeight,
                tint,
                physicalWidth,
                physicalHeight,
                span.frac()
        );

        var sampler = RenderSystem.getSamplerCache().getClampToEdge(FilterMode.LINEAR);
        var lo = colorView(levels[span.lo()]);
        if (lo == null) return;
        var hi = colorView(levels[span.hi()]);
        if (hi == null) return;

        var encoder = RenderSystem.getDevice().createCommandEncoder();
        try (var rp = encoder.createRenderPass(
                () -> "Backdrop fillRegion -> " + target, target, Optional.empty()
        )) {
            rp.setPipeline(RenderSystem.getCompiledPipeline(Render.RenderPipelines.BACKDROP_SAMPLE));
            rp.setUniform("Sampler0", lo, sampler);
            rp.setUniform("Sampler1", hi, sampler);
            rp.setUniform("BackdropInfo", requireUbo().slice());
            rp.setVertexBuffer(0, Render.Buffers.getInstance().getFSQuadVBNDC().slice());
            var seq = RenderSystem.getSequentialBuffer(PrimitiveTopology.QUADS);
            rp.setIndexBuffer(seq.getBuffer(6), seq.type());
            rp.enableScissor(sx, sy, sw, sh);
            rp.drawIndexed(6, 1, 0, 0, 0);
            rp.disableScissor();
        }
    }

    private void blurDown(TextureTarget out, GpuTextureView source, float radius) {
        var w = out.width;
        var h = out.height;
        var desc = new RenderTargetDescriptor(
                w,
                h,
                new RenderTargetDescriptor.TextureProperties(new Vector4f(0f), GpuFormat.RGBA8_UNORM),
                null
        );
        var swap = Render.Buffers.getResourcePool().acquire(desc);
        try {
            var swapView = swap.getColorTextureView();
            if (swapView == null) return;
            var blurUboSlice = Render.BlurUniforms.getBlurUniformsBuffer().slice();
            var sampler = RenderSystem.getSamplerCache().getClampToEdge(FilterMode.LINEAR);
            var uniforms = List.of(new UniformBinding("BlurInfo", blurUboSlice));
            var outColor = out.getColorTextureView();
            if (outColor == null) return;

            Render.BlurUniforms.writeBlurUniforms(new Vector2f(w, h), 1f, 0f, radius);
            Render.runBlitPass(
                    swapView, null, true, false,
                    Render.RenderPipelines.GAUSSIAN_BLUR,
                    Render.Buffers.getInstance().getFSQuadVBNDC(),
                    List.of(new TextureBinding("Sampler0", source, sampler)), uniforms
            );
            Render.BlurUniforms.writeBlurUniforms(new Vector2f(w, h), 0f, 1f, radius);
            Render.runBlitPass(
                    outColor, null, true, false,
                    Render.RenderPipelines.GAUSSIAN_BLUR,
                    Render.Buffers.getInstance().getFSQuadVBNDC(),
                    List.of(new TextureBinding("Sampler0", swapView, sampler)), uniforms
            );
        } finally {
            Render.Buffers.getResourcePool().release(desc, swap);
        }
    }

    private int countActiveLevels() {
        return levels.length;
    }

    private void ensureTargets(int width, int height, int layerCount) {
        if (baseW == width && baseH == height && baseLayers >= layerCount) return;
        for (var level : levels) if (level != null) level.destroyBuffers();
        Arrays.fill(levels, null);
        var sizes = BackdropBlur.pyramidSize(width, height, layerCount);
        for (var i = 0; i < layerCount; i++) {
            var size = sizes.get(i);
            levels[i] = new TextureTarget(
                    "BackdropBlur-L" + i, size.width(), size.height(), GpuFormat.RGBA8_UNORM, null
            );
        }
        baseW = width;
        baseH = height;
        baseLayers = layerCount;
    }

    private GpuBuffer requireUbo() {
        if (ubo != null) return ubo;
        var uboUsage = GpuBuffer.USAGE_UNIFORM | GpuBuffer.USAGE_COPY_DST;
        var buffer = RenderSystem.getDevice().createBuffer(
                () -> "BackdropBlur UBO", uboUsage, BackdropUniforms.UBO_SIZE
        );
        ubo = buffer;
        return buffer;
    }

    private void writeBackdropUniforms(
            float rectX, float rectY, float rectW, float rectH,
            Vector4f tint,
            float outW, float outH,
            float levelLerp
    ) {
        var buffer = requireUbo();
        try (var stack = MemoryStack.stackPush()) {
            var builder = Std140Builder.onStack(stack, BackdropUniforms.UBO_SIZE);
            new BackdropUniforms(
                    new Vector4f(rectX, rectY, rectW, rectH),
                    tint,
                    new Vector2f(outW, outH),
                    levelLerp,
                    0f
            ).write(builder);
            RenderSystem.getDevice().createCommandEncoder().writeToBuffer(buffer.slice(), builder.get());
        }
    }

    @Nullable
    private static GpuTextureView colorView(@Nullable TextureTarget target) {
        return target == null ? null : target.getColorTextureView();
    }

    private record BackdropUniforms(
            Vector4f regionRect,
            Vector4f tint,
            Vector2f outSize,
            float levelLerp,
            float cornerRadius
    ) {
        static final int UBO_SIZE = new Std140SizeCalculator()
                .putVec4().putVec4().putVec2().putFloat().putFloat().get();

        void write(Std140Builder builder) {
            builder.putVec4(regionRect).putVec4(tint).putVec2(outSize)
                    .putFloat(levelLerp).putFloat(cornerRadius);
        }
    }
}
