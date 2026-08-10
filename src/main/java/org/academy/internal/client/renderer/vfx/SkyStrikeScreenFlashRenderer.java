package org.academy.internal.client.renderer.vfx;

import com.mojang.blaze3d.PrimitiveTopology;
import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.systems.GpuDevice;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.ByteBufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import org.academy.api.client.render.vfx.VfxPipelines;
import org.academy.api.client.render.vfx.VfxRenderContext;
import org.academy.api.client.render.vfx.VfxRenderer;
import org.jspecify.annotations.Nullable;
import org.lwjgl.BufferUtils;

import java.util.List;
import java.util.Optional;
import java.util.OptionalDouble;

public final class SkyStrikeScreenFlashRenderer implements VfxRenderer<SkyStrikeScreenFlashData> {
    private @Nullable GpuBuffer quadBuffer;
    private @Nullable GpuBuffer colorBuffer;

    @Override
    public void init(GpuDevice device) {
        try (var bytes = ByteBufferBuilder.exactlySized(DefaultVertexFormat.POSITION.getVertexSize() * 4)) {
            var builder = new BufferBuilder(bytes, PrimitiveTopology.QUADS, DefaultVertexFormat.POSITION);
            builder.addVertex(0, 0, 0);
            builder.addVertex(1, 0, 0);
            builder.addVertex(1, 1, 0);
            builder.addVertex(0, 1, 0);
            try (var mesh = builder.buildOrThrow()) {
                quadBuffer = device.createBuffer(() -> "VFX Sky Strike Screen Quad",
                        GpuBuffer.USAGE_VERTEX, mesh.vertexBuffer());
            }
        }
        colorBuffer = device.createBuffer(
                () -> "VFX Sky Strike Screen Color",
                GpuBuffer.USAGE_VERTEX | GpuBuffer.USAGE_COPY_DST,
                4L * Float.BYTES
        );
    }

    @Override
    public void render(VfxRenderContext context, List<? extends SkyStrikeScreenFlashData> data) {
        if (data.isEmpty() || quadBuffer == null || colorBuffer == null) return;
        var target = context.mainColor();
        var depth = context.mainDepth();
        if (target == null || depth == null) return;

        var sum = 0.0f;
        var cap = 0.0f;
        for (var flash : data) {
            if (Float.isFinite(flash.alpha())) sum += Math.max(0.0f, flash.alpha());
            if (Float.isFinite(flash.cap())) cap = Math.max(cap, flash.cap());
        }
        var alpha = Math.min(sum, cap);
        if (alpha <= 0.001f) return;

        var color = BufferUtils.createByteBuffer(4 * Float.BYTES);
        color.putFloat(0.80f).putFloat(0.93f).putFloat(1.0f).putFloat(alpha).flip();
        context.device().createCommandEncoder().writeToBuffer(colorBuffer.slice(), color);

        var encoder = context.device().createCommandEncoder();
        try (var pass = encoder.createRenderPass(
                () -> "VFX Sky Strike Screen Flash",
                target,
                Optional.empty(),
                depth,
                OptionalDouble.empty()
        )) {
            pass.setPipeline(VfxPipelines.SCREEN_FLASH);
            pass.setVertexBuffer(0, quadBuffer.slice());
            pass.setVertexBuffer(1, colorBuffer.slice());
            var sequential = RenderSystem.getSequentialBuffer(PrimitiveTopology.QUADS);
            pass.setIndexBuffer(sequential.getBuffer(6), sequential.type());
            pass.drawIndexed(6, 1, 0, 0, 0);
        }
    }

    @Override
    public void close() {
        if (quadBuffer != null) quadBuffer.close();
        if (colorBuffer != null) colorBuffer.close();
        quadBuffer = null;
        colorBuffer = null;
    }
}
