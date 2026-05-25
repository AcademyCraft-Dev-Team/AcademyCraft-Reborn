package org.academy.api.client.gui.render

import com.mojang.blaze3d.buffers.GpuBuffer
import com.mojang.blaze3d.buffers.GpuBufferSlice
import com.mojang.blaze3d.buffers.GpuFence
import com.mojang.blaze3d.systems.RenderPass
import com.mojang.blaze3d.systems.RenderSystem
import com.mojang.blaze3d.textures.GpuTextureView
import com.mojang.blaze3d.vertex.VertexFormat
import net.minecraft.client.Minecraft
import org.academy.AcademyCraft
import java.lang.AutoCloseable
import java.util.*
import kotlin.math.max

class CommandExecutor : AutoCloseable {
    private var globalBuffer: GpuBuffer? = null
    private var writeOffset = 0L

    private val activeRegions: Deque<FrameRegion> = ArrayDeque<FrameRegion>()
    private val retiredBuffers: Deque<RetiredBuffer> = ArrayDeque<RetiredBuffer>()

    fun execute(
        batches: List<PendingBatch>,
        color: GpuTextureView,
        depth: GpuTextureView,
        projectionUbo: GpuBufferSlice,
        dynamicTransformsUbo: GpuBuffer,
        guiScale: Float
    ) {
        processRetiredBuffers()

        if (batches.isEmpty()) return

        val meshAbsoluteOffsets = LongArray(batches.size)
        val allocation = prepareBuffer(batches, meshAbsoluteOffsets)

        if (globalBuffer == null) return
        val buffer = globalBuffer

        val uploadResult = uploadBatchData(batches, allocation, meshAbsoluteOffsets, buffer!!)

        submitRenderPass(
            uploadResult.drawCalls, uploadResult.maxIndexCount,
            color, depth, projectionUbo, dynamicTransformsUbo,
            guiScale, buffer
        )

        recordFence(allocation)
    }

    private fun prepareBuffer(batches: List<PendingBatch>, meshAbsoluteOffsets: LongArray): AllocationResult {
        var allocation = tryAllocate(batches, meshAbsoluteOffsets)

        if (globalBuffer == null) {
            ensureCapacity(allocation.totalSize)
            allocation = tryAllocate(batches, meshAbsoluteOffsets)
        } else if (allocation.needsRotate || allocation.totalSize > globalBuffer!!.size()) {
            retireCurrentBuffer()
            if (allocation.totalSize > globalBuffer!!.size()) ensureCapacity(allocation.totalSize)
            allocation = tryAllocate(batches, meshAbsoluteOffsets)
        }
        return allocation
    }

    private fun uploadBatchData(
        batches: List<PendingBatch>,
        allocation: AllocationResult,
        meshAbsoluteOffsets: LongArray,
        buffer: GpuBuffer
    ): BatchUploadResult {
        val drawCalls = ArrayList<DrawCall>(batches.size)
        var maxIndexCount = 0

        RenderSystem.getDevice().createCommandEncoder()
            .mapBuffer(
                buffer.slice(allocation.start, allocation.length),
                false, true
            ).use { mapped ->
                val mappedBuffer = mapped.data()
                for (i in batches.indices) {
                    val batch = batches[i]
                    val mesh = batch.meshData
                    val relativeOffset = meshAbsoluteOffsets[i] - allocation.start

                    mappedBuffer.position(Math.toIntExact(relativeOffset))
                    mappedBuffer.put(mesh.vertexBuffer())

                    val baseVertex = Math.toIntExact(meshAbsoluteOffsets[i] / batch.vertexStride)
                    drawCalls.add(
                        DrawCall(
                            batch.pipeline,
                            batch.scissorArea,
                            batch.textures,
                            batch.uniforms,
                            baseVertex,
                            batch.indexCount
                        )
                    )

                    maxIndexCount = max(maxIndexCount, batch.indexCount)
                    batch.close()
                }
            }
        return BatchUploadResult(drawCalls, maxIndexCount)
    }

    private fun submitRenderPass(
        drawCalls: MutableList<DrawCall>, maxIndexCount: Int,
        color: GpuTextureView, depth: GpuTextureView,
        projectionUbo: GpuBufferSlice, dynamicTransformsUbo: GpuBuffer,
        guiScale: Float, buffer: GpuBuffer
    ) {
        val sequentialIndexBuffer = RenderSystem.getSequentialBuffer(VertexFormat.Mode.QUADS)
        val indexBuffer = sequentialIndexBuffer.getBuffer(maxIndexCount)
        val indexType = sequentialIndexBuffer.type()
        val physicalHeight = Minecraft.getInstance().window.height

        val commandEncoder = RenderSystem.getDevice().createCommandEncoder()
        commandEncoder.createRenderPass(
            { "UIRender" },
            color, OptionalInt.empty(), depth, OptionalDouble.empty()
        ).use { renderPass ->
            renderPass.setIndexBuffer(indexBuffer, indexType)
            for (drawCall in drawCalls) {
                if (configureDrawCall(
                        renderPass, drawCall, projectionUbo, dynamicTransformsUbo, guiScale, physicalHeight, buffer
                    )
                ) renderPass.drawIndexed(drawCall.baseVertex, 0, drawCall.indexCount, 1)
            }
        }
    }

    private fun configureDrawCall(
        renderPass: RenderPass,
        drawCall: DrawCall,
        projectionUbo: GpuBufferSlice, dynamicTransformsUbo: GpuBuffer,
        guiScale: Float, physicalHeight: Int,
        buffer: GpuBuffer
    ): Boolean {
        renderPass.setPipeline(drawCall.pipeline)
        renderPass.setVertexBuffer(0, buffer)

        val scissor = drawCall.scissorArea
        if (scissor != null) {
            val pos = scissor.position
            val screenX = (pos.x * guiScale).toInt()
            val screenWidth = (scissor.width * guiScale).toInt()
            val screenHeight = (scissor.height * guiScale).toInt()
            val screenY = (physicalHeight - (pos.y + scissor.height) * guiScale).toInt()
            renderPass.enableScissor(screenX, screenY, screenWidth, screenHeight)
        } else renderPass.disableScissor()

        RenderSystem.bindDefaultUniforms(renderPass)
        renderPass.setUniform("Projection", projectionUbo)
        renderPass.setUniform("DynamicTransforms", dynamicTransformsUbo)

        return bindResources(renderPass, drawCall)
    }

    private fun bindResources(renderPass: RenderPass, drawCall: DrawCall): Boolean {
        for (texture in drawCall.textures) {
            val value = texture.view
            if (value.isClosed) {
                logger.error("Sampler {} has been closed, skipping draw call.", texture.name)
                return false
            }
            renderPass.bindTexture(texture.name, value, texture.sampler)
        }
        for (uniform in drawCall.uniforms) {
            val since = uniform.slice
            if (since.buffer().isClosed) {
                logger.error("Uniform {} has been closed, skipping draw call.", uniform.name)
                return false
            }
            renderPass.setUniform(uniform.name, since)
        }
        return true
    }

    private fun recordFence(allocation: AllocationResult) {
        val fence = RenderSystem.getDevice().createCommandEncoder().createFence()
        activeRegions.addLast(FrameRegion(allocation.start, allocation.end, fence))
        writeOffset = allocation.end
    }

    private fun tryAllocate(batches: List<PendingBatch>, outOffsets: LongArray): AllocationResult {
        var currentPtr = writeOffset

        for (i in batches.indices) {
            val batch = batches[i]
            currentPtr = align(currentPtr, batch.vertexStride)
            outOffsets[i] = currentPtr
            currentPtr += batch.vertexBufferSize.toLong()
        }

        var endPtr = currentPtr
        var startPtr = writeOffset
        var requiredLength = endPtr - startPtr

        var needsRotate = false

        if (globalBuffer != null && endPtr > globalBuffer!!.size()) {
            currentPtr = 0
            for (i in batches.indices) {
                val batch = batches[i]
                currentPtr = align(currentPtr, batch.vertexStride)
                outOffsets[i] = currentPtr
                currentPtr += batch.vertexBufferSize.toLong()
            }
            startPtr = 0
            endPtr = currentPtr
            requiredLength = endPtr

            if (globalBuffer != null && endPtr <= globalBuffer!!.size()) {
                if (isRegionConflicted(startPtr, endPtr)) needsRotate = true
            } else needsRotate = true
        } else if (isRegionConflicted(startPtr, endPtr)) needsRotate = true

        return AllocationResult(startPtr, endPtr, requiredLength, requiredLength, needsRotate)
    }

    private fun processRetiredBuffers() {
        while (!retiredBuffers.isEmpty()) {
            val retired = retiredBuffers.peekFirst()
            if (retired.isReady) {
                retired.free()
                retiredBuffers.pollFirst()
            } else break
        }
    }

    private fun isRegionConflicted(start: Long, end: Long): Boolean {
        cleanupActiveRegions()
        for (region in activeRegions) if (region.intersects(start, end)) return true
        return false
    }

    private fun cleanupActiveRegions() {
        while (!activeRegions.isEmpty()) {
            val region = activeRegions.peekFirst()
            if (region.fence.awaitCompletion(0)) {
                region.fence.close()
                activeRegions.pollFirst()
            } else break
        }
    }

    private fun retireCurrentBuffer() {
        if (globalBuffer != null) {
            retiredBuffers.add(RetiredBuffer(globalBuffer!!, ArrayDeque(activeRegions)))
            globalBuffer = null
            activeRegions.clear()
            writeOffset = 0L
        }
    }

    private fun ensureCapacity(requiredBytes: Long) {
        var newSize = max(INITIAL_CAPACITY, requiredBytes)
        if (!retiredBuffers.isEmpty()) {
            newSize = max(retiredBuffers.getLast().buffer.size(), newSize)
        }
        val usage = GpuBuffer.USAGE_VERTEX or GpuBuffer.USAGE_COPY_DST or GpuBuffer.USAGE_MAP_WRITE
        globalBuffer = RenderSystem.getDevice().createBuffer(
            { "AC UI StreamingBuffer" }, usage, newSize
        )
        writeOffset = 0L
    }

    override fun close() {
        if (globalBuffer != null) {
            globalBuffer!!.close()
            globalBuffer = null
        }
        for (region in activeRegions) region.fence.close()
        activeRegions.clear()
        for (retired in retiredBuffers) retired.free()
        retiredBuffers.clear()
        writeOffset = 0L
    }

    private data class FrameRegion(val start: Long, val end: Long, val fence: GpuFence) {
        fun intersects(otherStart: Long, otherEnd: Long): Boolean {
            return start < otherEnd && otherStart < end
        }
    }

    private data class RetiredBuffer(val buffer: GpuBuffer, val regions: Deque<FrameRegion>) {
        val isReady: Boolean
            get() {
                if (regions.isEmpty()) return true
                return regions.getLast().fence.awaitCompletion(0)
            }

        fun free() {
            for (region in regions) region.fence.close()
            regions.clear()
            buffer.close()
        }
    }

    private data class AllocationResult(
        val start: Long,
        val end: Long,
        val length: Long,
        val totalSize: Long,
        val needsRotate: Boolean
    )

    private data class BatchUploadResult(val drawCalls: MutableList<DrawCall>, val maxIndexCount: Int)
    companion object {
        private val logger = AcademyCraft.getLogger()

        private const val INITIAL_CAPACITY = 4L * 1024L * 1024L

        private fun align(offset: Long, alignment: Int): Long {
            return (offset + (alignment - 1)) / alignment * alignment
        }
    }
}