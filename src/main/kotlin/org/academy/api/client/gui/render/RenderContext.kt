package org.academy.api.client.gui.render

import com.mojang.blaze3d.vertex.PoseStack
import org.academy.api.client.gui.command.DrawCommand
import org.academy.api.client.gui.command.SubmittedCommand
import org.joml.Matrix4f
import org.joml.Quaternionfc
import java.util.*
import java.util.function.Supplier

class RenderContext {
    val commands = mutableListOf<SubmittedCommand>()
    private val pose = PoseStack()
    private val pose2D = PoseStack2D()
    private val scissorStack: ScissorStack
    private val drawOrderStack: DrawOrderStack
    private val alphaStack: AlphaStack
    var commandCounter = 0
        private set

    val blurRegions = mutableListOf<BlurRegion>()

    var recordedMax = 0L
        private set

    init {
        scissorStack = ScissorStack()
        drawOrderStack = DrawOrderStack()
        alphaStack = AlphaStack()
    }

    fun resetRecordedMax() {
        recordedMax = 0L
    }

    fun submit(command: DrawCommand) {
        val currentPose = pose.last().copy()
        val currentScissor = scissorStack.peek()
        val currentDrawOrder = drawOrderStack.peek()
        if (currentDrawOrder > recordedMax) recordedMax = currentDrawOrder
        commands.add(SubmittedCommand(command, currentPose, currentScissor, currentDrawOrder, commandCounter))
        commandCounter++
    }

    fun addCached(cache: SubtreeCache, currentOrigin: Matrix4f): Boolean {
        val cachedCommands = cache.commands
        if (cachedCommands.isEmpty() && cache.regions.isEmpty()) return true

        val anchor =
            if (cachedCommands.isNotEmpty()) cachedCommands.first().commandIndex else cache.regions.first().commandIndex
        val offset = commandCounter - anchor

        val alphaMul = alphaMulCorrection(cache)
        val poseChanged = !SubtreeCache.sameMatrix(cache.recordOrigin, currentOrigin)
        val baseDrawOrder = drawOrderStack.peek()

        if (poseChanged) {
            if (cache.regions.isNotEmpty()) return false
            addRecomposed(cachedCommands, offset, currentOrigin, cache.recordOrigin, alphaMul, baseDrawOrder)
            return true
        }

        if (cachedCommands.isNotEmpty()) {
            for (c in cachedCommands) {
                val absoluteOrder = baseDrawOrder + c.drawOrder
                if (absoluteOrder > recordedMax) recordedMax = absoluteOrder
                commands.add(
                    SubmittedCommand(
                        c.command,
                        c.pose,
                        replayScissor(c),
                        absoluteOrder,
                        c.commandIndex + offset,
                        alphaMul
                    )
                )
            }
            commandCounter = commands.size
        }
        for ((x, y, width, height, radius, commandIndex) in cache.regions) {
            blurRegions.add(BlurRegion(x, y, width, height, radius, commandIndex + offset))
        }
        return true
    }

    private fun addRecomposed(
        cachedCommands: List<SubmittedCommand>,
        offset: Int,
        currentOrigin: Matrix4f,
        recordOrigin: Matrix4f,
        alphaMul: Float,
        baseDrawOrder: Long
    ) {
        if (cachedCommands.isEmpty()) return
        val invRecordOrigin = Matrix4f(recordOrigin).invert() ?: return
        for (c in cachedCommands) {
            val replayPose = SubtreeCache.recomposePose(c.pose, currentOrigin, invRecordOrigin)
            val absoluteOrder = baseDrawOrder + c.drawOrder
            if (absoluteOrder > recordedMax) recordedMax = absoluteOrder
            commands.add(
                SubmittedCommand(
                    c.command,
                    replayPose,
                    replayScissor(c),
                    absoluteOrder,
                    c.commandIndex + offset,
                    alphaMul
                )
            )
        }
        commandCounter = commands.size
    }

    private fun replayScissor(c: SubmittedCommand): ScissorRect? = c.scissorRect ?: scissorStack.peek()

    private fun alphaMulCorrection(cache: SubtreeCache): Float {
        val record = cache.recordAlphaMul
        val current = alphaStack.peek()
        if (record == current) return 1.0f
        if (record == 0f) return 1.0f
        return current / record
    }

    fun pose(): PoseStack2D {
        return pose2D
    }

    fun drawOrder(): DrawOrderStack {
        return drawOrderStack
    }

    fun alpha(): AlphaStack {
        return alphaStack
    }

    fun registerBlurRegion(region: BlurRegion) {
        blurRegions.add(region)
    }

    fun blurRegionCount(): Int = blurRegions.size

    fun blurRegionsSince(fromIndex: Int): List<BlurRegion> =
        if (fromIndex < blurRegions.size) blurRegions.subList(fromIndex, blurRegions.size).toList()
        else emptyList()

    fun enableScissor(scissorRect: ScissorRect) {
        scissorStack.push(scissorRect)
    }

    fun disableScissor() {
        scissorStack.pop()
    }

    fun currentScissor(): ScissorRect? = scissorStack.peek()

    val accumulatedAlpha: Float
        get() = alphaStack.peek()

    fun clear() {
        commands.clear()
        drawOrderStack.clear()
        alphaStack.clear()
        blurRegions.clear()
        commandCounter = 0
    }

    inner class PoseStack2D {
        fun translate(x: Float, y: Float) {
            last().translate(x, y, 0f)
        }

        fun scale(x: Float, y: Float) {
            last().scale(x, y, 1f)
        }

        fun mulPose(by: Quaternionfc) {
            last().rotate(by)
        }

        fun pushPose() {
            pose.pushPose()
        }

        fun popPose() {
            pose.popPose()
        }

        fun last(): PoseStack.Pose {
            return pose.last()
        }
    }

    class AlphaStack {
        private val stack = ArrayDeque<Float>()

        init {
            stack.push(1.0f)
        }

        fun push(alpha: Float) {
            stack.push(peek() * alpha)
        }

        fun pop() {
            if (stack.size > 1) stack.pop()
        }

        fun peek(): Float {
            val value = stack.peek()
            return value ?: 1.0f
        }

        fun clear() {
            stack.clear()
            stack.push(1.0f)
        }
    }

    class DrawOrderStack {
        private val stack = ArrayDeque<Long>()

        init {
            stack.push(0L)
        }

        fun push(x: Long = peek()) {
            stack.push(x)
        }

        fun pop() {
            if (stack.size > 1) stack.pop()
        }

        fun advance(x: Long = 1L) {
            val next = stack.pop() + x
            // 防止 coverAllPrev 的 advance(recordedMax+1) 指数增长导致 Long 溢出回绕成负数.
            // 真实 GUI 的 drawOrder 远小于该上限, 不会触顶, 因此层级顺序不受影响.
            stack.push(if (next > MAX_ORDER) MAX_ORDER else next)
        }

        fun peek(): Long {
            val value = stack.peek()
            return value ?: 0L
        }

        fun clear() {
            stack.clear()
            stack.push(0L)
        }

        companion object {
            const val MAX_ORDER: Long = 1L shl 40
        }
    }

    class ScissorStack {
        private val stack = ArrayDeque<ScissorRect>()

        fun push(scissor: ScissorRect) {
            val currentScissor = stack.peekLast()
            if (currentScissor != null) {
                val intersection = scissor.intersection(currentScissor)
                stack.addLast(
                    Objects.requireNonNullElseGet<ScissorRect>(
                        intersection,
                        Supplier { ScissorRect.empty() })
                )
            } else stack.addLast(scissor)
        }

        fun pop() {
            check(!stack.isEmpty()) { "Scissor stack underflow" }
            stack.removeLast()
        }

        fun peek(): ScissorRect? {
            return stack.peekLast()
        }
    }
}
