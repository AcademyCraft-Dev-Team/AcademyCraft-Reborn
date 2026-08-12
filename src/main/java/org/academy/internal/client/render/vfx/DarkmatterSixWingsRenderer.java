package org.academy.internal.client.render.vfx;

import com.mojang.blaze3d.PrimitiveTopology;
import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.systems.GpuDevice;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.Minecraft;
import org.academy.api.client.render.vfx.VfxPipelines;
import org.academy.api.client.render.vfx.VfxRenderContext;
import org.academy.api.client.render.vfx.VfxRenderer;
import org.academy.api.client.resources.R;
import org.jspecify.annotations.Nullable;
import org.lwjgl.BufferUtils;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.List;
import java.util.Optional;
import java.util.OptionalDouble;

public final class DarkmatterSixWingsRenderer implements VfxRenderer<DarkmatterSixWingsData> {
    private static final int INITIAL_VERTICES = 4096;

    private @Nullable GpuBuffer vertexBuffer;
    private @Nullable ByteBuffer vertexData;
    private int capacityVertices;

    @Override
    public void init(GpuDevice device) {
        vertexBuffer = device.createBuffer(
                () -> "VFX Darkmatter Six Wings",
                GpuBuffer.USAGE_VERTEX | GpuBuffer.USAGE_COPY_DST,
                (long) INITIAL_VERTICES * DarkmatterSixWingsVfx.VERTEX_STRIDE
        );
        capacityVertices = INITIAL_VERTICES;
    }

    @Override
    public void render(VfxRenderContext ctx, List<? extends DarkmatterSixWingsData> data) {
        if (data.isEmpty() || vertexBuffer == null) return;
        var color = ctx.mainColor();
        var depth = ctx.mainDepth();
        if (color == null || depth == null) return;

        var totalVertices = 0;
        var totalBytes = 0L;
        for (var item : data) {
            totalVertices += item.vertexCount();
            totalBytes += (long) item.vertexCount() * DarkmatterSixWingsVfx.VERTEX_STRIDE;
        }
        if (totalVertices == 0) return;

        if (totalBytes > (long) capacityVertices * DarkmatterSixWingsVfx.VERTEX_STRIDE) {
            grow(totalVertices);
        }
        if (vertexData == null || vertexData.capacity() < totalBytes) {
            vertexData = BufferUtils.createByteBuffer(Math.toIntExact(totalBytes));
        }

        var camera = ctx.cameraPos();
        vertexData.clear();
        for (var item : data) {
            var vertices = item.vertices().duplicate().order(ByteOrder.nativeOrder());
            var oldLimit = vertices.limit();
            vertices.limit(vertices.position() + item.vertexCount() * DarkmatterSixWingsVfx.VERTEX_STRIDE);
            while (vertices.hasRemaining()) {
                vertexData.putFloat(vertices.getFloat() - camera.x);
                vertexData.putFloat(vertices.getFloat() - camera.y);
                vertexData.putFloat(vertices.getFloat() - camera.z);
                vertexData.putFloat(vertices.getFloat());
                vertexData.putFloat(vertices.getFloat());
            }
            vertices.limit(oldLimit);
        }
        vertexData.flip();
        var neededBytes = (long) totalVertices * DarkmatterSixWingsVfx.VERTEX_STRIDE;
        ctx.device().createCommandEncoder().writeToBuffer(vertexBuffer.slice(0, neededBytes), vertexData);

        var encoder = ctx.device().createCommandEncoder();
        try (var renderPass = encoder.createRenderPass(
                () -> "VFX Darkmatter Six Wings", color, Optional.empty(), depth, OptionalDouble.empty()
        )) {
            renderPass.setPipeline(VfxPipelines.MODEL_MESH);
            renderPass.setUniform("Projection", ctx.projectionUniform());
            var transform = RenderSystem.getDynamicUniforms().writeTransform(ctx.viewRotationMatrix());
            renderPass.setUniform("DynamicTransforms", transform);
            var texture = Minecraft.getInstance().getTextureManager().getTexture(R.textures.darkmatter_six_wings_effect);
            renderPass.bindTexture("Sampler0", texture.getTextureView(), texture.getSampler());
            renderPass.setVertexBuffer(0, vertexBuffer.slice(0, neededBytes));
            var sequential = RenderSystem.getSequentialBuffer(PrimitiveTopology.TRIANGLES);
            renderPass.setIndexBuffer(sequential.getBuffer(totalVertices), sequential.type());
            renderPass.drawIndexed(totalVertices, 1, 0, 0, 0);
        }
    }

    @Override
    public void close() {
        if (vertexBuffer != null) {
            vertexBuffer.close();
            vertexBuffer = null;
        }
    }

    private void grow(int requiredVertices) {
        if (vertexBuffer == null) return;
        var newCapacity = Math.max(requiredVertices, capacityVertices * 2);
        var oldBuffer = vertexBuffer;
        vertexBuffer = RenderSystem.getDevice().createBuffer(
                () -> "VFX Darkmatter Six Wings",
                GpuBuffer.USAGE_VERTEX | GpuBuffer.USAGE_COPY_DST,
                (long) newCapacity * DarkmatterSixWingsVfx.VERTEX_STRIDE
        );
        capacityVertices = newCapacity;
        oldBuffer.close();
    }
}
