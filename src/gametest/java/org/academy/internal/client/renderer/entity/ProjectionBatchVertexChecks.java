package org.academy.internal.client.renderer.entity;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.model.geom.builders.UVPair;
import net.minecraft.util.LightCoordsUtil;
import org.academy.internal.client.renderer.entity.state.NativeStructureProjectionRenderState;

import java.util.ArrayList;

/** Records the actual production batch emitter instead of merely inspecting its cached meshes. */
public final class ProjectionBatchVertexChecks {
    private ProjectionBatchVertexChecks() {}

    public static void run(NativeStructureProjectionRenderState state) {
        var batches = new ArrayList<>(state.solidBatches);
        batches.addAll(state.cutoutBatches);
        batches.addAll(state.translucentBatches);
        var buffer = new RecordingConsumer();
        NativeStructureProjectionRenderer.emitBatchGeometry(batches, state, new PoseStack().last(), buffer);
        int offset = 0;
        boolean asymmetricUv = false;
        for (var batch : batches) for (int index = 0; index < batch.quads.size(); index++) {
            var quad = batch.quads.get(index);
            for (int vertex = 0; vertex < 4; vertex++) {
                var actual = buffer.vertices.get(offset++);
                float u = UVPair.unpackU(quad.packedUV(vertex));
                float v = UVPair.unpackV(quad.packedUV(vertex));
                asymmetricUv |= Float.compare(u, v) != 0;
                require(Float.compare(actual.u, u) == 0 && Float.compare(actual.v, v) == 0,
                        "Submitted atlas UV must match vanilla U/V order");
                require(actual.light == LightCoordsUtil.lightCoordsWithEmission(batch.vertexLight(index, vertex),
                        quad.materialInfo().lightEmission()), "Submitted vertex light must retain vanilla emission");
                var position = quad.position(vertex);
                require(actual.x == position.x() && actual.y == position.y() && actual.z == position.z(),
                        "Submitted position must retain the translated local model vertex");
            }
        }
        require(offset == buffer.vertices.size() && offset > 0, "Every cached vertex must be submitted exactly once");
        require(asymmetricUv, "UV regression fixture must distinguish U from V");
        int originalOutline = state.outlineColor;
        try {
            state.outlineColor = 0;
            require(NativeStructureProjectionRenderer.usesBlockRenderer(state)
                            == Boolean.getBoolean("academy.nativeStructureForceBlockRenderer"),
                    "Normal structures should retain the batch path");
            state.outlineColor = 0xFF00FF00;
            require(NativeStructureProjectionRenderer.usesBlockRenderer(state),
                    "Glowing structures must retain vanilla outline rendering");
        } finally {
            state.outlineColor = originalOutline;
        }
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }

    private static final class Vertex {
        float x, y, z, u, v;
        int light;
    }

    private static final class RecordingConsumer implements VertexConsumer {
        private final ArrayList<Vertex> vertices = new ArrayList<>();
        private Vertex current;

        @Override
        public VertexConsumer addVertex(float x, float y, float z) {
            current = new Vertex();
            current.x = x;
            current.y = y;
            current.z = z;
            vertices.add(current);
            return this;
        }

        @Override public VertexConsumer setColor(int r, int g, int b, int a) { return this; }
        @Override public VertexConsumer setColor(int color) { return this; }
        @Override public VertexConsumer setUv(float u, float v) { current.u = u; current.v = v; return this; }
        @Override public VertexConsumer setUv1(int u, int v) { return this; }
        @Override public VertexConsumer setUv2(int u, int v) { current.light = u | v << 16; return this; }
        @Override public VertexConsumer setNormal(float x, float y, float z) { return this; }
        @Override public VertexConsumer setLineWidth(float width) { return this; }
    }
}
