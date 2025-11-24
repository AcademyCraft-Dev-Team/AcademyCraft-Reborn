package org.academy.api.client.gui.render;

import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.buffers.GpuBufferSlice;
import com.mojang.blaze3d.buffers.GpuFence;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.GpuTextureView;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.Minecraft;
import net.neoforged.neoforge.client.stencil.StencilFunction;
import net.neoforged.neoforge.client.stencil.StencilOperation;
import net.neoforged.neoforge.client.stencil.StencilPerFaceTest;
import net.neoforged.neoforge.client.stencil.StencilTest;
import org.academy.AcademyCraft;
import org.jspecify.annotations.Nullable;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.OptionalDouble;
import java.util.OptionalInt;

public final class CommandExecutor implements AutoCloseable {
    private static final int INITIAL_CAPACITY = 4 * 1024 * 1024;

    private @Nullable GpuBuffer globalBuffer;
    private int writeOffset = 0;

    private final Deque<FrameRegion> activeRegions = new ArrayDeque<>();
    private final Deque<RetiredBuffer> retiredBuffers = new ArrayDeque<>();

    public void execute(
            List<PendingBatch> batches,
            GpuTextureView color,
            GpuTextureView depth,
            GpuBufferSlice projectionUbo,
            GpuBuffer dynamicTransformsUbo,
            float guiScale,
            boolean stencilTest
    ) {
        processRetiredBuffers();

        if (batches.isEmpty())
            return;

        var meshAbsoluteOffsets = new int[batches.size()];
        var allocation = tryAllocate(batches, meshAbsoluteOffsets);

        if (globalBuffer == null) {
            ensureCapacity(allocation.totalSize);
            allocation = tryAllocate(batches, meshAbsoluteOffsets);
        } else if (allocation.needsRotate || allocation.totalSize > globalBuffer.size()) {
            retireCurrentBuffer();
            if (allocation.totalSize > globalBuffer.size()) {
                ensureCapacity(allocation.totalSize);
            }
            allocation = tryAllocate(batches, meshAbsoluteOffsets);
        }

        var drawCalls = new ArrayList<DrawCall>(batches.size());
        var maxIndexCount = 0;

        try (var mapped = RenderSystem.getDevice().createCommandEncoder()
                .mapBuffer(globalBuffer.slice(allocation.start, allocation.length), false, true)) {

            var buffer = mapped.data();

            for (var i = 0; i < batches.size(); i++) {
                var batch = batches.get(i);
                var mesh = batch.meshData();
                var srcBuffer = mesh.vertexBuffer();
                var relativeOffset = meshAbsoluteOffsets[i] - allocation.start;
                buffer.position(relativeOffset);
                buffer.put(srcBuffer);

                var baseVertex = meshAbsoluteOffsets[i] / batch.vertexStride();
                drawCalls.add(new DrawCall(
                        batch.pipeline(),
                        batch.scissorArea(),
                        batch.samplers(),
                        batch.uniforms(),
                        baseVertex,
                        batch.indexCount()
                ));

                maxIndexCount = Math.max(maxIndexCount, batch.indexCount());
                batch.close();
            }
        }

        var sequentialIndexBuffer = RenderSystem.getSequentialBuffer(VertexFormat.Mode.QUADS);
        var indexBuffer = sequentialIndexBuffer.getBuffer(maxIndexCount);
        var indexType = sequentialIndexBuffer.type();
        var window = Minecraft.getInstance().getWindow();
        var physicalHeight = window.getHeight();

        var commandEncoder = RenderSystem.getDevice().createCommandEncoder();
        try (var renderPass = commandEncoder.createRenderPass(
                () -> "UIRender", color, OptionalInt.empty(), depth, OptionalDouble.empty()
        )) {
            renderPass.setIndexBuffer(indexBuffer, indexType);

            for (var drawCall : drawCalls) {
                var hasClosedSampler = false;

                {
                    var pipeline = drawCall.pipeline();
                    if (stencilTest) {
                        var perFaceTest = new StencilPerFaceTest(
                                StencilOperation.KEEP,
                                StencilOperation.KEEP,
                                StencilOperation.REPLACE,
                                StencilFunction.ALWAYS
                        );
                        var stencilTestConfig = new StencilTest(
                                perFaceTest, 0xFF, 0xFF, 1
                        );
                        pipeline = pipeline.toBuilder()
                                .withStencilTest(stencilTestConfig)
                                .build();
                    }
                    renderPass.setPipeline(pipeline);

                    renderPass.setVertexBuffer(0, globalBuffer);

                    var scissor = drawCall.scissorArea();
                    if (scissor != null) {
                        var pos = scissor.getPosition();
                        var screenX = (int) (pos.x * guiScale);
                        var screenWidth = (int) (scissor.getWidth() * guiScale);
                        var screenHeight = (int) (scissor.getHeight() * guiScale);
                        var screenY = (int) (physicalHeight - (pos.y + scissor.getHeight()) * guiScale);
                        renderPass.enableScissor(screenX, screenY, screenWidth, screenHeight);
                    } else {
                        renderPass.disableScissor();
                    }

                    RenderSystem.bindDefaultUniforms(renderPass);
                    renderPass.setUniform("Projection", projectionUbo);
                    renderPass.setUniform("DynamicTransforms", dynamicTransformsUbo);

                    for (var entry : drawCall.samplers().entrySet()) {
                        var value = entry.getValue();
                        if (value.isClosed()) {
                            AcademyCraft.LOGGER.error("Sampler {} has been closed, skipping draw call.", entry.getKey());
                            hasClosedSampler = true;
                            break;
                        }
                        renderPass.bindSampler(entry.getKey(), value);
                    }
                    if (hasClosedSampler) {
                        continue;
                    }
                    for (var entry : drawCall.uniforms().entrySet()) {
                        renderPass.setUniform(entry.getKey(), entry.getValue());
                    }
                }

                renderPass.drawIndexed(drawCall.baseVertex(), 0, drawCall.indexCount(), 1);
            }
        }

        var fence = commandEncoder.createFence();
        activeRegions.addLast(new FrameRegion(allocation.start, allocation.end, fence));
        writeOffset = allocation.end;
    }

    private AllocationResult tryAllocate(List<PendingBatch> batches, int[] outOffsets) {
        var currentPtr = writeOffset;

        for (var i = 0; i < batches.size(); i++) {
            var batch = batches.get(i);
            currentPtr = align(currentPtr, batch.vertexStride());
            outOffsets[i] = currentPtr;
            currentPtr += batch.vertexBufferSize();
        }

        var endPtr = currentPtr;
        var startPtr = writeOffset;
        var requiredLength = endPtr - startPtr;

        var needsRotate = false;

        if (globalBuffer != null && endPtr > globalBuffer.size()) {
            currentPtr = 0;
            for (var i = 0; i < batches.size(); i++) {
                var batch = batches.get(i);
                currentPtr = align(currentPtr, batch.vertexStride());
                outOffsets[i] = currentPtr;
                currentPtr += batch.vertexBufferSize();
            }
            startPtr = 0;
            endPtr = currentPtr;
            requiredLength = endPtr;

            if (globalBuffer != null && endPtr <= globalBuffer.size()) {
                if (isRegionConflicted(startPtr, endPtr)) {
                    needsRotate = true;
                }
            } else {
                needsRotate = true;
            }
        } else {
            if (isRegionConflicted(startPtr, endPtr)) {
                needsRotate = true;
            }
        }

        return new AllocationResult(startPtr, endPtr, requiredLength, requiredLength, needsRotate);
    }

    private void processRetiredBuffers() {
        while (!retiredBuffers.isEmpty()) {
            var retired = retiredBuffers.peekFirst();
            if (retired.isReady()) {
                retired.free();
                retiredBuffers.pollFirst();
            } else {
                break;
            }
        }
    }

    private boolean isRegionConflicted(int start, int end) {
        cleanupActiveRegions();
        for (var region : activeRegions) {
            if (region.intersects(start, end)) {
                return true;
            }
        }
        return false;
    }

    private void cleanupActiveRegions() {
        while (!activeRegions.isEmpty()) {
            var region = activeRegions.peekFirst();
            if (region.fence.awaitCompletion(0)) {
                region.fence.close();
                activeRegions.pollFirst();
            } else {
                break;
            }
        }
    }

    private void retireCurrentBuffer() {
        if (globalBuffer != null) {
            retiredBuffers.add(new RetiredBuffer(globalBuffer, new ArrayDeque<>(activeRegions)));
            globalBuffer = null;
            activeRegions.clear();
            writeOffset = 0;
        }
    }

    private void ensureCapacity(int requiredBytes) {
        var newSize = Math.max(INITIAL_CAPACITY, requiredBytes);
        if (!retiredBuffers.isEmpty()) {
            newSize = Math.max(retiredBuffers.getLast().buffer.size(), newSize);
        }
        var usage = GpuBuffer.USAGE_VERTEX | GpuBuffer.USAGE_COPY_DST | GpuBuffer.USAGE_MAP_WRITE;
        globalBuffer = RenderSystem.getDevice().createBuffer(() -> "AC_UI_StreamingBuffer", usage, newSize);
        writeOffset = 0;
    }

    private static int align(int offset, int alignment) {
        return (offset + (alignment - 1)) / alignment * alignment;
    }

    @Override
    public void close() {
        if (globalBuffer != null) {
            globalBuffer.close();
            globalBuffer = null;
        }
        for (var region : activeRegions) {
            region.fence.close();
        }
        activeRegions.clear();
        for (var retired : retiredBuffers) {
            retired.free();
        }
        retiredBuffers.clear();
        writeOffset = 0;
    }

    private record FrameRegion(int start, int end, GpuFence fence) {
        boolean intersects(int otherStart, int otherEnd) {
            return start < otherEnd && otherStart < end;
        }
    }

    private record RetiredBuffer(GpuBuffer buffer, Deque<FrameRegion> regions) {
        boolean isReady() {
            if (regions.isEmpty()) return true;
            return regions.getLast().fence.awaitCompletion(0);
        }

        void free() {
            for (var region : regions) {
                region.fence.close();
            }
            regions.clear();
            buffer.close();
        }
    }

    private record AllocationResult(int start, int end, int length, int totalSize, boolean needsRotate) {
    }
}