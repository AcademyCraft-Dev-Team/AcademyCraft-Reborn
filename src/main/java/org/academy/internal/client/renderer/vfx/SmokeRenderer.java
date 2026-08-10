package org.academy.internal.client.renderer.vfx;

import com.mojang.blaze3d.PrimitiveTopology;
import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.systems.GpuDevice;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.ByteBufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.Identifier;
import org.academy.AcademyCraft;
import org.academy.api.client.render.vfx.VfxPipelines;
import org.academy.api.client.render.vfx.VfxRenderContext;
import org.academy.api.client.render.vfx.VfxRenderer;
import org.jspecify.annotations.Nullable;
import org.lwjgl.BufferUtils;

import java.nio.ByteBuffer;
import java.util.List;
import java.util.Optional;
import java.util.OptionalDouble;

public final class SmokeRenderer implements VfxRenderer<SmokeData> {
    public static final Identifier TEXTURE = AcademyCraft.academy("textures/ability/generic/effect/smokes.png");
    private static final int INSTANCE_STRIDE = 3 * 4 + 4 + 4 + 4 * 4;
    private static final int INITIAL_INSTANCES = 128;

    private @Nullable GpuBuffer quadBuffer;
    private @Nullable GpuBuffer instanceBuffer;
    private @Nullable ByteBuffer instanceData;
    private int capacityInstances;

    private static ByteBuffer buildQuad() {
        try (var byteBufferBuilder = ByteBufferBuilder.exactlySized(DefaultVertexFormat.POSITION_TEX.getVertexSize() * 4)) {
            var builder = new BufferBuilder(byteBufferBuilder, PrimitiveTopology.QUADS, DefaultVertexFormat.POSITION_TEX);
            builder.addVertex(0.0f, 0.0f, 0.0f).setUv(0.0f, 0.0f);
            builder.addVertex(1.0f, 0.0f, 0.0f).setUv(1.0f, 0.0f);
            builder.addVertex(1.0f, 1.0f, 0.0f).setUv(1.0f, 1.0f);
            builder.addVertex(0.0f, 1.0f, 0.0f).setUv(0.0f, 1.0f);
            try (var meshData = builder.buildOrThrow()) {
                return meshData.vertexBuffer();
            }
        }
    }

    @Override
    public void init(GpuDevice device) {
        quadBuffer = device.createBuffer(() -> "VFX Smoke Quad", GpuBuffer.USAGE_VERTEX, buildQuad());
        instanceBuffer = device.createBuffer(
                () -> "VFX Smoke Instances",
                GpuBuffer.USAGE_VERTEX | GpuBuffer.USAGE_COPY_DST,
                (long) INSTANCE_STRIDE * INITIAL_INSTANCES
        );
        capacityInstances = INITIAL_INSTANCES;
    }

    @Override
    public void render(VfxRenderContext ctx, List<? extends SmokeData> data) {
        if (data.isEmpty() || instanceBuffer == null || quadBuffer == null) return;
        var color = ctx.mainColor();
        var depth = ctx.mainDepth();
        if (color == null || depth == null) return;

        var instanceCount = data.size();
        var neededBytes = (long) INSTANCE_STRIDE * instanceCount;
        if (neededBytes > instanceBuffer.size()) {
            grow(instanceCount);
        }
        if (instanceData == null || instanceData.capacity() < neededBytes) {
            instanceData = BufferUtils.createByteBuffer(Math.toIntExact(neededBytes));
        }

        var cameraPos = ctx.cameraPos();
        instanceData.clear();
        for (var smoke : data) {
            instanceData.putFloat(smoke.pos().x - cameraPos.x);
            instanceData.putFloat(smoke.pos().y - cameraPos.y);
            instanceData.putFloat(smoke.pos().z - cameraPos.z);
            instanceData.putFloat(smoke.size());
            instanceData.putFloat(smoke.alpha());
            instanceData.putFloat(smoke.u0()).putFloat(smoke.v0()).putFloat(smoke.u1()).putFloat(smoke.v1());
        }
        instanceData.flip();
        var writeEncoder = ctx.device().createCommandEncoder();
        writeEncoder.writeToBuffer(instanceBuffer.slice(), instanceData);

        var passEncoder = ctx.device().createCommandEncoder();
        try (var renderPass = passEncoder.createRenderPass(
                () -> "VFX Smoke", color, Optional.empty(), depth, OptionalDouble.empty()
        )) {
            renderPass.setPipeline(VfxPipelines.TEX_BILLBOARD_TRANSLUCENT);
            var projection = RenderSystem.getProjectionMatrixBuffer();
            if (projection != null) renderPass.setUniform("Projection", projection);
            var transform = RenderSystem.getDynamicUniforms().writeTransform(RenderSystem.getModelViewMatrixCopy());
            renderPass.setUniform("DynamicTransforms", transform);

            var texture = Minecraft.getInstance().getTextureManager().getTexture(TEXTURE);
            renderPass.bindTexture("Sampler0", texture.getTextureView(), texture.getSampler());

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
        var newCapacity = Math.max(requiredInstances, capacityInstances * 2);
        var oldBuffer = instanceBuffer;
        instanceBuffer = RenderSystem.getDevice().createBuffer(
                () -> "VFX Smoke Instances",
                GpuBuffer.USAGE_VERTEX | GpuBuffer.USAGE_COPY_DST,
                (long) INSTANCE_STRIDE * newCapacity
        );
        capacityInstances = newCapacity;
        oldBuffer.close();
    }
}
