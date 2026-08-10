package org.academy.internal.client.renderer.vfx;

import com.mojang.blaze3d.PrimitiveTopology;
import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.systems.GpuDevice;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.ByteBufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import net.minecraft.client.Minecraft;
import org.academy.api.client.render.vfx.VfxPipelines;
import org.academy.api.client.render.vfx.VfxRenderContext;
import org.academy.api.client.render.vfx.VfxRenderer;
import org.academy.api.client.resources.R;
import org.academy.api.client.util.VertexUtil;
import org.jspecify.annotations.Nullable;

import java.nio.ByteBuffer;
import java.util.List;
import java.util.Optional;
import java.util.OptionalDouble;

public final class PlasmaCloudRenderer implements VfxRenderer<PlasmaCloudData> {
    private static final int INSTANCE_STRIDE = 64;
    private static final int RING_SEGMENTS = 12;
    private static final int RING_INDEX_COUNT = RING_SEGMENTS * 6;
    private static final int INITIAL_INSTANCES = 128;
    private @Nullable GpuBuffer ringBuffer;
    private @Nullable GpuBuffer instanceBuffer;
    private int capacityInstances;

    private static ByteBuffer buildRing() {
        var ring = VertexUtil.Ring.getVerticalVertexBuffer(1.0f, 1.0f, RING_SEGMENTS);
        try (var byteBufferBuilder = ByteBufferBuilder.exactlySized(
                DefaultVertexFormat.POSITION_TEX.getVertexSize() * RING_SEGMENTS * 4)) {
            var builder = new BufferBuilder(
                    byteBufferBuilder,
                    PrimitiveTopology.QUADS,
                    DefaultVertexFormat.POSITION_TEX
            );
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
        ringBuffer = device.createBuffer(
                () -> "VFX Plasma Cloud Ring",
                GpuBuffer.USAGE_VERTEX,
                buildRing()
        );
        instanceBuffer = device.createBuffer(
                () -> "VFX Plasma Cloud Instances",
                GpuBuffer.USAGE_VERTEX | GpuBuffer.USAGE_COPY_DST,
                (long) INSTANCE_STRIDE * INITIAL_INSTANCES
        );
        capacityInstances = INITIAL_INSTANCES;
    }

    @Override
    public void render(VfxRenderContext context, List<? extends PlasmaCloudData> data) {
        if (data.isEmpty() || ringBuffer == null || instanceBuffer == null) return;
        var color = context.mainColor();
        var depth = context.mainDepth();
        if (color == null || depth == null) return;

        long totalBytes = 0;
        var instanceCount = 0;
        for (var item : data) {
            totalBytes += item.instances().remaining();
            instanceCount += item.instances().remaining() / INSTANCE_STRIDE;
        }
        if (instanceCount == 0) return;
        if (totalBytes > instanceBuffer.size()) grow(instanceCount);

        var writer = context.device().createCommandEncoder();
        long offset = 0;
        for (var item : data) {
            var instances = item.instances();
            writer.writeToBuffer(instanceBuffer.slice(offset, instances.remaining()), instances);
            offset += instances.remaining();
        }

        var encoder = context.device().createCommandEncoder();
        try (var renderPass = encoder.createRenderPass(
                () -> "VFX Plasma Cloud",
                color,
                Optional.empty(),
                depth,
                OptionalDouble.empty()
        )) {
            renderPass.setPipeline(VfxPipelines.TEX_RING_TRANSLUCENT);
            var projection = RenderSystem.getProjectionMatrixBuffer();
            if (projection != null) renderPass.setUniform("Projection", projection);
            var transform = RenderSystem.getDynamicUniforms()
                    .writeTransform(RenderSystem.getModelViewMatrixCopy());
            renderPass.setUniform("DynamicTransforms", transform);
            var texture = Minecraft.getInstance().getTextureManager()
                    .getTexture(R.textures.plasma_generation_cloud);
            renderPass.bindTexture("Sampler0", texture.getTextureView(), texture.getSampler());
            renderPass.setVertexBuffer(0, ringBuffer.slice());
            renderPass.setVertexBuffer(1, instanceBuffer.slice(0, totalBytes));
            var sequential = RenderSystem.getSequentialBuffer(PrimitiveTopology.QUADS);
            renderPass.setIndexBuffer(sequential.getBuffer(RING_INDEX_COUNT), sequential.type());
            renderPass.drawIndexed(RING_INDEX_COUNT, instanceCount, 0, 0, 0);
        }
    }

    private void grow(int requiredInstances) {
        if (instanceBuffer == null) return;
        var old = instanceBuffer;
        capacityInstances = Math.max(requiredInstances, capacityInstances * 2);
        instanceBuffer = RenderSystem.getDevice().createBuffer(
                () -> "VFX Plasma Cloud Instances",
                GpuBuffer.USAGE_VERTEX | GpuBuffer.USAGE_COPY_DST,
                (long) INSTANCE_STRIDE * capacityInstances
        );
        old.close();
    }

    @Override
    public void close() {
        if (ringBuffer != null) ringBuffer.close();
        if (instanceBuffer != null) instanceBuffer.close();
        ringBuffer = null;
        instanceBuffer = null;
    }
}
