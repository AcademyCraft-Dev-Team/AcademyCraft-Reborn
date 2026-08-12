package org.academy.internal.client.render.vfx;

import com.mojang.blaze3d.PrimitiveTopology;
import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.buffers.Std140Builder;
import com.mojang.blaze3d.systems.GpuDevice;
import com.mojang.blaze3d.systems.RenderPass;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.ByteBufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import net.minecraft.world.phys.AABB;
import org.academy.api.client.render.vfx.VfxPipelines;
import org.academy.api.client.render.vfx.VfxRenderContext;
import org.academy.api.client.render.vfx.VfxRenderer;
import org.academy.api.client.util.VertexUtil;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.joml.Vector4f;
import org.jspecify.annotations.Nullable;
import org.lwjgl.BufferUtils;

import java.nio.ByteBuffer;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalDouble;
import java.util.function.Supplier;
import net.minecraft.util.Mth;

public final class BeamRenderer implements VfxRenderer<BeamData> {
    private static final AABB RAY = new AABB(-0.5, 0, -0.5, 0.5, 1, 0.5);
    private static final BeamGeometry GEOMETRY = new BeamGeometry();
    private static final int INSTANCE_STRIDE = 64;
    private static final int INITIAL_INSTANCES = 128;

    private final boolean glow;
    private final Vector4f colorModulator;
    private final float ballFactor;
    private final float boxFactorXZ;

    private @Nullable GpuBuffer ballInstanceBuffer;
    private @Nullable GpuBuffer boxInstanceBuffer;
    private @Nullable ByteBuffer instanceData;
    private int capacityInstances;

    public BeamRenderer(boolean glow) {
        this.glow = glow;
        if (glow) {
            colorModulator = new Vector4f(0.0f, 1.0f, 0.0f, 1.0f);
            ballFactor = 1.0f;
            boxFactorXZ = 1.0f;
        } else {
            colorModulator = new Vector4f(1.0f, 1.0f, 1.0f, 1.0f);
            ballFactor = 0.85f;
            boxFactorXZ = 0.75f;
        }
    }

    private static Matrix4f orientation(BeamData beam) {
        return new Matrix4f()
                .rotateY((float) (90 - beam.yRot()) * Mth.DEG_TO_RAD)
                .rotateZ((float) (90 + beam.xRot()) * Mth.DEG_TO_RAD);
    }

    @Override
    public void init(GpuDevice device) {
        GEOMETRY.init(device);
        ballInstanceBuffer = device.createBuffer(
                () -> "VFX Beam Ball Instances",
                GpuBuffer.USAGE_VERTEX | GpuBuffer.USAGE_COPY_DST,
                (long) INSTANCE_STRIDE * INITIAL_INSTANCES
        );
        boxInstanceBuffer = device.createBuffer(
                () -> "VFX Beam Box Instances",
                GpuBuffer.USAGE_VERTEX | GpuBuffer.USAGE_COPY_DST,
                (long) INSTANCE_STRIDE * INITIAL_INSTANCES
        );
        capacityInstances = INITIAL_INSTANCES;
    }

    @Override
    public void close() {
        GEOMETRY.close();
        if (ballInstanceBuffer != null) {
            ballInstanceBuffer.close();
            ballInstanceBuffer = null;
        }
        if (boxInstanceBuffer != null) {
            boxInstanceBuffer.close();
            boxInstanceBuffer = null;
        }
    }

    @Override
    public void render(VfxRenderContext ctx, List<? extends BeamData> data) {
        if (data.isEmpty() || ballInstanceBuffer == null || boxInstanceBuffer == null) return;
        var color = glow ? ctx.bloomInputColor() : ctx.mainColor();
        var depth = glow ? ctx.bloomInputDepth() : ctx.mainDepth();
        if (color == null || depth == null) return;

        var instanceCount = data.size();
        var neededBytes = (long) INSTANCE_STRIDE * instanceCount;
        if (neededBytes > ballInstanceBuffer.size()) {
            grow(instanceCount);
        }
        if (instanceData == null || instanceData.capacity() < neededBytes) {
            instanceData = BufferUtils.createByteBuffer(Math.toIntExact(neededBytes));
        }

        var cameraPos = ctx.cameraPos();
        var writeEncoder = ctx.device().createCommandEncoder();
        writeBallInstances(data, cameraPos, instanceData);
        writeEncoder.writeToBuffer(ballInstanceBuffer.slice(0, neededBytes), instanceData);
        writeBoxInstances(data, cameraPos, instanceData);
        writeEncoder.writeToBuffer(boxInstanceBuffer.slice(0, neededBytes), instanceData);

        var passEncoder = ctx.device().createCommandEncoder();
        try (var renderPass = passEncoder.createRenderPass(
                () -> glow ? "VFX Beam Glow" : "VFX Beam Core", color, Optional.empty(), depth, OptionalDouble.empty()
        )) {
            renderPass.setUniform("Projection", ctx.projectionUniform());
            var transform = RenderSystem.getDynamicUniforms()
                    .writeTransform(ctx.viewRotationMatrix(), colorModulator);
            renderPass.setUniform("DynamicTransforms", transform);

            drawBallInstances(renderPass, instanceCount, ballInstanceBuffer);
            drawBoxInstances(renderPass, instanceCount, boxInstanceBuffer);
        }
    }

    private void writeBallInstances(List<? extends BeamData> data, Vector3f cameraPos, ByteBuffer instanceData) {
        instanceData.clear();
        var builder = Std140Builder.intoBuffer(instanceData);
        for (var beam : data) {
            var relPos = new Vector3f(beam.pos()).sub(cameraPos);
            var ballRadius = beam.progress() * 0.185f * beam.ballScale();
            builder.putMat4f(new Matrix4f()
                    .translate(relPos)
                    .mul(orientation(beam))
                    .scale(ballRadius * ballFactor));
        }
        instanceData.flip();
    }

    private void writeBoxInstances(List<? extends BeamData> data, Vector3f cameraPos, ByteBuffer instanceData) {
        instanceData.clear();
        var builder = Std140Builder.intoBuffer(instanceData);
        for (var beam : data) {
            var relPos = new Vector3f(beam.pos()).sub(cameraPos);
            var rayVisualProgress = beam.isCharging() ? 0.0f : beam.progress();
            var width = rayVisualProgress * 0.25f * boxFactorXZ * beam.widthScale();
            builder.putMat4f(new Matrix4f()
                    .translate(relPos)
                    .mul(orientation(beam))
                    .scale(width, beam.length(), width));
        }
        instanceData.flip();
    }

    private void drawBallInstances(RenderPass renderPass, int instanceCount, GpuBuffer instanceBuffer) {
        renderPass.setPipeline(glow ? VfxPipelines.BEAM_BALL : VfxPipelines.BEAM_CORE_BALL);
        renderPass.setVertexBuffer(0, GEOMETRY.ballBuffer().slice());
        renderPass.setVertexBuffer(1, instanceBuffer.slice(0, (long) INSTANCE_STRIDE * instanceCount));

        var sequential = RenderSystem.getSequentialBuffer(PrimitiveTopology.TRIANGLES);
        renderPass.setIndexBuffer(sequential.getBuffer(GEOMETRY.ballVertexCount()), sequential.type());
        renderPass.drawIndexed(GEOMETRY.ballVertexCount(), instanceCount, 0, 0, 0);
    }

    private void drawBoxInstances(RenderPass renderPass, int instanceCount, GpuBuffer instanceBuffer) {
        renderPass.setPipeline(glow ? VfxPipelines.BEAM_BOX : VfxPipelines.BEAM_CORE_BOX);
        renderPass.setVertexBuffer(0, GEOMETRY.boxBuffer().slice());
        renderPass.setVertexBuffer(1, instanceBuffer.slice(0, (long) INSTANCE_STRIDE * instanceCount));

        var indexCount = GEOMETRY.boxVertexCount() / 4 * 6;
        var sequential = RenderSystem.getSequentialBuffer(PrimitiveTopology.QUADS);
        renderPass.setIndexBuffer(sequential.getBuffer(indexCount), sequential.type());
        renderPass.drawIndexed(indexCount, instanceCount, 0, 0, 0);
    }

    private void grow(int requiredInstances) {
        if (ballInstanceBuffer == null || boxInstanceBuffer == null) return;
        var newCapacity = Math.max(requiredInstances, capacityInstances * 2);
        var oldBall = ballInstanceBuffer;
        ballInstanceBuffer = RenderSystem.getDevice().createBuffer(
                () -> "VFX Beam Ball Instances",
                GpuBuffer.USAGE_VERTEX | GpuBuffer.USAGE_COPY_DST,
                (long) INSTANCE_STRIDE * newCapacity
        );
        var oldBox = boxInstanceBuffer;
        boxInstanceBuffer = RenderSystem.getDevice().createBuffer(
                () -> "VFX Beam Box Instances",
                GpuBuffer.USAGE_VERTEX | GpuBuffer.USAGE_COPY_DST,
                (long) INSTANCE_STRIDE * newCapacity
        );
        capacityInstances = newCapacity;
        oldBall.close();
        oldBox.close();
    }

    private static final class BeamGeometry {
        private @Nullable GpuBuffer ballBuffer;
        private @Nullable GpuBuffer boxBuffer;
        private int ballVertexCount;
        private int boxVertexCount;
        private boolean initialized;

        private static float[][] toPositions(float[][] vertices) {
            var positions = new float[vertices.length][3];
            for (var i = 0; i < vertices.length; i++) {
                positions[i][0] = vertices[i][0];
                positions[i][1] = vertices[i][1];
                positions[i][2] = vertices[i][2];
            }
            return positions;
        }

        private static GpuBuffer buildPosColor(GpuDevice device, Supplier<String> label, float[][] positions, PrimitiveTopology topology) {
            var format = DefaultVertexFormat.POSITION_COLOR;
            try (var byteBufferBuilder = ByteBufferBuilder.exactlySized(format.getVertexSize() * positions.length)) {
                var builder = new BufferBuilder(byteBufferBuilder, topology, format);
                for (var position : positions) {
                    builder.addVertex(position[0], position[1], position[2]).setColor(1.0f, 1.0f, 1.0f, 1.0f);
                }
                try (var meshData = builder.buildOrThrow()) {
                    return device.createBuffer(label, GpuBuffer.USAGE_VERTEX, meshData.vertexBuffer());
                }
            }
        }

        void init(GpuDevice device) {
            if (initialized) return;

            var icosphere = VertexUtil.Ball.getIcosphereVertexBuffer(1.0f, 2, true);
            ballVertexCount = icosphere.length;
            ballBuffer = buildPosColor(device, () -> "VFX Beam Ball", toPositions(icosphere), PrimitiveTopology.TRIANGLES);

            var cylinder = VertexUtil.Cylinder.getCylinderVertexBuffer(0.0f, 1.0f, 0.5f, 16, false);
            var positions = toPositions(cylinder);
            boxVertexCount = positions.length;
            boxBuffer = buildPosColor(device, () -> "VFX Beam Box", positions, PrimitiveTopology.QUADS);

            initialized = true;
        }

        void close() {
            if (ballBuffer != null) {
                ballBuffer.close();
                ballBuffer = null;
            }
            if (boxBuffer != null) {
                boxBuffer.close();
                boxBuffer = null;
            }
            initialized = false;
        }

        GpuBuffer ballBuffer() {
            return Objects.requireNonNull(ballBuffer);
        }

        GpuBuffer boxBuffer() {
            return Objects.requireNonNull(boxBuffer);
        }

        int ballVertexCount() {
            return ballVertexCount;
        }

        int boxVertexCount() {
            return boxVertexCount;
        }
    }
}
