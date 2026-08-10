package org.academy.internal.client.render.vfx;

import com.mojang.blaze3d.PrimitiveTopology;
import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.systems.GpuDevice;
import com.mojang.blaze3d.systems.RenderSystem;
import org.academy.api.client.render.vfx.VfxPipelines;
import org.academy.api.client.render.vfx.VfxRenderContext;
import org.academy.api.client.render.vfx.VfxRenderer;
import org.jspecify.annotations.Nullable;
import org.lwjgl.BufferUtils;

import java.nio.ByteBuffer;
import java.util.List;
import java.util.Optional;
import java.util.OptionalDouble;

public final class ColorMeshRenderer implements VfxRenderer<ColorMeshData> {
    private static final int INITIAL_VERTICES = 1024;

    private @Nullable GpuBuffer vertexBuffer;
    private @Nullable ByteBuffer vertexData;
    private int capacityVertices;

    @Override
    public void init(GpuDevice device) {
        vertexBuffer = device.createBuffer(
                () -> "VFX Color Mesh",
                GpuBuffer.USAGE_VERTEX | GpuBuffer.USAGE_COPY_DST,
                (long) INITIAL_VERTICES * ColorMeshData.VERTEX_STRIDE
        );
        capacityVertices = INITIAL_VERTICES;
    }

    @Override
    public void render(VfxRenderContext ctx, List<? extends ColorMeshData> data) {
        if (data.isEmpty() || vertexBuffer == null) return;
        var color = ctx.mainColor();
        var depth = ctx.mainDepth();
        if (color == null || depth == null) return;

        var totalVertices = 0;
        var totalBytes = 0L;
        for (var item : data) {
            totalVertices += item.vertexCount();
            totalBytes += (long) item.vertexCount() * ColorMeshData.VERTEX_STRIDE;
        }
        if (totalVertices == 0) return;

        if (totalBytes > (long) capacityVertices * ColorMeshData.VERTEX_STRIDE) {
            grow(totalVertices);
        }
        if (vertexData == null || vertexData.capacity() < totalBytes) {
            vertexData = BufferUtils.createByteBuffer(Math.toIntExact(totalBytes));
        }

        vertexData.clear();
        for (var item : data) {
            var vertices = item.vertices();
            var oldLimit = vertices.limit();
            vertices.limit(vertices.position() + item.vertexCount() * ColorMeshData.VERTEX_STRIDE);
            vertexData.put(vertices);
            vertices.limit(oldLimit);
        }
        vertexData.flip();
        ctx.device().createCommandEncoder().writeToBuffer(vertexBuffer.slice(0, totalBytes), vertexData);

        var encoder = ctx.device().createCommandEncoder();
        try (var renderPass = encoder.createRenderPass(
                () -> "VFX Color Mesh", color, Optional.empty(), depth, OptionalDouble.empty()
        )) {
            renderPass.setPipeline(VfxPipelines.COLOR_MESH);
            var projection = RenderSystem.getProjectionMatrixBuffer();
            if (projection != null) renderPass.setUniform("Projection", projection);
            var transform = RenderSystem.getDynamicUniforms().writeTransform(RenderSystem.getModelViewMatrixCopy());
            renderPass.setUniform("DynamicTransforms", transform);
            renderPass.setVertexBuffer(0, vertexBuffer.slice(0, totalBytes));
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
                () -> "VFX Color Mesh",
                GpuBuffer.USAGE_VERTEX | GpuBuffer.USAGE_COPY_DST,
                (long) newCapacity * ColorMeshData.VERTEX_STRIDE
        );
        capacityVertices = newCapacity;
        oldBuffer.close();
    }
}
