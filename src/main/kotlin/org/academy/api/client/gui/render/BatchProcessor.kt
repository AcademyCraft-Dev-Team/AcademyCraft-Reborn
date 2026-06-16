package org.academy.api.client.gui.render

import com.mojang.blaze3d.buffers.GpuBufferSlice
import com.mojang.blaze3d.pipeline.RenderPipeline
import com.mojang.blaze3d.vertex.BufferBuilder
import com.mojang.blaze3d.vertex.MeshData
import net.minecraft.client.renderer.DynamicUniformStorage.DynamicUniform
import org.academy.api.client.Render.Buffers.getByteBufferBuilder
import org.academy.api.client.gui.command.SubmittedCommand
import org.academy.api.client.render.TextureBinding
import org.academy.api.client.render.UniformBinding
import org.academy.api.client.render.UniformPayload

object BatchProcessor {
    fun process(
        commands: MutableList<SubmittedCommand>,
        uploader: UboUploader
    ): List<PendingBatch> {
        if (commands.isEmpty()) return mutableListOf()

        commands.sortWith { c1, c2 ->
            compareCommands(c1, c2)
        }

        val batches = ArrayList<PendingBatch>()
        val iterator = commands.iterator()

        if (!iterator.hasNext()) return batches

        val firstCommand = iterator.next()
        val state = BatchState(firstCommand)

        applyVertices(state, firstCommand)

        while (iterator.hasNext()) {
            val command = iterator.next()

            if (state.shouldBreakBatch(command)) {
                finishBatch(batches, state, uploader)
                state.reset(command)
            }

            applyVertices(state, command)
        }

        finishBatch(batches, state, uploader)

        return batches
    }

    private fun compareCommands(c1: SubmittedCommand, c2: SubmittedCommand): Int {
        val orderCompare = c1.drawOrder.toLong().compareTo(c2.drawOrder.toLong())
        if (orderCompare != 0) return orderCompare

        val pipelineCompare =
            c1.command.pipeline.sortKey.compareTo(c2.command.pipeline.sortKey)
        if (pipelineCompare != 0) return pipelineCompare

        val resCompare = c1.resourceKey.compareTo(c2.resourceKey)
        if (resCompare != 0) return resCompare

        val s1 = c1.scissorRect
        val s2 = c2.scissorRect
        if (s1 == null && s2 == null) return 0
        if (s1 == null) return -1
        if (s2 == null) return 1
        return s1.compareTo(s2)
    }

    private fun applyVertices(state: BatchState, submittedCommand: SubmittedCommand) {
        val command = submittedCommand.command
        val pose = submittedCommand.pose

        if (state.builders.size == 1) {
            command.generateVertices(state.builders[0], pose)
        } else {
            val instancing = command.isGeometryFixed()
            if (!instancing || state.instanceCount == 0) {
                command.generateVertices(state.builders[0], pose)
            }
            for (i in 1 until state.builders.size) {
                val slot = state.slotIndices[i]
                command.generateInstanceData(slot, state.builders[i], if (instancing) state.instanceCount else 0, pose)
            }
            if (instancing) state.instanceCount++ else state.instanceCount = 1
        }
    }

    private fun finishBatch(
        batches: MutableList<PendingBatch>,
        state: BatchState,
        uploader: UboUploader
    ) {
        val meshDataList = ArrayList<MeshData>()
        for (builder in state.builders) {
            val mesh = builder.build()
            if (mesh == null && state.builders.size > 1) {
                meshDataList.forEach { it.close() }
                return
            }
            if (mesh != null) {
                meshDataList.add(mesh)
            }
        }

        if (meshDataList.isEmpty()) return

        val bindings = ArrayList<UniformBinding>(state.uniforms.size)
        for (payload in state.uniforms) {
            val slice = uploader.upload(payload)
            bindings.add(UniformBinding(payload.name, slice))
        }

        val indexCount = meshDataList[0].drawState().indexCount()
        val vertexStride = meshDataList[0].drawState().format().vertexSize
        val instanceCount = if (state.builders.size > 1) state.instanceCount else 1

        batches.add(
            PendingBatch(
                meshDataList, state.slotIndices, state.pipeline, state.scissor,
                state.textures, bindings, indexCount, vertexStride, instanceCount
            )
        )
    }

    interface UboUploader {
        fun <T : DynamicUniform> upload(payload: UniformPayload<T>): GpuBufferSlice
    }

    private class BatchState(initialCommand: SubmittedCommand) {
        var pipeline: RenderPipeline
        var scissor: ScissorRect? = null
        var textures: List<TextureBinding>
        var uniforms: List<UniformPayload<*>>
        var builders: List<BufferBuilder>
        var slotIndices: List<Int>
        var instanceCount: Int

        private var resourceKey: Long = 0

        init {
            val innerCommand = initialCommand.command
            pipeline = innerCommand.pipeline
            scissor = initialCommand.scissorRect
            resourceKey = initialCommand.resourceKey
            textures = innerCommand.textures
            uniforms = innerCommand.uniforms

            val activeSlots = pipeline.vertexFormatBindings
                .mapIndexedNotNull { index, format -> if (format != null) index else null }
            slotIndices = activeSlots

            builders = activeSlots.map { slot ->
                val format = pipeline.getVertexFormatBinding(slot)!!
                BufferBuilder(getByteBufferBuilder(), pipeline.primitiveTopology, format)
            }

            instanceCount = if (activeSlots.size > 1 && innerCommand.isGeometryFixed()) 0 else 1
        }

        fun reset(command: SubmittedCommand) {
            val innerCommand = command.command
            pipeline = innerCommand.pipeline
            scissor = command.scissorRect
            resourceKey = command.resourceKey
            textures = innerCommand.textures
            uniforms = innerCommand.uniforms

            val activeSlots = pipeline.vertexFormatBindings
                .mapIndexedNotNull { index, format -> if (format != null) index else null }
            slotIndices = activeSlots

            builders = activeSlots.map { slot ->
                val format = pipeline.getVertexFormatBinding(slot)!!
                BufferBuilder(getByteBufferBuilder(), pipeline.primitiveTopology, format)
            }

            instanceCount = if (activeSlots.size > 1 && innerCommand.isGeometryFixed()) 0 else 1
        }

        fun shouldBreakBatch(nextCommand: SubmittedCommand): Boolean {
            val nextInner = nextCommand.command
            if (nextInner.pipeline !== pipeline || nextCommand.resourceKey != resourceKey || nextCommand.scissorRect != scissor) return true
            if (builders.size > 1) {
                return !nextInner.isGeometryFixed()
            }
            return false
        }
    }
}