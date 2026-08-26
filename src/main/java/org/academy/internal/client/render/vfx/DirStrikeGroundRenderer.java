package org.academy.internal.client.render.vfx;

import com.mojang.blaze3d.PrimitiveTopology;
import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.systems.GpuDevice;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.FilterMode;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.chunk.ChunkSectionLayer;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.texture.TextureAtlas;
import org.academy.api.client.compatibility.IrisIntegration;
import org.academy.api.client.render.vfx.VfxPipelines;
import org.academy.api.client.render.vfx.VfxRenderContext;
import org.academy.api.client.render.vfx.VfxRenderer;
import org.jspecify.annotations.Nullable;
import org.lwjgl.BufferUtils;

import java.nio.ByteBuffer;
import java.util.List;
import java.util.Optional;
import java.util.OptionalDouble;

public final class DirStrikeGroundRenderer implements VfxRenderer<DirStrikeGroundData> {
    private static final int INITIAL_VERTICES = 8192;
    private static final int GPU_VERTEX_STRIDE = (3 + 2 + 4) * Float.BYTES + 2 * Short.BYTES;

    private @Nullable GpuBuffer vertexBuffer;
    private @Nullable ByteBuffer vertexData;
    private int capacityVertices;

    @Override
    public void init(GpuDevice device) {
        vertexBuffer = device.createBuffer(
                () -> "VFX Dir Strike Ground",
                GpuBuffer.USAGE_VERTEX | GpuBuffer.USAGE_COPY_DST,
                (long) INITIAL_VERTICES * GPU_VERTEX_STRIDE
        );
        capacityVertices = INITIAL_VERTICES;
    }

    @Override
    public void render(VfxRenderContext ctx, List<? extends DirStrikeGroundData> data) {
        if (IrisIntegration.isShaderPackInUse() || data.isEmpty() || vertexBuffer == null) return;
        var color = ctx.mainColor();
        var depth = ctx.mainDepth();
        if (color == null || depth == null) return;

        var totalVertices = 0;
        var totalBytes = 0L;
        for (var item : data) {
            totalVertices += item.vertexCount();
            totalBytes += (long) item.vertexCount() * GPU_VERTEX_STRIDE;
        }
        if (totalVertices == 0) return;

        if (totalBytes > (long) capacityVertices * GPU_VERTEX_STRIDE) {
            grow(totalVertices);
        }
        if (vertexData == null || vertexData.capacity() < totalBytes) {
            vertexData = BufferUtils.createByteBuffer(Math.toIntExact(totalBytes));
        }

        var camera = ctx.cameraPos();
        vertexData.clear();
        for (var item : data) {
            var vertices = item.vertices().duplicate().order(item.vertices().order());
            vertices.limit(vertices.position() + item.vertexCount() * DirStrikeGroundData.VERTEX_STRIDE);
            while (vertices.hasRemaining()) {
                var x = vertices.getFloat() - camera.x;
                var y = vertices.getFloat() - camera.y;
                var z = vertices.getFloat() - camera.z;
                vertexData.putFloat(x);
                vertexData.putFloat(y);
                vertexData.putFloat(z);
                vertexData.putFloat(vertices.getFloat());
                vertexData.putFloat(vertices.getFloat());
                vertexData.putFloat(vertices.getFloat());
                vertexData.putFloat(vertices.getFloat());
                vertexData.putFloat(vertices.getFloat());
                vertexData.putFloat(vertices.getFloat());
                vertexData.putShort(vertices.getShort());
                vertexData.putShort(vertices.getShort());
                vertices.position(vertices.position() + 4 * Byte.BYTES);
            }
        }
        vertexData.flip();
        ctx.device().createCommandEncoder().writeToBuffer(vertexBuffer.slice(0, totalBytes), vertexData);

        var encoder = ctx.device().createCommandEncoder();
        try (var renderPass = encoder.createRenderPass(
                () -> "VFX Dir Strike Ground", color, Optional.empty(), depth, OptionalDouble.empty()
        )) {
            renderPass.setPipeline(VfxPipelines.BLOCK_MESH);
            renderPass.setUniform("Projection", ctx.projectionUniform());
            var transform = RenderSystem.getDynamicUniforms().writeTransform(ctx.viewRotationMatrix());
            renderPass.setUniform("DynamicTransforms", transform);
            var texture = Minecraft.getInstance().getTextureManager().getTexture(TextureAtlas.LOCATION_BLOCKS);
            renderPass.bindTexture("Sampler0", texture.getTextureView(), texture.getSampler());
            renderPass.bindTexture(
                    "Sampler2",
                    Minecraft.getInstance().gameRenderer.lightmap(),
                    RenderSystem.getSamplerCache().getClampToEdge(FilterMode.LINEAR)
            );
            renderPass.setVertexBuffer(0, vertexBuffer.slice(0, totalBytes));
            var quadCount = totalVertices / 4;
            var indexCount = quadCount * 6;
            var sequential = RenderSystem.getSequentialBuffer(PrimitiveTopology.QUADS);
            renderPass.setIndexBuffer(sequential.getBuffer(indexCount), sequential.type());
            renderPass.drawIndexed(indexCount, 1, 0, 0, 0);
        }
    }

    @Override
    public void submitWorldGeometry(VfxRenderContext ctx, PoseStack poseStack,
                                    SubmitNodeCollector output,
                                    List<? extends DirStrikeGroundData> data) {
        if (!IrisIntegration.isShaderPackInUse() || data.isEmpty()) return;

        var camera = ctx.cameraPos();
        var worldPose = new PoseStack();
        worldPose.last().set(poseStack.last());
        worldPose.translate(-camera.x, -camera.y, -camera.z);
        for (var layer : ChunkSectionLayer.values()) {
            if (!hasVertices(data, layer)) continue;
            output.submitCustomGeometry(
                    worldPose,
                    shaderPackRenderType(layer),
                    (pose, buffer) -> {
                        for (var item : data) {
                            if (item.layer() == layer && item.vertexCount() > 0) {
                                putShaderPackVertices(pose, buffer, item);
                            }
                        }
                    }
            );
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
                () -> "VFX Dir Strike Ground",
                GpuBuffer.USAGE_VERTEX | GpuBuffer.USAGE_COPY_DST,
                (long) newCapacity * GPU_VERTEX_STRIDE
        );
        capacityVertices = newCapacity;
        oldBuffer.close();
    }

    private static RenderType shaderPackRenderType(ChunkSectionLayer layer) {
        return switch (layer) {
            case SOLID -> RenderTypes.solidMovingBlock();
            case CUTOUT -> RenderTypes.cutoutMovingBlock();
            case TRANSLUCENT -> RenderTypes.translucentMovingBlock();
        };
    }

    private static boolean hasVertices(List<? extends DirStrikeGroundData> data,
                                       ChunkSectionLayer layer) {
        for (var item : data) {
            if (item.layer() == layer && item.vertexCount() > 0) return true;
        }
        return false;
    }

    private static void putShaderPackVertices(PoseStack.Pose pose, VertexConsumer output,
                                              DirStrikeGroundData data) {
        var vertices = data.vertices().duplicate().order(data.vertices().order());
        vertices.limit(vertices.position() + data.vertexCount() * DirStrikeGroundData.VERTEX_STRIDE);
        for (var index = 0; index < data.vertexCount(); index++) {
            var x = vertices.getFloat();
            var y = vertices.getFloat();
            var z = vertices.getFloat();
            var u = vertices.getFloat();
            var v = vertices.getFloat();
            var red = colorChannel(vertices.getFloat());
            var green = colorChannel(vertices.getFloat());
            var blue = colorChannel(vertices.getFloat());
            var alpha = colorChannel(vertices.getFloat());
            var blockLight = Short.toUnsignedInt(vertices.getShort());
            var skyLight = Short.toUnsignedInt(vertices.getShort());
            var normalX = DirStrikeGroundData.unpackNormal(vertices.get());
            var normalY = DirStrikeGroundData.unpackNormal(vertices.get());
            var normalZ = DirStrikeGroundData.unpackNormal(vertices.get());
            vertices.get();

            output.addVertex(pose, x, y, z)
                    .setColor(red, green, blue, alpha)
                    .setUv(u, v)
                    .setLight(blockLight | skyLight << 16)
                    .setNormal(pose, normalX, normalY, normalZ);
        }
    }

    private static int colorChannel(float value) {
        return Math.clamp(Math.round(value * 255.0f), 0, 255);
    }
}
