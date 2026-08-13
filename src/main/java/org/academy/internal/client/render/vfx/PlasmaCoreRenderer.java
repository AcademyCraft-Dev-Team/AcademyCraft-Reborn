package org.academy.internal.client.render.vfx;

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
import org.jspecify.annotations.Nullable;
import org.lwjgl.BufferUtils;

import java.nio.ByteBuffer;
import java.util.List;
import java.util.Optional;
import java.util.OptionalDouble;

public final class PlasmaCoreRenderer implements VfxRenderer<PlasmaCoreData> {
    private static final int INSTANCE_STRIDE = 36;
    private static final int INITIAL_INSTANCES = 32;
    private @Nullable GpuBuffer quadBuffer;
    private @Nullable GpuBuffer instanceBuffer;
    private @Nullable ByteBuffer instanceData;
    private int capacityInstances;

    private static ByteBuffer buildQuad() {
        try (var byteBufferBuilder = ByteBufferBuilder.exactlySized(
                DefaultVertexFormat.POSITION_TEX.getVertexSize() * 4)) {
            var builder = new BufferBuilder(
                    byteBufferBuilder,
                    PrimitiveTopology.QUADS,
                    DefaultVertexFormat.POSITION_TEX
            );
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
        quadBuffer = device.createBuffer(
                () -> "VFX Plasma Core Quad",
                GpuBuffer.USAGE_VERTEX,
                buildQuad()
        );
        instanceBuffer = device.createBuffer(
                () -> "VFX Plasma Core Instances",
                GpuBuffer.USAGE_VERTEX | GpuBuffer.USAGE_COPY_DST,
                (long) INSTANCE_STRIDE * INITIAL_INSTANCES
        );
        capacityInstances = INITIAL_INSTANCES;
    }

    @Override
    public void render(VfxRenderContext context, List<? extends PlasmaCoreData> data) {
        if (data.isEmpty() || quadBuffer == null || instanceBuffer == null) return;
        var color = context.bloomInputColor();
        var depth = context.bloomInputDepth();
        if (color == null || depth == null) return;
        var neededBytes = (long) INSTANCE_STRIDE * data.size();
        if (neededBytes > instanceBuffer.size()) grow(data.size());
        if (instanceData == null || instanceData.capacity() < neededBytes) {
            instanceData = BufferUtils.createByteBuffer(Math.toIntExact(neededBytes));
        }
        var camera = context.cameraPos();
        instanceData.clear();
        for (var core : data) {
            instanceData.putFloat(core.pos().x - camera.x);
            instanceData.putFloat(core.pos().y - camera.y);
            instanceData.putFloat(core.pos().z - camera.z);
            instanceData.putFloat(core.size());
            instanceData.putFloat(core.alpha());
            instanceData.putFloat(0.0f).putFloat(0.0f).putFloat(1.0f).putFloat(1.0f);
        }
        instanceData.flip();
        context.device().createCommandEncoder()
                .writeToBuffer(instanceBuffer.slice(0, neededBytes), instanceData);

        var encoder = context.device().createCommandEncoder();
        try (var renderPass = encoder.createRenderPass(
                () -> "VFX Plasma Core",
                color,
                Optional.empty(),
                depth,
                OptionalDouble.empty()
        )) {
            renderPass.setPipeline(VfxPipelines.TEX_BILLBOARD_TRANSLUCENT);
            renderPass.setUniform("Projection", context.projectionUniform());
            var transform = RenderSystem.getDynamicUniforms().writeTransform(context.viewRotationMatrix());
            renderPass.setUniform("DynamicTransforms", transform);
            var texture = Minecraft.getInstance().getTextureManager()
                    .getTexture(R.textures.plasma_generation_effect);
            renderPass.bindTexture("Sampler0", texture.getTextureView(), texture.getSampler());
            renderPass.setVertexBuffer(0, quadBuffer.slice());
            renderPass.setVertexBuffer(1, instanceBuffer.slice(0, neededBytes));
            var sequential = RenderSystem.getSequentialBuffer(PrimitiveTopology.QUADS);
            renderPass.setIndexBuffer(sequential.getBuffer(6), sequential.type());
            renderPass.drawIndexed(6, data.size(), 0, 0, 0);
        }
    }

    private void grow(int requiredInstances) {
        if (instanceBuffer == null) return;
        var old = instanceBuffer;
        capacityInstances = Math.max(requiredInstances, capacityInstances * 2);
        instanceBuffer = RenderSystem.getDevice().createBuffer(
                () -> "VFX Plasma Core Instances",
                GpuBuffer.USAGE_VERTEX | GpuBuffer.USAGE_COPY_DST,
                (long) INSTANCE_STRIDE * capacityInstances
        );
        old.close();
    }

    @Override
    public void close() {
        if (quadBuffer != null) quadBuffer.close();
        if (instanceBuffer != null) instanceBuffer.close();
        quadBuffer = null;
        instanceBuffer = null;
    }
}
