package org.academy.internal.client.render.vfx;

import com.mojang.blaze3d.PrimitiveTopology;
import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.systems.GpuDevice;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.GpuTextureView;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.ByteBufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.Identifier;
import org.academy.api.client.render.vfx.VfxPipelines;
import org.academy.api.client.render.vfx.VfxRenderContext;
import org.academy.api.client.render.vfx.VfxRenderer;
import org.joml.Vector2f;
import org.joml.Vector3f;
import org.joml.Vector4f;
import org.jspecify.annotations.Nullable;
import org.lwjgl.BufferUtils;

import java.nio.ByteBuffer;
import java.util.*;

public final class SkyStrikeWorldRenderer implements VfxRenderer<SkyStrikeWorldData> {
    private static final int INSTANCE_STRIDE = 4 * (3 + 2 + 4) * Float.BYTES;
    private static final int INITIAL_QUADS = 16;
    private static final Vector2f UV0 = new Vector2f(0.0f, 1.0f);
    private static final Vector2f UV1 = new Vector2f(1.0f, 1.0f);
    private static final Vector2f UV2 = new Vector2f(1.0f, 0.0f);
    private static final Vector2f UV3 = new Vector2f(0.0f, 0.0f);

    private final boolean glow;
    private @Nullable GpuBuffer quadBuffer;
    private @Nullable GpuBuffer instanceBuffer;
    private @Nullable ByteBuffer instanceData;
    private int capacityQuads;

    public SkyStrikeWorldRenderer(boolean glow) {
        this.glow = glow;
    }

    private static void putCorner(
            ByteBuffer target,
            Vector3f position,
            Vector2f uv,
            Vector4f color,
            Vector3f camera
    ) {
        target.putFloat(position.x - camera.x);
        target.putFloat(position.y - camera.y);
        target.putFloat(position.z - camera.z);
        target.putFloat(uv.x).putFloat(uv.y);
        target.putFloat(color.x).putFloat(color.y).putFloat(color.z).putFloat(color.w);
    }

    @Override
    public void init(GpuDevice device) {
        try (var bytes = ByteBufferBuilder.exactlySized(DefaultVertexFormat.POSITION.getVertexSize() * 4)) {
            var builder = new BufferBuilder(bytes, PrimitiveTopology.QUADS, DefaultVertexFormat.POSITION);
            builder.addVertex(0, 0, 0);
            builder.addVertex(1, 0, 0);
            builder.addVertex(1, 1, 0);
            builder.addVertex(0, 1, 0);
            try (var mesh = builder.buildOrThrow()) {
                quadBuffer = device.createBuffer(this::label, GpuBuffer.USAGE_VERTEX, mesh.vertexBuffer());
            }
        }
        instanceBuffer = device.createBuffer(
                this::label,
                GpuBuffer.USAGE_VERTEX | GpuBuffer.USAGE_COPY_DST,
                (long) INSTANCE_STRIDE * INITIAL_QUADS
        );
        capacityQuads = INITIAL_QUADS;
    }

    @Override
    public void render(VfxRenderContext context, List<? extends SkyStrikeWorldData> data) {
        if (data.isEmpty() || quadBuffer == null || instanceBuffer == null) return;
        var colorTarget = glow ? context.bloomInputColor() : context.mainColor();
        var depthTarget = glow ? context.bloomInputDepth() : context.mainDepth();
        if (colorTarget == null || depthTarget == null) return;

        var grouped = new LinkedHashMap<Identifier, List<SkyStrikeWorldData>>();
        for (var item : data) {
            grouped.computeIfAbsent(item.texture(), _ -> new ArrayList<>()).add(item);
        }
        for (var entry : grouped.entrySet()) {
            renderBatch(context, colorTarget, depthTarget, entry.getKey(), entry.getValue());
        }
    }

    private void renderBatch(
            VfxRenderContext context,
            GpuTextureView colorTarget,
            GpuTextureView depthTarget,
            Identifier textureId,
            List<SkyStrikeWorldData> data
    ) {
        if (quadBuffer == null || instanceBuffer == null || data.isEmpty()) return;
        if (data.size() > capacityQuads) grow(data.size());
        var neededBytes = (long) INSTANCE_STRIDE * data.size();
        if (instanceData == null || instanceData.capacity() < neededBytes) {
            instanceData = BufferUtils.createByteBuffer(Math.toIntExact(neededBytes));
        }

        instanceData.clear();
        var camera = context.cameraPos();
        for (var quad : data) {
            putCorner(instanceData, quad.corner0(), UV0, quad.color(), camera);
            putCorner(instanceData, quad.corner1(), UV1, quad.color(), camera);
            putCorner(instanceData, quad.corner2(), UV2, quad.color(), camera);
            putCorner(instanceData, quad.corner3(), UV3, quad.color(), camera);
        }
        instanceData.flip();
        context.device().createCommandEncoder()
                .writeToBuffer(instanceBuffer.slice(0, neededBytes), instanceData);

        var encoder = context.device().createCommandEncoder();
        try (var pass = encoder.createRenderPass(
                this::label, colorTarget, Optional.empty(), depthTarget, OptionalDouble.empty()
        )) {
            pass.setPipeline(glow
                    ? VfxPipelines.SKY_STRIKE_QUAD_ADDITIVE
                    : VfxPipelines.SKY_STRIKE_QUAD_TRANSLUCENT);
            RenderSystem.bindDefaultUniforms(pass);
            pass.setUniform("Projection", context.projectionUniform());
            var transform = RenderSystem.getDynamicUniforms().writeTransform(context.viewRotationMatrix());
            pass.setUniform("DynamicTransforms", transform);
            var texture = Minecraft.getInstance().getTextureManager().getTexture(textureId);
            pass.bindTexture("Sampler0", texture.getTextureView(), texture.getSampler());
            pass.setVertexBuffer(0, quadBuffer.slice());
            pass.setVertexBuffer(1, instanceBuffer.slice(0, neededBytes));
            var sequential = RenderSystem.getSequentialBuffer(PrimitiveTopology.QUADS);
            pass.setIndexBuffer(sequential.getBuffer(6), sequential.type());
            pass.drawIndexed(6, data.size(), 0, 0, 0);
        }
    }

    private void grow(int requiredQuads) {
        if (instanceBuffer == null) return;
        var old = instanceBuffer;
        capacityQuads = Math.max(requiredQuads, capacityQuads * 2);
        instanceBuffer = RenderSystem.getDevice().createBuffer(
                this::label,
                GpuBuffer.USAGE_VERTEX | GpuBuffer.USAGE_COPY_DST,
                (long) INSTANCE_STRIDE * capacityQuads
        );
        old.close();
    }

    private String label() {
        return glow ? "VFX Sky Strike Glow" : "VFX Sky Strike Core";
    }

    @Override
    public void close() {
        if (quadBuffer != null) quadBuffer.close();
        if (instanceBuffer != null) instanceBuffer.close();
        quadBuffer = null;
        instanceBuffer = null;
    }
}
