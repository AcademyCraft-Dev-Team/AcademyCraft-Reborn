package org.academy.api.client.gui.render

import com.mojang.blaze3d.buffers.GpuBufferSlice
import com.mojang.blaze3d.pipeline.RenderPipeline
import com.mojang.blaze3d.vertex.BufferBuilder
import net.minecraft.client.renderer.DynamicUniformStorage.DynamicUniform
import org.academy.api.client.Render.Buffers.getByteBufferBuilder
import org.academy.api.client.gui.command.SubmittedCommand
import org.academy.api.client.render.TextureBinding
import org.academy.api.client.render.UniformBinding
import org.academy.api.client.render.UniformPayload
import org.joml.Matrix4f

object BatchProcessor {
    fun process(
        commands: MutableList<SubmittedCommand>,
        depthEpsilon: Float,
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

        applyVertices(state.builder, firstCommand, depthEpsilon)

        while (iterator.hasNext()) {
            val command = iterator.next()

            if (state.shouldBreakBatch(command)) {
                finishBatch(batches, state, uploader)
                state.reset(command)
            }

            applyVertices(state.builder, command, depthEpsilon)
        }

        finishBatch(batches, state, uploader)

        return batches
    }

    private fun compareCommands(c1: SubmittedCommand, c2: SubmittedCommand): Int {
        val pose1 = c1.pose.pose().m32()
        val pose2 = c2.pose.pose().m32()
        val zCompare = pose1.toDouble().compareTo(pose2.toDouble())
        if (zCompare != 0) return zCompare

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

    private fun applyVertices(builder: BufferBuilder, submittedCommand: SubmittedCommand, depthEpsilon: Float) {
        val command = submittedCommand.command
        val pose = submittedCommand.pose.pose()
        val sequenceId = submittedCommand.drawOrder

        if (sequenceId > 0 && depthEpsilon > 0) {
            val tempPose = Matrix4f(pose)
            tempPose.translate(0f, 0f, sequenceId * depthEpsilon)
            command.generateVertices(builder, tempPose)
        } else command.generateVertices(builder, pose)
    }

    private fun finishBatch(
        batches: MutableList<PendingBatch>,
        state: BatchState,
        uploader: UboUploader
    ) {
        val meshData = state.builder.build()
        if (meshData != null) {
            val bindings = ArrayList<UniformBinding>(state.uniforms.size)
            for (payload in state.uniforms) {
                val slice = uploader.upload(payload)
                bindings.add(UniformBinding(payload.name, slice))
            }
            batches.add(PendingBatch(meshData, state.pipeline, state.scissor, state.textures, bindings))
        }
    }

    interface UboUploader {
        fun <T : DynamicUniform> upload(payload: UniformPayload<T>): GpuBufferSlice
    }

    private class BatchState(initialCommand: SubmittedCommand) {
        var pipeline: RenderPipeline
        var scissor: ScissorRect? = null
        var textures: List<TextureBinding>
        var uniforms: List<UniformPayload<*>>
        var builder: BufferBuilder

        private var resourceKey: Long = 0

        init {
            val innerCommand = initialCommand.command
            pipeline = innerCommand.pipeline
            scissor = initialCommand.scissorRect
            resourceKey = initialCommand.resourceKey
            textures = innerCommand.textures
            uniforms = innerCommand.uniforms
            builder = BufferBuilder(
                getByteBufferBuilder(),
                pipeline.vertexFormatMode,
                pipeline.vertexFormat
            )
        }

        fun reset(command: SubmittedCommand) {
            val innerCommand = command.command
            pipeline = innerCommand.pipeline
            scissor = command.scissorRect
            resourceKey = command.resourceKey
            textures = innerCommand.textures
            uniforms = innerCommand.uniforms
            builder = BufferBuilder(
                getByteBufferBuilder(),
                pipeline.vertexFormatMode,
                pipeline.vertexFormat
            )
        }

        fun shouldBreakBatch(nextCommand: SubmittedCommand): Boolean {
            val nextInner = nextCommand.command
            return nextInner.pipeline !== pipeline || nextCommand.resourceKey != resourceKey || (nextCommand.scissorRect != scissor)
        }
    }
}