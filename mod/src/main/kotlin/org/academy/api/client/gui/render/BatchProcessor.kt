package org.academy.api.client.gui.render

import com.mojang.blaze3d.vertex.MeshData
import com.mojang.renderpearl.api.buffers.GpuBufferSlice
import com.mojang.renderpearl.api.pipeline.RenderPipeline
import net.minecraft.client.renderer.DynamicGpuDataStorage.DynamicGpuData
import org.academy.api.client.gui.command.ExpandableDrawCommand
import org.academy.api.client.gui.command.SubmittedCommand
import org.academy.api.client.render.Render.Buffers.getByteBufferBuilder
import org.academy.api.client.render.TextureBinding
import org.academy.api.client.render.UniformBinding
import org.academy.api.client.render.UniformPayload

object BatchProcessor {
    private const val CLIP_LEFT = 0x1
    private const val CLIP_TOP = 0x2
    private const val CLIP_RIGHT = 0x4
    private const val CLIP_BOTTOM = 0x8
    private const val CLIP_FULL = 0xF

    fun process(
        commands: MutableList<SubmittedCommand>,
        uploader: UboUploader
    ): List<PendingBatch> {
        if (commands.isEmpty()) return mutableListOf()

        commands.removeAll { it.scissorRect?.isEmpty == true }
        if (commands.isEmpty()) return mutableListOf()

        expandCommands(commands)
        if (commands.isEmpty()) return mutableListOf()

        val batches = ArrayList<DeferredBatch>()
        val lastByKey = HashMap<MergeKey, DeferredBatch>()

        for (submitted in commands) {
            val op = resolve(submitted) ?: continue
            val key = MergeKey(op.pipeline, op.resourceKey)
            var target = lastByKey[key]
            if (target != null && !target.canMergeWith(op)) {
                target = null
            }
            val plan = locateInsertIndex(batches, op.clippedBounds, target, op.pipeline)
            val resolvedTarget = plan.target
            if (resolvedTarget != null) {
                resolvedTarget.merge(op)
            } else {
                val batch = DeferredBatch(op, key)
                batches.add(plan.index.coerceIn(0, batches.size), batch)
                lastByKey[key] = batch
            }
        }

        val result = ArrayList<PendingBatch>(batches.size)
        for (batch in batches) {
            batch.build(uploader)?.let { result.add(it) }
        }
        return result
    }

    private data class MergeKey(val pipeline: RenderPipeline, val resourceKey: Long)

    private class ResolvedOp(
        val submitted: SubmittedCommand,
        val pipeline: RenderPipeline,
        val resourceKey: Long,
        val slotCount: Int,
        val instanced: Boolean,
        val clippedBounds: ScissorRect?,
        val clipFlags: Int,
        val allowsOverlap: Boolean
    )

    private fun resolve(submitted: SubmittedCommand): ResolvedOp? {
        val command = submitted.command
        val pipeline = command.pipeline
        val scissor = submitted.scissorRect
        val local = command.localBounds()

        var flags: Int
        var clipped: ScissorRect?
        if (local == null) {
            flags = CLIP_FULL
            clipped = scissor
        } else {
            val raw = ScissorRect.fromLocal(
                local.left, local.top, local.right, local.bottom, submitted.pose.pose()
            )
            if (scissor == null) {
                flags = 0
                clipped = raw
            } else {
                if (raw.isEmpty || !raw.intersects(scissor)) return null
                flags = computeClipSideFlags(scissor, raw)
                clipped = scissor.intersection(raw) ?: return null
            }
        }

        val slotCount = pipeline.vertexFormatBindings.count { it != null }
        return ResolvedOp(
            submitted,
            pipeline,
            submitted.resourceKey,
            slotCount,
            command.isGeometryFixed(),
            clipped,
            flags,
            command.allowsOverlapMerge()
        )
    }

    private fun computeClipSideFlags(clip: ScissorRect, bounds: ScissorRect): Int {
        var flags = 0
        if (clip.left > bounds.left) flags = flags or CLIP_LEFT
        if (clip.top > bounds.top) flags = flags or CLIP_TOP
        if (clip.right < bounds.right) flags = flags or CLIP_RIGHT
        if (clip.bottom < bounds.bottom) flags = flags or CLIP_BOTTOM
        return flags
    }

    private fun checkSide(currentFlags: Int, newFlags: Int, side: Int, boundsDelta: Float): Boolean {
        val currentClip = currentFlags and side != 0
        val newClip = newFlags and side != 0
        if (boundsDelta > 0f && currentClip) return false
        if (boundsDelta < 0f && newClip) return false
        return true
    }

    private fun clipSidesCompatible(
        currentFlags: Int,
        newFlags: Int,
        currentBounds: ScissorRect?,
        opBounds: ScissorRect
    ): Boolean {
        if (currentFlags == 0 && newFlags == 0) return true
        if (currentBounds == null) return false
        if (!checkSide(currentFlags, newFlags, CLIP_LEFT, currentBounds.left - opBounds.left)) return false
        if (!checkSide(currentFlags, newFlags, CLIP_TOP, currentBounds.top - opBounds.top)) return false
        if (!checkSide(currentFlags, newFlags, CLIP_RIGHT, opBounds.right - currentBounds.right)) return false
        if (!checkSide(currentFlags, newFlags, CLIP_BOTTOM, opBounds.bottom - currentBounds.bottom)) return false
        return true
    }

    private class InsertPlan(var target: DeferredBatch?, var index: Int)

    private fun locateInsertIndex(
        batches: List<DeferredBatch>,
        bounds: ScissorRect?,
        target: DeferredBatch?,
        pipeline: RenderPipeline
    ): InsertPlan {
        val plan = InsertPlan(target, batches.size)
        for (i in batches.indices.reversed()) {
            val over = batches[i]
            if (over === plan.target) break
            if (over.pipeline === pipeline) {
                plan.index = i + 1
                if (plan.target == null) break
            }
            if (over.intersects(bounds)) {
                plan.target = null
                break
            }
        }
        return plan
    }

    private class DeferredBatch(first: ResolvedOp, val key: MergeKey) {
        val pipeline: RenderPipeline = first.pipeline
        private val slotCount: Int = first.slotCount
        private val ops = ArrayList<ResolvedOp>()
        private val opBounds = ArrayList<ScissorRect?>()
        private var bounds: ScissorRect? = null
        private var clipFlags: Int = 0

        init {
            merge(first)
        }

        fun merge(op: ResolvedOp) {
            ops.add(op)
            opBounds.add(op.clippedBounds)
            clipFlags = clipFlags or op.clipFlags
            val opBound = op.clippedBounds
            if (opBound == null) {
                bounds = null
            } else {
                val current = bounds
                bounds = current?.union(opBound) ?: opBound
            }
        }

        fun canMergeWith(op: ResolvedOp): Boolean {
            if (op.clippedBounds == null) return false
            if (slotCount > 1 && !op.instanced) return false
            if (!op.allowsOverlap && intersects(op.clippedBounds)) return false
            return clipSidesCompatible(clipFlags, op.clipFlags, bounds, op.clippedBounds)
        }

        fun intersects(rect: ScissorRect?): Boolean {
            if (rect == null) return true
            val batchBounds = bounds ?: return true
            if (!batchBounds.intersects(rect)) return false
            for (opBound in opBounds) {
                if (opBound == null) return true
                if (opBound.intersects(rect)) return true
            }
            return false
        }

        fun build(uploader: UboUploader): PendingBatch? {
            val state = BatchState(ops.first().submitted)
            for (op in ops) {
                applyVertices(state, op.submitted)
            }
            val scissor = if (clipFlags != 0) bounds else null
            return finishBatch(state, scissor, uploader)
        }
    }

    private fun expandCommands(commands: MutableList<SubmittedCommand>) {
        var any = false
        for (c in commands) {
            if (c.command is ExpandableDrawCommand) {
                any = true
                break
            }
        }
        if (!any) return

        val expanded = ArrayList<SubmittedCommand>(commands.size)
        for (c in commands) {
            val command = c.command
            if (command is ExpandableDrawCommand) {
                val subs = command.expand(c.pose, c.alphaMul)
                for (sub in subs) {
                    expanded.add(
                        SubmittedCommand(sub, c.pose, c.scissorRect, c.commandIndex, c.alphaMul)
                    )
                }
            } else {
                expanded.add(c)
            }
        }
        commands.clear()
        commands.addAll(expanded)
    }

    private fun applyVertices(state: BatchState, submittedCommand: SubmittedCommand) {
        val command = submittedCommand.command
        val pose = submittedCommand.pose
        val alphaMul = submittedCommand.alphaMul

        if (state.builders.size == 1) {
            command.generateVertices(state.builders[0], pose, alphaMul)
            return
        }

        val instancing = command.isGeometryFixed()
        if (!instancing || state.instanceCount == 0) {
            command.generateVertices(state.builders[0], pose, alphaMul)
            if (instancing) {
                state.geometryMeshData = state.builders[0].build()
            }
        }

        if (instancing) {
            val count = command.instanceCount(pose, alphaMul).coerceAtLeast(0)
            for (i in 1 until state.builders.size) {
                command.appendInstances(state.slotIndices[i], state.builders[i], pose, alphaMul)
            }
            state.instanceCount += count
        } else {
            for (i in 1 until state.builders.size) {
                command.appendInstances(state.slotIndices[i], state.builders[i], pose, alphaMul)
            }
            state.instanceCount = 1
        }
    }

    private fun finishBatch(
        state: BatchState,
        scissor: ScissorRect?,
        uploader: UboUploader
    ): PendingBatch? {
        val meshDataList = ArrayList<MeshData>()

        if (state.geometryMeshData != null) {
            meshDataList.add(state.geometryMeshData!!)
            for (i in 1 until state.builders.size) {
                val mesh = state.builders[i].build()
                if (mesh == null) {
                    meshDataList.forEach { it.close() }
                    return null
                }
                meshDataList.add(mesh)
            }
        } else {
            for (builder in state.builders) {
                val mesh = builder.build()
                if (mesh == null) {
                    meshDataList.forEach { it.close() }
                    return null
                }
                meshDataList.add(mesh)
            }
        }

        if (meshDataList.isEmpty()) return null

        val bindings = ArrayList<UniformBinding>(state.uniforms.size)
        for (payload in state.uniforms) {
            val slice = uploader.upload(payload)
            bindings.add(UniformBinding(payload.name, slice))
        }

        val indexCount = meshDataList[0].drawState().indexCount()
        val vertexStride = meshDataList[0].drawState().format().vertexSize
        val instanceCount = if (state.builders.size > 1) state.instanceCount else 1

        return PendingBatch(
            meshDataList, state.slotIndices, state.pipeline, scissor,
            state.textures, bindings, indexCount, vertexStride, instanceCount
        )
    }

    interface UboUploader {
        fun <T : DynamicGpuData> upload(payload: UniformPayload<T>): GpuBufferSlice
    }

    private class BatchState(initialCommand: SubmittedCommand) {
        var pipeline: RenderPipeline
        var textures: List<TextureBinding>
        var uniforms: List<UniformPayload<*>>
        var builders: List<MeshBuilder>
        var slotIndices: List<Int>
        var instanceCount: Int
        var geometryMeshData: MeshData? = null

        init {
            val innerCommand = initialCommand.command
            pipeline = innerCommand.pipeline
            textures = innerCommand.textures
            uniforms = innerCommand.uniforms

            val activeSlots = pipeline.vertexFormatBindings
                .mapIndexedNotNull { index, format -> if (format != null) index else null }
            slotIndices = activeSlots

            val sharedBuffer = getByteBufferBuilder()
            builders = activeSlots.map { slot ->
                val format = pipeline.getVertexFormatBinding(slot)!!
                MeshBuilder(sharedBuffer, pipeline.primitiveTopology, format)
            }

            instanceCount = if (activeSlots.size > 1 && innerCommand.isGeometryFixed()) 0 else 1
        }
    }
}
