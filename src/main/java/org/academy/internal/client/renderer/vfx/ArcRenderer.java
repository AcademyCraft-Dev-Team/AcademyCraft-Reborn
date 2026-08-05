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
import org.academy.api.client.renderer.ArcFactory;
import org.academy.api.client.resources.R;
import org.joml.Vector3f;
import org.jspecify.annotations.Nullable;
import org.lwjgl.BufferUtils;

import java.nio.ByteBuffer;
import java.util.*;
import java.util.function.Supplier;

public final class ArcRenderer implements VfxRenderer<ArcData> {
    private static final int INSTANCE_STRIDE = 8 * 4 * 4;
    private static final int INITIAL_QUADS = 128;

    private final boolean glow;
    private @Nullable GpuBuffer quadBuffer;
    private @Nullable GpuBuffer instanceBuffer;
    private @Nullable ByteBuffer instanceData;
    private int capacityQuads;

    public ArcRenderer(boolean glow) {
        this.glow = glow;
    }

    private static void collectQuads(ArcFactory.ArcRenderData data, List<ArcFactory.Quad> out) {
        out.addAll(data.quads);
        for (var branch : data.branches) {
            collectQuads(branch, out);
        }
    }

    @Override
    public void init(GpuDevice device) {
        Supplier<String> label = () -> glow ? "VFX Arc Glow" : "VFX Arc Core";
        try (var bbb = ByteBufferBuilder.exactlySized(DefaultVertexFormat.POSITION.getVertexSize() * 4)) {
            var builder = new BufferBuilder(bbb, PrimitiveTopology.QUADS, DefaultVertexFormat.POSITION);
            builder.addVertex(0, 0, 0);
            builder.addVertex(1, 0, 0);
            builder.addVertex(1, 1, 0);
            builder.addVertex(0, 1, 0);
            try (var meshData = builder.buildOrThrow()) {
                quadBuffer = device.createBuffer(label, GpuBuffer.USAGE_VERTEX, meshData.vertexBuffer());
            }
        }
        instanceBuffer = device.createBuffer(
                label,
                GpuBuffer.USAGE_VERTEX | GpuBuffer.USAGE_COPY_DST,
                (long) INSTANCE_STRIDE * INITIAL_QUADS
        );
        capacityQuads = INITIAL_QUADS;
    }

    @Override
    public void render(VfxRenderContext ctx, List<? extends ArcData> data) {
        if (data.isEmpty() || quadBuffer == null || instanceBuffer == null) return;
        var color = glow ? ctx.bloomInputColor() : ctx.mainColor();
        var depth = glow ? ctx.bloomInputDepth() : ctx.mainDepth();
        if (color == null || depth == null) return;

        var quads = new ArrayList<ArcFactory.Quad>();
        for (var item : data) {
            collectQuads(item.renderData(), quads);
        }
        if (quads.isEmpty()) return;

        if (quads.size() > capacityQuads) {
            grow(quads.size());
        }
        var neededBytes = (long) INSTANCE_STRIDE * quads.size();
        if (instanceData == null || instanceData.capacity() < neededBytes) {
            instanceData = BufferUtils.createByteBuffer(Math.toIntExact(neededBytes));
        }

        var cameraPos = ctx.cameraPos();
        packInstances(quads, cameraPos, instanceData);

        var writeEncoder = ctx.device().createCommandEncoder();
        writeEncoder.writeToBuffer(instanceBuffer.slice(0, neededBytes), instanceData);

        var passEncoder = ctx.device().createCommandEncoder();
        try (var renderPass = passEncoder.createRenderPass(
                () -> glow ? "VFX Arc Glow" : "VFX Arc Core", color, Optional.empty(), depth, OptionalDouble.empty()
        )) {
            renderPass.setPipeline(glow ? VfxPipelines.TEX_QUAD_INSTANCED_ADDITIVE : VfxPipelines.TEX_QUAD_INSTANCED_TRANSLUCENT);
            RenderSystem.bindDefaultUniforms(renderPass);
            var transform = RenderSystem.getDynamicUniforms().writeTransform(RenderSystem.getModelViewMatrixCopy());
            renderPass.setUniform("DynamicTransforms", transform);

            var texture = Minecraft.getInstance().getTextureManager()
                    .getTexture(R.textures.ability.electromaster.skill.arc_generate.effect.line_segment);
            renderPass.bindTexture("Sampler0", texture.getTextureView(), texture.getSampler());

            renderPass.setVertexBuffer(0, quadBuffer.slice());
            renderPass.setVertexBuffer(1, instanceBuffer.slice(0, neededBytes));
            var sequential = RenderSystem.getSequentialBuffer(PrimitiveTopology.QUADS);
            renderPass.setIndexBuffer(sequential.getBuffer(6), sequential.type());
            renderPass.drawIndexed(6, quads.size(), 0, 0, 0);
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

    private void packInstances(List<ArcFactory.Quad> quads, Vector3f cameraPos, ByteBuffer instanceData) {
        instanceData.clear();
        for (var quad : quads) {
            packCorner(quad.v1(), cameraPos, instanceData);
            packCorner(quad.v2(), cameraPos, instanceData);
            packCorner(quad.v3(), cameraPos, instanceData);
            packCorner(quad.v4(), cameraPos, instanceData);
        }
        instanceData.flip();
    }

    private void packCorner(ArcFactory.Vertex v, Vector3f cameraPos, ByteBuffer instanceData) {
        instanceData.putFloat(v.pos.x - cameraPos.x);
        instanceData.putFloat(v.pos.y - cameraPos.y);
        instanceData.putFloat(v.pos.z - cameraPos.z);
        instanceData.putFloat(v.u);
        instanceData.putFloat(v.v);
        instanceData.putFloat(v.color.x);
        instanceData.putFloat(v.color.y);
        instanceData.putFloat(v.color.z);
    }

    private void grow(int requiredQuads) {
        if (instanceBuffer == null) return;
        var newCapacity = Math.max(requiredQuads, capacityQuads * 2);
        var oldBuffer = instanceBuffer;
        instanceBuffer = RenderSystem.getDevice().createBuffer(
                () -> glow ? "VFX Arc Glow" : "VFX Arc Core",
                GpuBuffer.USAGE_VERTEX | GpuBuffer.USAGE_COPY_DST,
                (long) INSTANCE_STRIDE * newCapacity
        );
        capacityQuads = newCapacity;
        oldBuffer.close();
    }
}
