package org.academy.api.client.render.vfx.renderer;

import com.mojang.blaze3d.GpuFormat;
import com.mojang.blaze3d.PrimitiveTopology;
import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.systems.GpuDevice;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.ByteBufferBuilder;
import com.mojang.blaze3d.vertex.VertexFormat;
import org.academy.api.client.render.vfx.VfxPipelines;
import org.academy.api.client.render.vfx.VfxRenderContext;
import org.academy.api.client.render.vfx.VfxRenderer;
import org.academy.api.client.render.vfx.data.ParticleData;
import org.jspecify.annotations.Nullable;
import org.lwjgl.BufferUtils;

import java.nio.ByteBuffer;
import java.util.List;
import java.util.Optional;
import java.util.OptionalDouble;

public final class ParticleRenderer implements VfxRenderer<ParticleData> {
    private static final int INSTANCE_STRIDE = 3 * 4 + 4 + 4 * 4;
    private static final int INITIAL_INSTANCES = 4096;

    private @Nullable GpuBuffer quadBuffer;
    private @Nullable GpuBuffer instanceBuffer;
    private @Nullable ByteBuffer instanceData;

    private static ByteBuffer buildQuad() {
        var format = VertexFormat.builder(0).addAttribute("Position", GpuFormat.RGB32_FLOAT).build();
        try (var byteBufferBuilder = ByteBufferBuilder.exactlySized(format.getVertexSize() * 4)) {
            var builder = new BufferBuilder(byteBufferBuilder, PrimitiveTopology.QUADS, format);
            builder.addVertex(0.0f, 0.0f, 0.0f);
            builder.addVertex(1.0f, 0.0f, 0.0f);
            builder.addVertex(1.0f, 1.0f, 0.0f);
            builder.addVertex(0.0f, 1.0f, 0.0f);
            try (var meshData = builder.buildOrThrow()) {
                return meshData.vertexBuffer();
            }
        }
    }

    @Override
    public void init(GpuDevice device) {
        quadBuffer = device.createBuffer(() -> "VFX Particle Quad", GpuBuffer.USAGE_VERTEX, buildQuad());
        instanceBuffer = device.createBuffer(
                () -> "VFX Particle Instances",
                GpuBuffer.USAGE_VERTEX | GpuBuffer.USAGE_COPY_DST,
                (long) INSTANCE_STRIDE * INITIAL_INSTANCES
        );
    }

    @Override
    public void render(VfxRenderContext ctx, List<? extends ParticleData> data) {
        if (data.isEmpty() || instanceBuffer == null || quadBuffer == null) return;
        var color = ctx.mainColor();
        var depth = ctx.mainDepth();
        if (color == null || depth == null) return;

        var instanceCount = data.size();
        var neededBytes = (long) INSTANCE_STRIDE * instanceCount;
        if (neededBytes > instanceBuffer.size()) {
            grow(instanceCount);
        }

        var cameraPos = ctx.cameraPos();
        var writeEncoder = ctx.device().createCommandEncoder();
        if (instanceData == null || instanceData.capacity() < neededBytes) {
            instanceData = BufferUtils.createByteBuffer(Math.toIntExact(neededBytes));
        }
        instanceData.clear();
        for (var particle : data) {
            instanceData.putFloat(particle.pos().x - cameraPos.x);
            instanceData.putFloat(particle.pos().y - cameraPos.y);
            instanceData.putFloat(particle.pos().z - cameraPos.z);
            instanceData.putFloat(particle.size());
            instanceData.putFloat(particle.r()).putFloat(particle.g()).putFloat(particle.b()).putFloat(particle.a());
        }
        instanceData.flip();
        writeEncoder.writeToBuffer(instanceBuffer.slice(), instanceData);

        var passEncoder = ctx.device().createCommandEncoder();
        try (var renderPass = passEncoder.createRenderPass(
                () -> "VFX Particles", color, Optional.empty(), depth, OptionalDouble.empty()
        )) {
            renderPass.setPipeline(VfxPipelines.PARTICLE_ADDITIVE);
            renderPass.setUniform("Projection", ctx.projectionUniform());
            var transform = RenderSystem.getDynamicUniforms().writeTransform(ctx.viewRotationMatrix());
            renderPass.setUniform("DynamicTransforms", transform);

            renderPass.setVertexBuffer(0, quadBuffer.slice());
            renderPass.setVertexBuffer(1, instanceBuffer.slice(0, neededBytes));

            var sequential = RenderSystem.getSequentialBuffer(PrimitiveTopology.QUADS);
            renderPass.setIndexBuffer(sequential.getBuffer(6), sequential.type());
            renderPass.drawIndexed(6, instanceCount, 0, 0, 0);
        }
    }

    @Override
    public void close() {
        if (quadBuffer != null) {
            quadBuffer.close();
            quadBuffer = null;
        }
        if (instanceBuffer != null) {
            instanceBuffer.close();
            instanceBuffer = null;
        }
    }

    private void grow(int requiredInstances) {
        if (instanceBuffer == null) return;
        var newCapacity = Math.max(requiredInstances, (int) (instanceBuffer.size() / INSTANCE_STRIDE) * 2);
        var oldBuffer = instanceBuffer;
        instanceBuffer = RenderSystem.getDevice().createBuffer(
                () -> "VFX Particle Instances",
                GpuBuffer.USAGE_VERTEX | GpuBuffer.USAGE_COPY_DST,
                (long) INSTANCE_STRIDE * newCapacity
        );
        oldBuffer.close();
    }
}
