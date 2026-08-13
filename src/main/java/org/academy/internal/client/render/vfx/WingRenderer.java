package org.academy.internal.client.render.vfx;

import com.mojang.blaze3d.PrimitiveTopology;
import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.systems.GpuDevice;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.ByteBufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.Identifier;
import org.academy.api.client.render.vfx.VfxPipelines;
import org.academy.api.client.render.vfx.VfxRenderContext;
import org.academy.api.client.render.vfx.VfxRenderer;
import org.academy.api.client.resources.R;
import org.academy.api.client.util.VertexUtil;
import org.jspecify.annotations.Nullable;

import java.nio.ByteBuffer;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalDouble;

public final class WingRenderer implements VfxRenderer<WingData> {
    private static final int INSTANCE_STRIDE = 64;
    private static final int RING_SEGMENTS = 16;
    private static final int RING_INDEX_COUNT = RING_SEGMENTS * 6;
    private static final int INITIAL_INSTANCES = 1024;

    private @Nullable GpuBuffer ringBuffer;
    private @Nullable GpuBuffer instanceBuffer;
    private int capacityInstances;

    private static ByteBuffer buildRing() {
        var ring = VertexUtil.Ring.getVerticalVertexBuffer(1.0f, 1.0f, RING_SEGMENTS);
        try (var byteBufferBuilder = ByteBufferBuilder.exactlySized(DefaultVertexFormat.POSITION_TEX.getVertexSize() * RING_SEGMENTS * 4)) {
            var builder = new BufferBuilder(byteBufferBuilder, PrimitiveTopology.QUADS, DefaultVertexFormat.POSITION_TEX);
            for (var segment = 0; segment < RING_SEGMENTS; segment++) {
                var v0 = ring[segment][0];
                var v1 = ring[segment][1];
                var v2 = ring[segment][2];
                var v3 = ring[segment][3];
                builder.addVertex(v0[0], v0[1], v0[2]).setUv(v0[3], 0.0f);
                builder.addVertex(v1[0], v1[1], v1[2]).setUv(v1[3], 0.0f);
                builder.addVertex(v2[0], v2[1], v2[2]).setUv(v2[3], 1.0f);
                builder.addVertex(v3[0], v3[1], v3[2]).setUv(v3[3], 1.0f);
            }
            try (var meshData = builder.buildOrThrow()) {
                return meshData.vertexBuffer();
            }
        }
    }

    @Override
    public void init(GpuDevice device) {
        ringBuffer = device.createBuffer(() -> "VFX Wing Ring", GpuBuffer.USAGE_VERTEX, buildRing());
        instanceBuffer = device.createBuffer(
                () -> "VFX Wing Instances",
                GpuBuffer.USAGE_VERTEX | GpuBuffer.USAGE_COPY_DST,
                (long) INSTANCE_STRIDE * INITIAL_INSTANCES
        );
        capacityInstances = INITIAL_INSTANCES;
    }

    @Override
    public void render(VfxRenderContext ctx, List<? extends WingData> data) {
        if (data.isEmpty() || instanceBuffer == null || ringBuffer == null) return;
        var color = ctx.mainColor();
        var depth = ctx.mainDepth();
        if (color == null || depth == null) return;

        var totalBytes = 0L;
        var instanceCount = 0;
        for (var item : data) {
            var instances = item.instances();
            totalBytes += instances.remaining();
            instanceCount += instances.remaining() / INSTANCE_STRIDE;
        }
        if (instanceCount == 0) return;

        if (totalBytes > instanceBuffer.size()) {
            grow(instanceCount);
        }

        var writeEncoder = ctx.device().createCommandEncoder();
        var offset = 0L;
        for (var item : data) {
            var instances = item.instances();
            writeEncoder.writeToBuffer(instanceBuffer.slice(offset, instances.remaining()), instances);
            offset += instances.remaining();
        }

        var passEncoder = ctx.device().createCommandEncoder();
        var textureManager = Minecraft.getInstance().getTextureManager();
        try (var renderPass = passEncoder.createRenderPass(
                () -> "VFX Wing", color, Optional.empty(), depth, OptionalDouble.empty()
        )) {
            var projection = ctx.projectionUniform();
            var transform = RenderSystem.getDynamicUniforms().writeTransform(ctx.viewRotationMatrix());
            var sequential = RenderSystem.getSequentialBuffer(PrimitiveTopology.QUADS);
            var drawOffset = 0L;
            for (var item : data) {
                var itemBytes = item.instances().remaining();
                var itemCount = itemBytes / INSTANCE_STRIDE;
                if (itemCount == 0) {
                    drawOffset += itemBytes;
                    continue;
                }
                if (item.kind() == WingKind.PLATINUM && item.layer() == WingData.Layer.STABLE) {
                    renderPass.setPipeline(VfxPipelines.WING_RING_COSMIC);
                    renderPass.setUniform("Projection", projection);
                    renderPass.setUniform("DynamicTransforms", transform);
                    renderPass.setUniform("Globals", Objects.requireNonNull(RenderSystem.getGlobalSettingsUniform()));
                    var mask = textureManager.getTexture(R.textures.white_wing);
                    renderPass.bindTexture("Sampler0", mask.getTextureView(), mask.getSampler());
                    var starfield = textureManager.getTexture(R.textures.platinum_wing_starfield);
                    renderPass.bindTexture("Sampler1", starfield.getTextureView(), starfield.getSampler());
                } else {
                    renderPass.setPipeline(item.layer() == WingData.Layer.ASCENSION
                            ? VfxPipelines.TEX_RING_ADDITIVE
                            : VfxPipelines.TEX_RING_TRANSLUCENT);
                    renderPass.setUniform("Projection", projection);
                    renderPass.setUniform("DynamicTransforms", transform);
                    var textureId = item.layer() == WingData.Layer.ASCENSION
                            ? R.textures.white_wing_ascension
                            : tornadoTexture(item.kind());
                    var texture = textureManager.getTexture(textureId);
                    renderPass.bindTexture("Sampler0", texture.getTextureView(), texture.getSampler());
                }
                renderPass.setVertexBuffer(0, ringBuffer.slice());
                renderPass.setVertexBuffer(1, instanceBuffer.slice(drawOffset, itemBytes));
                renderPass.setIndexBuffer(sequential.getBuffer(RING_INDEX_COUNT), sequential.type());
                renderPass.drawIndexed(RING_INDEX_COUNT, itemCount, 0, 0, 0);
                drawOffset += itemBytes;
            }
        }
    }

    @Override
    public void close() {
        if (ringBuffer != null) {
            ringBuffer.close();
            ringBuffer = null;
        }
        if (instanceBuffer != null) {
            instanceBuffer.close();
            instanceBuffer = null;
        }
    }

    private void grow(int requiredInstances) {
        if (instanceBuffer == null) return;
        var newCapacity = Math.max(requiredInstances, capacityInstances * 2);
        var oldBuffer = instanceBuffer;
        instanceBuffer = RenderSystem.getDevice().createBuffer(
                () -> "VFX Wing Instances",
                GpuBuffer.USAGE_VERTEX | GpuBuffer.USAGE_COPY_DST,
                (long) INSTANCE_STRIDE * newCapacity
        );
        capacityInstances = newCapacity;
        oldBuffer.close();
    }

    private static Identifier tornadoTexture(WingKind kind) {
        return switch (kind) {
            case STORM -> R.textures.ability.accelerator.skill.storm_wing.effect.tornado_ring;
            case BLACK -> R.textures.black_wing;
            case WHITE, PLATINUM -> R.textures.white_wing;
        };
    }
}
