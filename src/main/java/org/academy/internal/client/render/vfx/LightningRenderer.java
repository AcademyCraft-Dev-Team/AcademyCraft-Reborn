package org.academy.internal.client.render.vfx;

import com.mojang.blaze3d.IndexType;
import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.buffers.Std140Builder;
import com.mojang.blaze3d.buffers.Std140SizeCalculator;
import com.mojang.blaze3d.systems.CommandEncoder;
import com.mojang.blaze3d.systems.GpuDevice;
import com.mojang.blaze3d.systems.RenderSystem;
import org.academy.api.client.render.vfx.VfxPipelines;
import org.academy.api.client.render.vfx.VfxRenderContext;
import org.academy.api.client.render.vfx.VfxRenderer;
import org.academy.api.client.render.vfx.lightning.TubeMeshView;
import org.academy.api.common.profiler.AcademyProfiler;
import org.joml.Vector3f;
import org.joml.Vector4f;
import org.jspecify.annotations.Nullable;
import org.lwjgl.BufferUtils;
import org.lwjgl.system.MemoryStack;

import java.util.*;

public final class LightningRenderer implements VfxRenderer<LightningMeshData> {
    private static final int UNIFORM_SIZE =
            new Std140SizeCalculator().putVec4().putVec4().putVec4().putVec4().get();
    private static final float BASE_R = 0.059f;
    private static final float BASE_G = 0.224f;
    private static final float BASE_B = 0.710f;
    private static final float EMISSION_R = 0.80f;
    private static final float EMISSION_G = 4.0f;
    private static final float EMISSION_B = 10.0f;
    private static final float OPACITY = 0.4f;

    private final boolean glow;
    private final Map<TubeMesh, BoltBuffers> buffers = new HashMap<>();
    private @Nullable GpuBuffer uniformBuffer;

    public LightningRenderer(boolean glow) {
        this.glow = glow;
    }

    @Override
    public void init(GpuDevice device) {
        uniformBuffer = device.createBuffer(
                () -> "VFX Lightning Uniforms",
                GpuBuffer.USAGE_UNIFORM | GpuBuffer.USAGE_COPY_DST,
                UNIFORM_SIZE
        );
    }

    @Override
    public void render(VfxRenderContext ctx, List<? extends LightningMeshData> data) {
        if (data.isEmpty() || uniformBuffer == null) return;
        var color = glow ? ctx.bloomInputColor() : ctx.mainColor();
        var depth = glow ? ctx.bloomInputDepth() : ctx.mainDepth();
        if (color == null || depth == null) return;

        var cameraPos = ctx.cameraPos();
        var device = ctx.device();
        var writeEncoder = device.createCommandEncoder();

        writeUniforms(writeEncoder, cameraPos);

        var seen = new HashSet<TubeMesh>();
        var captured = new ArrayList<CapturedBolt>();
        for (var item : data) {
            var tube = item.tube();
            var mesh = tube.mesh();
            seen.add(tube);
            if (mesh.isEmpty()) continue;
            captured.add(new CapturedBolt(tube, mesh));
            buffers.computeIfAbsent(tube, _ -> new BoltBuffers(device))
                    .upload(writeEncoder, mesh);
        }
        purgeUnused(seen);

        var passEncoder = device.createCommandEncoder();
        try (var renderPass = passEncoder.createRenderPass(
                () -> glow ? "VFX Lightning Glow" : "VFX Lightning Core",
                color, Optional.empty(), depth, OptionalDouble.empty()
        )) {
            renderPass.setPipeline(glow ? VfxPipelines.LIGHTNING_TUBE_BLOOM : VfxPipelines.LIGHTNING_TUBE);
            renderPass.setUniform("Projection", ctx.projectionUniform());
            var transform = RenderSystem.getDynamicUniforms().writeTransform(ctx.viewRotationMatrix());
            renderPass.setUniform("DynamicTransforms", transform);
            renderPass.setUniform("LightningUniforms", uniformBuffer.slice());

            for (var bolt : captured) {
                var b = buffers.get(bolt.tube);
                if (b == null || b.indexBuffer == null || b.vertexBuffer == null) continue;
                renderPass.setVertexBuffer(0, b.vertexBuffer.slice(0, b.usedVertexBytes));
                renderPass.setIndexBuffer(b.indexBuffer, IndexType.INT);
                renderPass.drawIndexed(bolt.mesh.indexCount(), 1, 0, 0, 0);
            }
        }
    }

    @Override
    public void close() {
        for (var b : buffers.values()) {
            b.close();
        }
        buffers.clear();
        if (uniformBuffer != null) {
            uniformBuffer.close();
            uniformBuffer = null;
        }
    }

    private void purgeUnused(Set<TubeMesh> seen) {
        var iterator = buffers.entrySet().iterator();
        while (iterator.hasNext()) {
            var entry = iterator.next();
            if (!seen.contains(entry.getKey())) {
                entry.getValue().close();
                iterator.remove();
            }
        }
    }

    private void writeUniforms(CommandEncoder encoder, Vector3f cameraPos) {
        var buffer = uniformBuffer;
        if (buffer == null) return;
        try (var memoryStack = MemoryStack.stackPush()) {
            var builder = Std140Builder.onStack(memoryStack, UNIFORM_SIZE);
            builder.putVec4(new Vector4f(BASE_R, BASE_G, BASE_B, 1.0f));
            builder.putVec4(new Vector4f(EMISSION_R, EMISSION_G, EMISSION_B, 1.0f));
            builder.putVec4(new Vector4f(1.0f, OPACITY, 0.0f, 0.0f));
            builder.putVec4(new Vector4f(cameraPos.x, cameraPos.y, cameraPos.z, 0.0f));
            encoder.writeToBuffer(buffer.slice(), builder.get());
        }
    }

    private static final class BoltBuffers {
        private final GpuDevice device;
        private @Nullable GpuBuffer vertexBuffer;
        private @Nullable GpuBuffer indexBuffer;
        private long usedVertexBytes;
        private int lastIndexCount = -1;
        private long lastVersion = -1;

        private BoltBuffers(GpuDevice device) {
            this.device = device;
        }

        private void upload(CommandEncoder writeEncoder, TubeMeshView mesh) {
            int vertexCount = mesh.vertexCount();
            int indexCount = mesh.indexCount();
            long vertexBytes = (long) vertexCount * TubeMesh.VERTEX_STRIDE_BYTES;
            long meshVersion = mesh.version();

            if (meshVersion == lastVersion && indexBuffer != null) {
                usedVertexBytes = vertexBytes;
                return;
            }

            if (indexCount != lastIndexCount || indexBuffer == null) {
                if (indexBuffer != null) indexBuffer.close();
                var indexBytes = Math.max(1, indexCount) * 4;
                indexBuffer = device.createBuffer(
                        () -> "VFX Lightning Indices",
                        GpuBuffer.USAGE_INDEX | GpuBuffer.USAGE_COPY_DST,
                        indexBytes
                );
                var ib = BufferUtils.createByteBuffer(indexBytes);
                mesh.packIndices(ib);
                writeEncoder.writeToBuffer(indexBuffer.slice(), ib);
                lastIndexCount = indexCount;
            }

            if (vertexBuffer == null || vertexBytes > vertexBuffer.size()) {
                if (vertexBuffer != null) vertexBuffer.close();
                var capacity = Math.max(vertexBytes * 2, 4096L * TubeMesh.VERTEX_STRIDE_BYTES);
                vertexBuffer = device.createBuffer(
                        () -> "VFX Lightning Vertices",
                        GpuBuffer.USAGE_VERTEX | GpuBuffer.USAGE_MAP_WRITE | GpuBuffer.USAGE_HINT_CLIENT_STORAGE,
                        capacity
                );
            }

            var positions = mesh.positions();
            var uvs = mesh.uvs();
            AcademyProfiler.runZone("academy.vfx.lightning.upload", () -> {
                try (var mapped = vertexBuffer.map(0, vertexBytes, false, true)) {
                    var buf = mapped.data();
                    buf.clear();
                    for (int i = 0; i < vertexCount; i++) {
                        int p = i * 3;
                        int u = i * 2;
                        buf.putFloat(positions[p]).putFloat(positions[p + 1]).putFloat(positions[p + 2]);
                        buf.putFloat(uvs[u]).putFloat(uvs[u + 1]);
                    }
                }
            });
            usedVertexBytes = vertexBytes;
            lastVersion = meshVersion;
        }

        private void close() {
            if (vertexBuffer != null) vertexBuffer.close();
            if (indexBuffer != null) indexBuffer.close();
            vertexBuffer = null;
            indexBuffer = null;
        }
    }

    private record CapturedBolt(TubeMesh tube, TubeMeshView mesh) {
    }
}
