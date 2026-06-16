package org.academy.api.client.gui.render

import com.mojang.blaze3d.PrimitiveTopology
import com.mojang.blaze3d.buffers.GpuBuffer
import com.mojang.blaze3d.buffers.GpuBufferSlice
import com.mojang.blaze3d.buffers.GpuFence
import com.mojang.blaze3d.pipeline.RenderPipeline
import com.mojang.blaze3d.systems.RenderPass
import com.mojang.blaze3d.systems.RenderSystem
import com.mojang.blaze3d.textures.GpuTextureView
import net.minecraft.client.Minecraft
import org.academy.AcademyCraft
import org.academy.api.client.render.TextureBinding
import org.academy.api.client.render.UniformBinding
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
        projectionUbo: GpuBufferSlice,
        dynamicTransformsUbo: GpuBuffer,
        guiScale: Float
    ) {
        processRetiredBuffers()

        if (batches.isEmpty()) return

        val meshAbsoluteOffsets = mutableListOf<MeshOffsetInfo>()
        val allocation = prepareBuffer(batches, meshAbsoluteOffsets)

        if (globalBuffer == null) return
        val buffer = globalBuffer!!

        val drawCalls = uploadAndBuildDrawCalls(batches, meshAbsoluteOffsets, allocation, buffer)

        submitRenderPass(drawCalls, color, projectionUbo, dynamicTransformsUbo, guiScale)

        recordFence(allocation)
    }

    private data class MeshOffsetInfo(
        val batchIndex: Int,
        val meshIndex: Int,
        val absoluteOffset: Long,
        val length: Long
    )

    private fun prepareBuffer(batches: List<PendingBatch>, offsets: MutableList<MeshOffsetInfo>): AllocationResult {
        var allocation = tryAllocate(batches, offsets)

        if (globalBuffer == null) {
            ensureCapacity(allocation.totalSize)
            allocation = tryAllocate(batches, offsets)
        } else if (allocation.needsRotate || allocation.totalSize > globalBuffer!!.size()) {
            val requiredSize = allocation.totalSize
            retireCurrentBuffer()
            ensureCapacity(max(requiredSize, INITIAL_CAPACITY))
            allocation = tryAllocate(batches, offsets)
        }
        return allocation
    }

    private fun tryAllocate(batches: List<PendingBatch>, outOffsets: MutableList<MeshOffsetInfo>): AllocationResult {
        outOffsets.clear()
        var currentPtr = writeOffset

        for (batchIdx in batches.indices) {
            val batch = batches[batchIdx]
            for (meshIdx in batch.meshDataList.indices) {
                val mesh = batch.meshDataList[meshIdx]
                val stride = mesh.drawState().format().vertexSize
                currentPtr = align(currentPtr, stride)
                outOffsets.add(MeshOffsetInfo(batchIdx, meshIdx, currentPtr, mesh.vertexBuffer().remaining().toLong()))
                currentPtr += mesh.vertexBuffer().remaining().toLong()
            }
        }

        var endPtr = currentPtr
        var startPtr = writeOffset
        var requiredLength = endPtr - startPtr
        var needsRotate = false

        if (globalBuffer != null && endPtr > globalBuffer!!.size()) {
            currentPtr = 0
            outOffsets.clear()
            for (batchIdx in batches.indices) {
                val batch = batches[batchIdx]
                for (meshIdx in batch.meshDataList.indices) {
                    val mesh = batch.meshDataList[meshIdx]
                    val stride = mesh.drawState().format().vertexSize
                    currentPtr = align(currentPtr, stride)
                    outOffsets.add(
                        MeshOffsetInfo(
                            batchIdx,
                            meshIdx,
                            currentPtr,
                            mesh.vertexBuffer().remaining().toLong()
                        )
                    )
                    currentPtr += mesh.vertexBuffer().remaining().toLong()
                }
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

    private fun uploadAndBuildDrawCalls(
        batches: List<PendingBatch>,
        offsets: List<MeshOffsetInfo>,
        allocation: AllocationResult,
        buffer: GpuBuffer
    ): List<DrawCall> {
        buffer.map(allocation.start, allocation.length, false, true).use { mapped ->
            val mappedBuffer = mapped.data()
            for (info in offsets) {
                val mesh = batches[info.batchIndex].meshDataList[info.meshIndex]
                mappedBuffer.position(Math.toIntExact(info.absoluteOffset - allocation.start))
                mappedBuffer.put(mesh.vertexBuffer())
            }
        }

        val drawCalls = mutableListOf<DrawCall>()
        var offsetIdx = 0
        for (batchIdx in batches.indices) {
            val batch = batches[batchIdx]
            val slices = mutableListOf<GpuBufferSlice>()
            val slotIndices = batch.slotIndices
            var indexCount = 0
            for (meshIdx in batch.meshDataList.indices) {
                val info = offsets[offsetIdx]
                slices.add(buffer.slice(info.absoluteOffset, info.length))
                if (batch.slotIndices[meshIdx] == 0) {
                    indexCount = batch.indexCount
                }
                offsetIdx++
            }
            drawCalls.add(
                DrawCall(
                    batch.pipeline,
                    batch.scissorArea,
                    batch.textures,
                    batch.uniforms,
                    slices,
                    slotIndices,
                    indexCount,
                    batch.instanceCount
                )
            )
            for (mesh in batch.meshDataList) {
                mesh.close()
            }
        }
        return drawCalls
    }

    private fun submitRenderPass(
        drawCalls: List<DrawCall>,
        color: GpuTextureView,
        projectionUbo: GpuBufferSlice,
        dynamicTransformsUbo: GpuBuffer,
        guiScale: Float
    ) {
        val sequentialIndexBuffer = RenderSystem.getSequentialBuffer(PrimitiveTopology.QUADS)
        val indexBuffer = sequentialIndexBuffer.getBuffer(drawCalls.maxOf { it.indexCount })
        val indexType = sequentialIndexBuffer.type()
        val physicalHeight = Minecraft.getInstance().window.height

        val commandEncoder = RenderSystem.getDevice().createCommandEncoder()
        commandEncoder.createRenderPass(
            { "UIRender" }, color, Optional.empty()
        ).use { renderPass ->
            renderPass.setIndexBuffer(indexBuffer, indexType)
            for (drawCall in drawCalls) {
                if (configureDrawCall(
                        renderPass,
                        drawCall,
                        projectionUbo,
                        dynamicTransformsUbo,
                        guiScale,
                        physicalHeight
                    )
                ) {
                    renderPass.drawIndexed(
                        drawCall.indexCount,
                        drawCall.instanceCount,
                        0,
                        0,
                        0
                    )
                }
            }
        }
    }

    private fun configureDrawCall(
        renderPass: RenderPass,
        drawCall: DrawCall,
        projectionUbo: GpuBufferSlice,
        dynamicTransformsUbo: GpuBuffer,
        guiScale: Float,
        physicalHeight: Int
    ): Boolean {
        renderPass.setPipeline(drawCall.pipeline)
        for (i in drawCall.vertexSlices.indices) {
            val slot = drawCall.slotIndices[i]
            renderPass.setVertexBuffer(slot, drawCall.vertexSlices[i])
        }

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
            val slice = uniform.slice
            if (slice.buffer().isClosed) {
                logger.error("Uniform {} has been closed, skipping draw call.", uniform.name)
                return false
            }
            renderPass.setUniform(uniform.name, slice)
        }
        return true
    }

    private fun recordFence(allocation: AllocationResult) {
        val fence = RenderSystem.getDevice().createCommandEncoder().createFence()
        activeRegions.addLast(FrameRegion(allocation.start, allocation.end, fence))
        writeOffset = allocation.end
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
            newSize = max(retiredBuffers.last.buffer.size(), newSize)
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
                return regions.last.fence.awaitCompletion(0)
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

    private data class DrawCall(
        val pipeline: RenderPipeline,
        val scissorArea: ScissorRect?,
        val textures: List<TextureBinding>,
        val uniforms: List<UniformBinding>,
        val vertexSlices: List<GpuBufferSlice>,
        val slotIndices: List<Int>,
        val indexCount: Int,
        val instanceCount: Int
    )

    companion object {
        private val logger = AcademyCraft.getLogger()
        private const val INITIAL_CAPACITY = 4L * 1024L * 1024L

        private fun align(offset: Long, alignment: Int): Long {
            return (offset + (alignment - 1)) / alignment * alignment
        }
    }
}