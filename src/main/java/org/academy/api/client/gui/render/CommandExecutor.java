package org.academy.api.client.gui.render;

import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.buffers.GpuBufferSlice;
import com.mojang.blaze3d.buffers.GpuFence;
import com.mojang.blaze3d.systems.RenderPass;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.GpuTextureView;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.Minecraft;
import org.academy.AcademyCraft;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;

import java.util.*;

public final class CommandExecutor implements AutoCloseable {
    private static final Logger LOGGER = AcademyCraft.getLogger();

    private static final long INITIAL_CAPACITY = 4L * 1024L * 1024L;

    private @Nullable GpuBuffer globalBuffer;
    private long writeOffset = 0L;

    private final Deque<FrameRegion> activeRegions = new ArrayDeque<>();
    private final Deque<RetiredBuffer> retiredBuffers = new ArrayDeque<>();

    public void execute(
            List<PendingBatch> batches,
            GpuTextureView color,
            GpuTextureView depth,
            GpuBufferSlice projectionUbo,
            GpuBuffer dynamicTransformsUbo,
            float guiScale
    ) {
        processRetiredBuffers();

        if (batches.isEmpty()) return;

        var meshAbsoluteOffsets = new long[batches.size()];
        var allocation = prepareBuffer(batches, meshAbsoluteOffsets);

        if (globalBuffer == null) return;
        var buffer = globalBuffer;

        var uploadResult = uploadBatchData(batches, allocation, meshAbsoluteOffsets, buffer);

        submitRenderPass(
                uploadResult.drawCalls, uploadResult.maxIndexCount,
                color, depth, projectionUbo, dynamicTransformsUbo,
                guiScale, buffer
        );

        recordFence(allocation);
    }

    private AllocationResult prepareBuffer(List<PendingBatch> batches, long[] meshAbsoluteOffsets) {
        var allocation = tryAllocate(batches, meshAbsoluteOffsets);

        if (globalBuffer == null) {
            ensureCapacity(allocation.totalSize);
            allocation = tryAllocate(batches, meshAbsoluteOffsets);
        } else if (allocation.needsRotate || allocation.totalSize > globalBuffer.size()) {
            retireCurrentBuffer();
            if (allocation.totalSize > globalBuffer.size()) ensureCapacity(allocation.totalSize);
            allocation = tryAllocate(batches, meshAbsoluteOffsets);
        }
        return allocation;
    }

    private BatchUploadResult uploadBatchData(
            List<PendingBatch> batches,
            AllocationResult allocation,
            long[] meshAbsoluteOffsets,
            GpuBuffer buffer
    ) {
        var drawCalls = new ArrayList<DrawCall>(batches.size());
        var maxIndexCount = 0;

        try (var mapped = RenderSystem.getDevice().createCommandEncoder()
                .mapBuffer(
                        buffer.slice(allocation.start, allocation.length),
                        false, true
                )
        ) {
            var mappedBuffer = mapped.data();

            for (var i = 0; i < batches.size(); i++) {
                var batch = batches.get(i);
                var mesh = batch.meshData();
                var relativeOffset = meshAbsoluteOffsets[i] - allocation.start;

                mappedBuffer.position(Math.toIntExact(relativeOffset));
                mappedBuffer.put(mesh.vertexBuffer());

                var baseVertex = Math.toIntExact(meshAbsoluteOffsets[i] / batch.vertexStride());
                drawCalls.add(new DrawCall(
                        batch.pipeline(),
                        batch.scissorArea(),
                        batch.textures(),
                        batch.uniforms(),
                        baseVertex,
                        batch.indexCount()
                ));

                maxIndexCount = Math.max(maxIndexCount, batch.indexCount());
                batch.close();
            }
        }
        return new BatchUploadResult(drawCalls, maxIndexCount);
    }

    private void submitRenderPass(
            List<DrawCall> drawCalls, int maxIndexCount,
            GpuTextureView color, GpuTextureView depth,
            GpuBufferSlice projectionUbo, GpuBuffer dynamicTransformsUbo,
            float guiScale, GpuBuffer buffer
    ) {
        var sequentialIndexBuffer = RenderSystem.getSequentialBuffer(VertexFormat.Mode.QUADS);
        var indexBuffer = sequentialIndexBuffer.getBuffer(maxIndexCount);
        var indexType = sequentialIndexBuffer.type();
        var physicalHeight = Minecraft.getInstance().getWindow().getHeight();

        var commandEncoder = RenderSystem.getDevice().createCommandEncoder();
        try (var renderPass = commandEncoder.createRenderPass(
                () -> "UIRender",
                color, OptionalInt.empty(), depth, OptionalDouble.empty()
        )) {
            renderPass.setIndexBuffer(indexBuffer, indexType);

            for (var drawCall : drawCalls) {
                configureDrawCall(renderPass, drawCall, projectionUbo, dynamicTransformsUbo, guiScale, physicalHeight, buffer);
                renderPass.drawIndexed(drawCall.baseVertex(), 0, drawCall.indexCount(), 1);
            }
        }
    }

    private void configureDrawCall(
            RenderPass renderPass,
            DrawCall drawCall,
            GpuBufferSlice projectionUbo, GpuBuffer dynamicTransformsUbo,
            float guiScale, int physicalHeight,
            GpuBuffer buffer
    ) {
        renderPass.setPipeline(drawCall.pipeline());
        renderPass.setVertexBuffer(0, buffer);

        var scissor = drawCall.scissorArea();
        if (scissor != null) {
            var pos = scissor.getPosition();
            var screenX = (int) (pos.x * guiScale);
            var screenWidth = (int) (scissor.getWidth() * guiScale);
            var screenHeight = (int) (scissor.getHeight() * guiScale);
            var screenY = (int) (physicalHeight - (pos.y + scissor.getHeight()) * guiScale);
            renderPass.enableScissor(screenX, screenY, screenWidth, screenHeight);
        } else renderPass.disableScissor();

        RenderSystem.bindDefaultUniforms(renderPass);
        renderPass.setUniform("Projection", projectionUbo);
        renderPass.setUniform("DynamicTransforms", dynamicTransformsUbo);

        bindResources(renderPass, drawCall);
    }

    private void bindResources(RenderPass renderPass, DrawCall drawCall) {
        for (var texture : drawCall.textures()) {
            var value = texture.view();
            if (value.isClosed()) {
                LOGGER.error("Sampler {} has been closed, skipping draw call.", texture.name());
                continue;
            }
            renderPass.bindTexture(texture.name(), value, texture.sampler());
        }
        for (var uniform : drawCall.uniforms()) {
            var since = uniform.slice();
            if (since.buffer().isClosed()) {
                LOGGER.error("Uniform {} has been closed, skipping draw call.", uniform.name());
            }
            renderPass.setUniform(uniform.name(), since);
        }
    }

    private void recordFence(AllocationResult allocation) {
        var fence = RenderSystem.getDevice().createCommandEncoder().createFence();
        activeRegions.addLast(new FrameRegion(allocation.start, allocation.end, fence));
        writeOffset = allocation.end;
    }

    private AllocationResult tryAllocate(List<PendingBatch> batches, long[] outOffsets) {
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
                if (isRegionConflicted(startPtr, endPtr)) needsRotate = true;
            } else needsRotate = true;
        } else if (isRegionConflicted(startPtr, endPtr)) needsRotate = true;

        return new AllocationResult(startPtr, endPtr, requiredLength, requiredLength, needsRotate);
    }

    private void processRetiredBuffers() {
        while (!retiredBuffers.isEmpty()) {
            var retired = retiredBuffers.peekFirst();
            if (retired.isReady()) {
                retired.free();
                retiredBuffers.pollFirst();
            } else break;
        }
    }

    private boolean isRegionConflicted(long start, long end) {
        cleanupActiveRegions();
        for (var region : activeRegions) if (region.intersects(start, end)) return true;
        return false;
    }

    private void cleanupActiveRegions() {
        while (!activeRegions.isEmpty()) {
            var region = activeRegions.peekFirst();
            if (region.fence.awaitCompletion(0)) {
                region.fence.close();
                activeRegions.pollFirst();
            } else break;
        }
    }

    private void retireCurrentBuffer() {
        if (globalBuffer != null) {
            retiredBuffers.add(new RetiredBuffer(globalBuffer, new ArrayDeque<>(activeRegions)));
            globalBuffer = null;
            activeRegions.clear();
            writeOffset = 0L;
        }
    }

    private void ensureCapacity(long requiredBytes) {
        var newSize = Math.max(INITIAL_CAPACITY, requiredBytes);
        if (!retiredBuffers.isEmpty()) {
            newSize = Math.max(retiredBuffers.getLast().buffer.size(), newSize);
        }
        var usage = GpuBuffer.USAGE_VERTEX | GpuBuffer.USAGE_COPY_DST | GpuBuffer.USAGE_MAP_WRITE;
        globalBuffer = RenderSystem.getDevice().createBuffer(
                () -> "AC UI StreamingBuffer", usage, newSize
        );
        writeOffset = 0L;
    }

    private static long align(long offset, int alignment) {
        return (offset + (alignment - 1)) / alignment * alignment;
    }

    @Override
    public void close() {
        if (globalBuffer != null) {
            globalBuffer.close();
            globalBuffer = null;
        }
        for (var region : activeRegions) region.fence.close();
        activeRegions.clear();
        for (var retired : retiredBuffers) retired.free();
        retiredBuffers.clear();
        writeOffset = 0L;
    }

    private record FrameRegion(long start, long end, GpuFence fence) {
        boolean intersects(long otherStart, long otherEnd) {
            return start < otherEnd && otherStart < end;
        }
    }

    private record RetiredBuffer(GpuBuffer buffer, Deque<FrameRegion> regions) {
        boolean isReady() {
            if (regions.isEmpty()) return true;
            return regions.getLast().fence.awaitCompletion(0);
        }

        void free() {
            for (var region : regions) region.fence.close();
            regions.clear();
            buffer.close();
        }
    }

    private record AllocationResult(long start, long end, long length, long totalSize, boolean needsRotate) {
    }

    private record BatchUploadResult(List<DrawCall> drawCalls, int maxIndexCount) {
    }
}